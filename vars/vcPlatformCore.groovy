def Modules
def Packaging
def Utilities

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

    def UNSTABLE_CAUSES = []

    node {
        properties([disableConcurrentBuilds()])

        def globalLib = library('global-shared-lib').com.test
		Utilities = globalLib.Utilities
		Packaging = globalLib.Packaging
		Modules = globalLib.Modules

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
            SETTINGS = globalLib.Settings.new(settingsFileContent)
            SETTINGS.setProject('platform-core')
            SETTINGS.setBranch(env.BRANCH_NAME)
            Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')
            def coverageFolder = Utilities.getCoverageFolder(this)

            try {
                stage('Checkout'){
                    deleteDir()
                    
                    checkout scm
                    
					
                }

                if(!Utilities.areThereCodeChanges(this))
                {
                    echo "There are no Code Changes"
                    currentBuild.result = 'SUCCESS'
                    return 0;
                }

                stage('Build'){
                    withSonarQubeEnv('VC Sonar Server'){
                        if(Utilities.isPullRequest(this))
                        {
                            powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -PullRequest -GitHubToken ${env.GITHUB_TOKEN} -skip Restore+Compile"
                        }
                        else
                        {
                            powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -skip Restore+Compile"
                        }
                    }
                    Packaging.startAnalyzer(this, true)
                    if(Utilities.isPullRequest)
                    {
                        withEnv(["BRANCH_NAME=${env.CHANGE_BRANCH}"])
                        {
                            powershell "vc-build Compile"
                        }
                    }
                    else
                    {
                        powershell "vc-build Compile"
                    }
                    
                }
                
                stage('Unit Tests'){
                    powershell "vc-build Test -skip Restore+Compile"
                } 

                stage('Quality Gate'){
                    //Packaging.endAnalyzer(this)
                    withSonarQubeEnv('VC Sonar Server'){
                        powershell "vc-build SonarQubeEnd -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken ${env.SONAR_AUTH_TOKEN} -skip Restore+Compile+SonarQubeStart"
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
                    powershell "vc-build Compress -skip Clean+Restore+Compile+Test"

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
						def artifacts = findFiles(glob: 'artifacts\\*.zip')
						Packaging.saveArtifact(this, 'vc', Utilities.getProjectType(this), '', artifacts[0].path)
                        // def ghReleaseResult = Utilities.runBatchScript(this, "@vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test")
                        // if(ghReleaseResult['status'] != 0){
                        //     def nugetAlreadyExists = false
                        //     for(line in ghReleaseResult['stdout']){
                        //         if(line.contains("error: Response status code does not indicate success: 409")){
                        //             nugetAlreadyExists = true
                        //         }
                        //     }
                        //     if(nugetAlreadyExists){
                        //         UNSTABLE_CAUSES.add("Nuget package already exists.")
                        //     }
                        //     else{
                        //         echo "${ghReleaseResult['stdout']}"
                        //         throw new Exception("ERROR: script returned exit code -1")
                        //     }
                        // }
                        def ghReleaseResult = powershell script: "vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test", returnStatus: true
                        if(ghReleaseResult == 409)
                        {
                            UNSTABLE_CAUSES.add("Nuget package already exists.")
                        } 
                        else if(ghReleaseResult != 0)
                        {
                            throw new Exception("ERROR: script returned ${ghReleaseResult}")
                        }

                    //     def orgName = Utilities.getOrgName(this)
                    //     powershell "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} -PreRelease -skip Clean+Restore+Compile+Test"
                    }
                    // stage('Deploy')
                    // {
                    //     // $ZipFile,
                    //     // $WebAppName,
                    //     // $ResourceGroupName,
                    //     // $SubscriptionID,
                    //     // $DestContentPath = ""
                    //     def artifacts = findFiles(glob: "artifacts/*.zip")
                    //     def artifactPath = artifacts[0].path
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"platform\"")
                    // }
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