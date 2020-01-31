import jobs.scripts.*

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

    def UNSTABLE_CAUSES = []

    node {
        properties([disableConcurrentBuilds()])
        def escapedBranch = env.BRANCH_NAME.replaceAll('/', '_')
        def repoName = Utilities.getRepoName(this)
        def workspace = "D:\\Buildsv3\\${repoName}\\${escapedBranch}"
        projectType = 'NETCORE2'
        dockerTag = 'dev'
        dir(workspace){
            def SETTINGS
            def settingsFileContent
            configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
                settingsFileContent = readFile(SETTINGS_FILE)
            }
            SETTINGS = new Settings(settingsFileContent)
            SETTINGS.setRegion('platform-core')
            SETTINGS.setEnvironment(env.BRANCH_NAME)
            Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')
            def coverageFolder = Utilities.getCoverageFolder(this)

            try {
                stage('Checkout'){
                    deleteDir()
                    checkout scm
                }

                stage('Build'){
                    withSonarQubeEnv('VC Sonar Server'){
                        bat "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -skip Restore+Compile"// %SONAR_HOST_URL% %SONAR_AUTH_TOKEN%
                    }
                    //Packaging.startAnalyzer(this, true)
                    bat "vc-build Compile"
                }
                
                stage('Unit Tests'){
                    dir("${workspace}\\artifacts"){}
                    bat "vc-build Test -skip Restore+Compile"
                } 

                stage('Quality Gate'){
                    //Packaging.endAnalyzer(this)
                    withSonarQubeEnv('VC Sonar Server'){
                        bat "vc-build SonarQubeEnd -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken ${env.SONAR_AUTH_TOKEN} -skip Restore+Compile+SonarQubeStart"
                    }
                    Packaging.checkAnalyzerGate(this)
                }  

                // stage('Swagger')
                // {
                //     dir("${workspace}\\src\\VirtoCommerce.Platform.Web"){
                //         bat "vc-build SwaggerValidation -Skip Restore+Compile+Publish"
                //     }
                // }

                stage('Packaging'){                
                    bat "vc-build Compress -skip Clean+Restore+Compile+Test"

                    if(!Utilities.isPullRequest(this)){
                        def websitePath = Utilities.getWebPublishFolder(this, "docker")
                        def dockerImageName = "platform-core"
                        powershell script: "Copy-Item ${workspace}\\artifacts\\publish\\* ${websitePath}\\VirtoCommerce.Platform -Recurse -Force"
                        powershell script: "Copy-Item ${env.WORKSPACE}@libs\\virto-shared-library\\resources\\docker.core\\windowsnano\\PlatformCore\\* ${websitePath} -Force"
                        dir(websitePath){
                            bat "dotnet dev-certs https -ep \"${websitePath}\\devcert.pfx\" -p virto"
                            docker.build("${dockerImageName}:${dockerTag}")
                        }
                    }
                }

                if(!Utilities.isPullRequest(this))
                {
                    stage('Publish')
                    {
                        // powershell "vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test"
                        def ghReleaseResult = Utilities.runBatchScript(this, "@vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test")
                        if(ghReleaseResult['status'] != 0){
                            def nugetAlreadyExists = false
                            for(line in ghReleaseResult['stdout']){
                                if(line.contains("error: Response status code does not indicate success: 409")){
                                    nugetAlreadyExists = true
                                }
                            }
                            if(nugetAlreadyExists){
                                UNSTABLE_CAUSES.add("Nuget package already exists.")
                            }
                            else{
                                echo "${ghReleaseResult['stdout']}"
                                throw new Exception("ERROR: script returned exit code -1")
                            }
                        }


                    //     def orgName = Utilities.getOrgName(this)
                    //     powershell "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} -PreRelease -skip Clean+Restore+Compile+Test"
                    }
                    stage('Deploy')
                    {
                        // $ZipFile,
                        // $WebAppName,
                        // $ResourceGroupName,
                        // $SubscriptionID,
                        // $DestContentPath = ""
                        def artifacts = findFiles(glob: "artifacts/*.zip")
                        def artifactPath = artifacts[0].path
                        Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"platform\"")
                    }
                }
            }
            catch(any){
                currentBuild.result = 'FAILURE'
                throw any
            }
            finally {
                if(currentBuild.resultIsBetterOrEqualTo('SUCCESS') && UNSTABLE_CAUSES.size()>0){
                    currentBuild.result = 'UNSTABLE'
                    for(cause in UNSTABLE_CAUSES){
                        echo cause
                    }
                }
			    Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
                Utilities.cleanPRFolder(this)
            }
        }
    }
}