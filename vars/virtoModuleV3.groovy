import jobs.scripts.*

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

    def UNSTABLE_CAUSES = []

    node {
        def escapedBranch = env.BRANCH_NAME.replaceAll('/', '_')
        def repoName = Utilities.getRepoName(this)
        def workspace = "D:\\Buildsv3\\${repoName}\\${escapedBranch}"
        dir(workspace){
            def SETTINGS
            def settingsFileContent
            configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
                settingsFileContent = readFile(SETTINGS_FILE)
            }
            SETTINGS = new Settings(settingsFileContent)
            SETTINGS.setRegion('platform-core')
            SETTINGS.setEnvironment(env.BRANCH_NAME)
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

                stage('Packaging'){                
                    bat "vc-build Compress -skip Clean+Restore+Compile+Test"
                }

                stage('Unit Tests'){
                    bat "vc-build Test -skip Restore+Compile"
                }   

                if(!Utilities.isPullRequest(this)){
                    stage('Publish'){
                        // def publishPackagesStatus = bat script:"@vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test > out.log", returnStatus: true
                        // def publishPackagesOut = readFile "out.log"
                        // echo publishPackagesOut
                        // if(publishPackagesStatus != 0){
                        //     def nugetAlreadyExists = false
                        //     def lines = publishPackagesOut.trim().split("\n")
                        //     for(line in lines){
                        //         if(line.contains("error: Response status code does not indicate success: 409")){
                        //             nugetAlreadyExists = true
                        //         }
                        //     }
                        //     if(nugetAlreadyExists){
                        //         UNSTABLE_CAUSES.add("Nuget package already exists.")
                        //     }
                        //     else{
                        //         throw new Exception("ERROR: script returned exit code -1")
                        //     }
                        // }

                        def stdout
                        try {
                            stdout = bat script:"@vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test", returnStdout: true
                        }
                        catch(any){
                            if(!stdout.contains("error: Response status code does not indicate success: 409")){
                                throw any
                            }
                        }
                        finally {
                            echo stdout
                        }


                        // bat script: "vc-build PublishModuleManifest > out.txt", returnStatus: true
                        // def orgName = Utilities.getOrgName(this)
                        // bat "@vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} -PreRelease -skip Clean+Restore+Compile+Test"
                    }

                    stage('Deploy'){
                        def moduleId = Modules.getModuleId(this)
                        def artifacts = findFiles(glob: "artifacts/*.zip")
                        def artifactPath = artifacts[0].path
                        def dstContentPath = "modules\\${moduleId}"
                        Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")
                        //Utilities.runSharedPS(this, "v3\\Restart-WebApp.ps1", "-WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']}")
                    }
                }
            }
            catch (any) {
                currentBuild.result = 'FAILURE'
                throw any
            }
            finally{
                if(currentBuild.resultIsBetterOrEqualTo('SUCCESS') && UNSTABLE_CAUSES.size()>0){
                    currentBuild.result = 'UNSTABLE'
                    for(cause in UNSTABLE_CAUSES){
                        echo cause
                    }
                }
            }
        }
    }
}