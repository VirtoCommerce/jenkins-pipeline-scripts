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
        def workspace = "S:\\Buildsv3\\${repoName}\\${escapedBranch}"
        projectType = 'NETCORE2'
        def platformDockerTag = 'latest-win'
        def platformLinuxDockerTag = 'latest'
        def storefrontDockerTag = 'latest'
        def releaseNotesPath = "${workspace}\\release_notes.txt"
        if(env.BRANCH_NAME == 'dev')
        {
            platformDockerTag = '3.0-dev'
            platformLinuxDockerTag = '3.0-dev-linux'
            storefrontDockerTag = 'dev-branch'
        }
        def dockerWinImage
        def dockerLinImage
        dir(workspace){
            def SETTINGS
            def settingsFileContent
            configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
                settingsFileContent = readFile(SETTINGS_FILE)
            }
            SETTINGS = new Settings(settingsFileContent)
            SETTINGS.setProject('platform-core')
            SETTINGS.setBranch(env.BRANCH_NAME)
            Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')
            def coverageFolder = Utilities.getCoverageFolder(this)
            
            def commitHash
            def commitNumber
            def platformVersion
            def versionSuffixArg

            try {
                stage('Checkout')
                {
                    deleteDir()
                    
                    checkout scm

                    commitHash = Utilities.getCommitHash(this)
                    commitNumber = Utilities.getCommitNumber(this)
                    versionSuffixArg = env.BRANCH_NAME == 'dev' ? "-CustomVersionSuffix \"alpha.${commitNumber}\"" : ""

                    // try
                    // {
                    //     def release = GithubRelease.getLatestGithubReleaseV3(this, Utilities.getOrgName(this), Utilities.getRepoName(this), env.BRANCH_NAME == 'master' ? false : true)
                    //     echo release.published_at
                    //     def releaseNotes = Utilities.getReleaseNotesFromCommits(this, release.published_at)
                    //     echo releaseNotes
                    //     writeFile file: releaseNotesPath, text: releaseNotes
                    // }
                    // catch(any)
                    // {
                    //     echo "exception:"
                    //     echo any.getMessage()
                    // }
                }

                if(!Utilities.areThereCodeChanges(this))
                {
                    echo "There are no Code Changes"
                    currentBuild.result = 'SUCCESS'
                    return 0;
                }

                stage('Build'){
                    if(versionSuffixArg != "")
                    {
                        pwsh "vc-build ChangeVersion ${versionSuffixArg}"
                    }
                    pwsh "vc-build Compile"
                    // if(Utilities.isPullRequest(this))
                    // {
                    //     // withSonarQubeEnv('VC Sonar Server'){
                    //         withEnv(["BRANCH_NAME=${env.CHANGE_BRANCH}"])
                    //         {
                    //     //         powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -PullRequest -GitHubToken ${env.GITHUB_TOKEN} -skip Restore+Compile"
                    //             powershell "vc-build Compile"
                    //         }
                    //     // }
                    // }
                    // else
                    // {
                    //     // withSonarQubeEnv('VC Sonar Server'){
                    //     //     powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -skip Restore+Compile"
                    //     // }
                    //     powershell "vc-build Compile ${versionSuffixArg}"
                    // }
                    
                }
                
                stage('Unit Tests')
                {
                    powershell "vc-build Test -skip"
                } 

                // stage('Quality Gate'){
                //     // Packaging.endAnalyzer(this)
                //     withSonarQubeEnv('VC Sonar Server'){
                //         powershell "vc-build SonarQubeEnd -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken ${env.SONAR_AUTH_TOKEN} -skip Restore+Compile+SonarQubeStart"
                //     }
                //     Packaging.checkAnalyzerGate(this)
                // }  

                // stage('Swagger')
                // {
                //     dir("${workspace}\\src\\VirtoCommerce.Platform.Web"){
                //         bat "vc-build SwaggerValidation -Skip Restore+Compile+Publish"
                //     }
                // }

                stage('Packaging'){
                             
                    powershell "vc-build Compress -skip Clean+Restore+Compile+Test"

                    platformVersion = pwsh (script: "(Get-Item artifacts\\publish\\VirtoCommerce.Platform.Web.dll).VersionInfo.ProductVersion", returnStdout: true, label: "Get platform version").trim()
                    echo "Platform version: ${platformVersion}"

                    if(env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'dev'){
                        def websitePath = Utilities.getWebPublishFolder(this, "docker")
                        def dockerImageName = "virtocommerce/platform"
                        powershell script: "Copy-Item ${workspace}\\artifacts\\publish\\* ${websitePath}\\VirtoCommerce.Platform -Recurse -Force"
                        powershell script: "Copy-Item ${env.WORKSPACE}\\..\\workspace@libs\\virto-shared-library\\resources\\docker.core\\windowsnano\\PlatformCore\\* ${websitePath} -Force"
                        dir(websitePath){
                            dockerWinImage = docker.build("${dockerImageName}:${platformDockerTag}")
                            stash includes: 'VirtoCommerce.Platform/**', name: 'artifact'
                        }
                        node('linux')
                        {
                            unstash 'artifact'
                            def dockerfileContent = libraryResource 'docker.core/linux/platform/Dockerfile'
                            writeFile file: "${env.WORKSPACE}/Dockerfile", text: dockerfileContent
                            dockerLinImage = docker.build("${dockerImageName}:${platformLinuxDockerTag}")
                        }
                    }
                }

                def artifacts
                stage('Saving Artifacts')
                {
                    timestamps
                    {
                        artifacts = findFiles(glob: 'artifacts\\*.zip')

                        if(env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'feature/migrate-to-vc30' || env.BRANCH_NAME.startsWith("feature/") || env.BRANCH_NAME.startsWith("bug/"))
                        {
                            Packaging.saveArtifact(this, 'vc', Utilities.getProjectType(this), '', artifacts[0].path)
                        }
                    }
                }

                if(env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'dev')
                {
                    // stage('Create Test Environment')
                    // {
                    //     timestamps
                    //     {
                    //         dir(Utilities.getComposeFolderV3(this))
                    //         {
                    //             def platformPort = Utilities.getPlatformPort(this)
                    //             def storefrontPort = Utilities.getStorefrontPort(this)
                    //             def sqlPort = Utilities.getSqlPort(this)
                    //             withEnv(["PLATFORM_DOCKER_TAG=${platformDockerTag}", "STOREFRONT_DOCKER_TAG=${storefrontDockerTag}", "DOCKER_PLATFORM_PORT=${platformPort}", "DOCKER_STOREFRONT_PORT=${storefrontPort}", "DOCKER_SQL_PORT=${sqlPort}", "COMPOSE_PROJECT_NAME=${env.BUILD_TAG}"]) {
                    //                 bat "docker-compose up -d"
                    //             }
                    //         }
                    //     }
                    // }
                    // stage('Install Modules')
                    // {
                    //     timestamps
                    //     {
                    //         def platformHost = Utilities.getPlatformCoreHost(this)
                    //         def platformContainerId = Utilities.getPlatformContainer(this)
                    //         echo "Platform Host: ${platformHost}"
                    //         sleep 90
                    //         Utilities.runPS(this, "docker_v3/vc-setup-modules.ps1", "-ApiUrl ${platformHost} -NeedRestart -ContainerId ${platformContainerId} -Verbose -Debug")
                    //         sleep 90
                    //         Utilities.runPS(this, "docker_v3/vc-check-installed-modules.ps1", "-ApiUrl ${platformHost} -Verbose -Debug")
                    //     }
                    // }
                    // stage('Install Sample Data')
                    // {
                    //     timestamps
                    //     {
                    //         Utilities.runPS(this, "docker_v3/vc-setup-sampledata.ps1", "-ApiUrl ${Utilities.getPlatformCoreHost(this)} -Verbose -Debug")
                    //     }
                    // }
                    // stage("Swagger Schema Validation")
                    // {
                    //     timestamps
                    //     {
                    //         def swaggerSchemaPath = "${workspace}\\swaggerSchema${env.BUILD_NUMBER}.json"
                    //         Utilities.runPS(this, "docker_v3/vc-get-swagger.ps1", "-ApiUrl ${Utilities.getPlatformCoreHost(this)} -OutFile ${swaggerSchemaPath} -Verbose -Debug")
                    //         def swaggerResult = powershell script: "vc-build ValidateSwaggerSchema -SwaggerSchemaPath ${swaggerSchemaPath}", returnStatus: true
                    //         if(swaggerResult != 0)
                    //         {
                    //             UNSTABLE_CAUSES.add("Swagger Schema contains error")
                    //         }
                    //     }
                    // }

                    if(env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master')
                    {
                        stage('Publish')
                        {
                            // def ghReleaseResult = powershell script: "vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test", returnStatus: true
                            // if(ghReleaseResult == 409)
                            // {
                            //     UNSTABLE_CAUSES.add("Nuget package already exists.")
                            // } 
                            // else if(ghReleaseResult != 0)
                            // {
                            //     throw new Exception("ERROR: script returned ${ghReleaseResult}")
                            // }

                            // if(env.BRANCH_NAME == 'dev')
                            // {
                            //     def artifactPath = artifacts[0].path
                            //     def artifactFileName = artifacts[0].name
                            //     powershell script: "${env.Utils}\\AzCopy10\\AzCopy.exe copy \"${artifactPath}\" \"https://vc3prerelease.blob.core.windows.net/packages${env.ARTIFACTS_BLOB_TOKEN}\"", label: "AzCopy"
                            //     // def orgName = Utilities.getOrgName(this)
                            //     // def releaseNotesFile = new File(releaseNotesPath)
                            //     // def releaseNotesArg = releaseNotesFile.exists() ? "-ReleaseNotes ${releaseNotesFile}" : ""
                            //     // def releaseResult = powershell script: "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} ${releaseNotesArg} -PreRelease -skip Clean+Restore+Compile+Test", returnStatus: true
                            //     // if(releaseResult == 422){
                            //     //     UNSTABLE_CAUSES.add("Release already exists on github")
                            //     // } else if(releaseResult !=0 ) {
                            //     //     throw new Exception("Github release error")
                            //     // }

                            //     return 0
                            // }
                            
                            Packaging.pushDockerImage(this, dockerWinImage, platformDockerTag)
                            if(env.BRANCH_NAME == 'master')
                            {
                                Packaging.pushDockerImage(this, dockerWinImage, "${platformVersion}-${commitHash}-win")
                            }
                            node('linux')
                            {
                                Packaging.pushDockerImage(this, dockerLinImage, platformLinuxDockerTag)
                                if(env.BRANCH_NAME == 'master')
                                {
                                    Packaging.pushDockerImage(this, dockerLinImage, "${platformVersion}-${commitHash}")
                                }
                            }
                            
                            // def orgName = Utilities.getOrgName(this)
                            // def releaseNotesFile = new File(releaseNotesPath)
                            // def releaseNotesArg = releaseNotesFile.exists() ? "-ReleaseNotes ${releaseNotesFile}" : ""
                            // def releaseBranchArg = "-ReleaseBranch ${Utilities.getReleaseBranch(this)}"
                            // def releaseResult = powershell script: "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} ${releaseBranchArg} ${releaseNotesArg} -skip Clean+Restore+Compile+Test", returnStatus: true
                            // if(releaseResult == 422){
                            //     UNSTABLE_CAUSES.add("Release already exists on github")
                            // } else if(releaseResult !=0 ) {
                            //     throw new Exception("Github release error")
                            // }

                        //     def orgName = Utilities.getOrgName(this)
                        //     powershell "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} -PreRelease -skip Clean+Restore+Compile+Test"
                        }
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
                // if(env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'dev')
                // {
                //     dir(Utilities.getComposeFolderV3(this))
                //     {
                //         def platformPort = Utilities.getPlatformPort(this)
                //         def storefrontPort = Utilities.getStorefrontPort(this)
                //         def sqlPort = Utilities.getSqlPort(this)
                //         withEnv(["PLATFORM_DOCKER_TAG=${platformDockerTag}", "STOREFRONT_DOCKER_TAG=${storefrontDockerTag}", "DOCKER_PLATFORM_PORT=${platformPort}", "DOCKER_STOREFRONT_PORT=${storefrontPort}", "DOCKER_SQL_PORT=${sqlPort}", "COMPOSE_PROJECT_NAME=${env.BUILD_TAG}"]) {
                //             bat "docker-compose down -v"
                //         }
                //     }
                // }
                if(currentBuild.resultIsBetterOrEqualTo('SUCCESS') && UNSTABLE_CAUSES.size()>0){
                    currentBuild.result = 'UNSTABLE'
                    for(cause in UNSTABLE_CAUSES){
                        echo cause
                    }
                }
			    Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
                // dir(Utilities.getComposeFolderV3(this))
                // {
                //     withEnv(["DOCKER_TAG=dev-branch", "COMPOSE_PROJECT_NAME=${env.BUILD_TAG}"]) {
                //         bat "docker-compose down -v"
                //     }
                // }
                Utilities.cleanPRFolder(this)
            }
        }
    }
}