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
        dir(workspace){
            // def SETTINGS
            // def settingsFileContent
            // configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
            //     settingsFileContent = readFile(SETTINGS_FILE)
            // }
            // SETTINGS = globalLib.Settings.new(settingsFileContent)
            // SETTINGS.setProject('platform-core')
            // SETTINGS.setBranch(env.BRANCH_NAME)
            try {
                stage('Checkout'){
                    deleteDir()
                    checkout scm
                }

                stage('Build'){
                    if(Utilities.isPullRequest(this))
                    {
                        withSonarQubeEnv('VC Sonar Server'){
                            withEnv(["BRANCH_NAME=${env.CHANGE_BRANCH}"])
                            {
                                powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -PullRequest -GitHubToken ${env.GITHUB_TOKEN} -skip Restore+Compile"
                                powershell "vc-build Compile"
                            }
                        }
                    }
                    else
                    {
                        withSonarQubeEnv('VC Sonar Server'){
                            powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -skip Restore+Compile"
                        }
                        powershell "vc-build Compile"
                    }
                }

                stage('Unit Tests'){
                    powershell "vc-build Test -skip Restore+Compile"
                } 

                stage('Quality Gate'){
                    sleep time: 15
                    // withSonarQubeEnv('VC Sonar Server'){
                    //     powershell "vc-build SonarQubeEnd -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken ${env.SONAR_AUTH_TOKEN} -skip Restore+Compile+SonarQubeStart"
                    // }
                    Packaging.endAnalyzer(this)
                    Packaging.checkAnalyzerGate(this)
                }
                 

                stage('Packaging'){                
                    powershell "vc-build Compress -skip Clean+Restore+Compile+Test"
                }

                if(!Utilities.isPullRequest(this)){
                    stage('Publish'){
						def artifacts = findFiles(glob: 'artifacts\\*.zip')
                        def artifactFileName = artifacts[0].path.split("\\\\").last()
                        def moduleId = artifactFileName.split("_").first()
                        echo "Module id: ${moduleId}"
						Packaging.saveArtifact(this, 'vc', 'module', moduleId, artifacts[0].path)
                        
                        // def gitversionOutput = powershell (script: "dotnet gitversion", returnStdout: true, label: 'Gitversion', encoding: 'UTF-8').trim()
                        // def gitversionJson = new groovy.json.JsonSlurperClassic().parseText(gitversionOutput)
                        // def commitNumber = gitversionJson['CommitsSinceVersionSource']
                        // def moduleArtifactName = "${moduleId}_3.0.0-build.${commitNumber}"
                        // echo "artifact version: ${moduleArtifactName}"
                        // def artifactPath = "${workspace}\\artifacts\\${moduleArtifactName}.zip"
                        // powershell "Copy-Item ${artifacts[0].path} -Destination ${artifactPath}"
                        // powershell script: "${env.Utils}\\AzCopy10\\AzCopy.exe copy \"${artifactPath}\" \"https://vc3prerelease.blob.core.windows.net/packages${env.ARTIFACTS_BLOB_TOKEN}\"", label: "AzCopy"
                        
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
                        
                        def orgName = Utilities.getOrgName(this)
                        def releaseResult = powershell script: "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} -PreRelease -skip Clean+Restore+Compile+Test", returnStatus: true
                        if(releaseResult == 422){
                            UNSTABLE_CAUSES.add("Release already exists on github")
                        } else if(releaseResult !=0 ) {
                            throw new Exception("Github release error")
                        }

                        def mmStatus = bat script: "vc-build PublishModuleManifest > out.log", returnStatus: true
                        def mmout = readFile "out.log"
                        echo mmout
                        if(mmStatus!=0){
                            def nothingToCommit = false
                            for(line in mmout.trim().split("\n")){
                                if(line.contains("nothing to commit, working tree clean")){
                                    nothingToCommit = true
                                }
                            }
                            if(nothingToCommit){
                                UNSTABLE_CAUSES.add("Module Manifest: nothing to commit, working tree clean")
                            } else {
                                throw new Exception("Module Manifest: returned nonzero exit status")
                            }
                        }
                    }

                    // stage('Deploy'){
                    //     def moduleId = Modules.getModuleId(this)
                    //     def artifacts = findFiles(glob: "artifacts/*.zip")
                    //     def artifactPath = artifacts[0].path
                    //     def dstContentPath = "modules\\${moduleId}"
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")

                    //     SETTINGS.setRegion('platform-core')
                    //     SETTINGS.setEnvironment('odtDev')
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")

                    //     SETTINGS.setRegion('platform-core')
                    //     SETTINGS.setEnvironment('odtQa')
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")

                    //     SETTINGS.setRegion('platform-core')
                    //     SETTINGS.setEnvironment('odtDemo')
                    //     Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"${dstContentPath}\"")

                    //     //Utilities.runSharedPS(this, "v3\\Restart-WebApp.ps1", "-WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']}")
                    // }
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
                Utilities.cleanPRFolder(this)
            }
        }
    }
}