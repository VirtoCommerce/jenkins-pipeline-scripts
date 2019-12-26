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

            try {
                stage('Checkout'){
                    deleteDir()
                    checkout scm
                }

                stage('Build'){
                    bat "dotnet build-server shutdown"
                    withSonarQubeEnv('VC Sonar Server'){
                        bat "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" "// %SONAR_HOST_URL% %SONAR_AUTH_TOKEN%
                        bat "vc-build SonarQubeEnd -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken ${env.SONAR_AUTH_TOKEN}"
                    }
                }

                stage('Quality Gate'){
                    Packaging.checkAnalyzerGate(this)
                }

                stage('Unit Tests'){
                    bat "vc-build Test -skip Restore+Compile"
                }   

                stage('Packaging'){                
                    bat "vc-build Compress -skip Clean+Restore+Compile+Test"

					def websitePath = Utilities.getWebPublishFolder(this, "docker")
                    def dockerImageName = "platform-core"
                    powershell script: "Copy-Item ${workspace}\\artifacts\\publish\\* ${websitePath}\\VirtoCommerce.Platform -Recurse"
                    powershell script: "Copy-Item ${env.WORKSPACE}@libs\\virto-shared-library\\resources\\docker.core\\windowsnano\\PlatformCore\\* ${websitePath}"
                    dir(websitePath){
                        docker.build("${dockerImageName}:${dockerTag}")
                    }
                }

                if(!Utilities.isPullRequest(this)){
                    stage('Publish'){
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
                                throw new Exception("ERROR: script returned exit code -1")
                            }
                        }


                    //     def orgName = Utilities.getOrgName(this)
                    //     powershell "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} -PreRelease -skip Clean+Restore+Compile+Test"
                    }
                    stage('Deploy'){
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