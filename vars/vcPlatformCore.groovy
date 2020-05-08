def Modules
def Packaging
def Utilities
def GithubRelease

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
        GithubRelease = globalLib.GithubRelease

        def escapedBranch = env.BRANCH_NAME.replaceAll('/', '_')
        def repoName = Utilities.getRepoName(this)
        def workspace = "S:\\Buildsv3\\${repoName}\\${escapedBranch}"
        projectType = 'NETCORE2'
        def platformDockerTag = '3.0-preview'
        def storefrontDockerTag = 'latest'
        if(env.BRANCH_NAME == 'dev-3.0.0')
        {
            platformDockerTag = '3.0-dev'
            storefrontDockerTag = 'dev-branch'
        }
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

                    try
                    {
                        def release = GithubRelease.getLatestGithubReleaseRegexp(this, Utilities.getOrgName(this), Utilities.getRepoName(this), /\d\.\d\.\d[\s]{0,1}[\w]*/, true)
                        def releaseNotes = Utilities.getReleaseNotesFromCommits(this, release.published_at)
                        echo releaseNotes
                    }
                    catch(any)
                    {
                        echo "exception:"
                        echo any.getMessage()
                    }
                    
//                     if(env.BRANCH_NAME == 'release/3.0.0')
//                     {
//                         def commitId = pwsh(returnStdout: true, script: 'git rev-parse HEAD').trim()
//                         def prevCommitId = pwsh(returnStdout: true, script: 'git rev-parse HEAD^1').trim()
// 					def changelog = gitChangelog from: [type: 'REF', value: "3.0.0-rc.5.248"], to: [type: 'REF', value: "3.0.0-rc.5.250"], returnType: 'STRING', template: '''# Changelog

// Changelog for {{ownerName}} {{repoName}}.

// {{#tags}}
// ## {{name}}
//  {{#issues}}
//   {{#hasIssue}}
//    {{#hasLink}}
// ### {{name}} [{{issue}}]({{link}}) {{title}} {{#hasIssueType}} *{{issueType}}* {{/hasIssueType}} {{#hasLabels}} {{#labels}} *{{.}}* {{/labels}} {{/hasLabels}}
//    {{/hasLink}}
//    {{^hasLink}}
// ### {{name}} {{issue}} {{title}} {{#hasIssueType}} *{{issueType}}* {{/hasIssueType}} {{#hasLabels}} {{#labels}} *{{.}}* {{/labels}} {{/hasLabels}}
//    {{/hasLink}}
//   {{/hasIssue}}
//   {{^hasIssue}}
// ### {{name}}
//   {{/hasIssue}}

//   {{#commits}}
// **{{{messageTitle}}}**

// {{#messageBodyItems}}
//  * {{.}} 
// {{/messageBodyItems}}

// [{{hash}}](https://github.com/{{ownerName}}/{{repoName}}/commit/{{hash}}) {{authorName}} *{{commitTime}}*

//   {{/commits}}

//  {{/issues}}
// {{/tags}}'''
//                     echo changelog
//                     }
                }

                if(!Utilities.areThereCodeChanges(this))
                {
                    echo "There are no Code Changes"
                    currentBuild.result = 'SUCCESS'
                    return 0;
                }

                stage('Build'){
                    
                    // Packaging.startAnalyzer(this, true)
                    // withSonarQubeEnv('VC Sonar Server'){
                    //     if(Utilities.isPullRequest(this))
                    //     {
                    //         powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -PullRequest -GitHubToken ${env.GITHUB_TOKEN} -skip Restore+Compile"
                    //     }
                    //     else
                    //     {
                    //         powershell "vc-build SonarQubeStart -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken \"${env.SONAR_AUTH_TOKEN}\" -skip Restore+Compile"
                    //     }
                    // }
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
                    // Packaging.endAnalyzer(this)
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

                    if(env.BRANCH_NAME == 'release/3.0.0' || env.BRANCH_NAME == 'dev-3.0.0'){
                        def websitePath = Utilities.getWebPublishFolder(this, "docker")
                        def dockerImageName = "virtocommerce/platform"
                        powershell script: "Copy-Item ${workspace}\\artifacts\\publish\\* ${websitePath}\\VirtoCommerce.Platform -Recurse -Force"
                        powershell script: "Copy-Item ${env.WORKSPACE}\\..\\workspace@libs\\virto-shared-library\\resources\\docker.core\\windowsnano\\PlatformCore\\* ${websitePath} -Force"
                        dir(websitePath){
                            bat "dotnet dev-certs https -ep \"${websitePath}\\devcert.pfx\" -p virto"
                            docker.build("${dockerImageName}:${platformDockerTag}")
                        }
                    }
                }

                if(env.BRANCH_NAME == 'release/3.0.0' || env.BRANCH_NAME == 'dev-3.0.0')
                {
                    stage('Create Test Environment')
                    {
                        timestamps
                        {
                            dir(Utilities.getComposeFolderV3(this))
                            {
                                def platformPort = Utilities.getPlatformPort(this)
                                def storefrontPort = Utilities.getStorefrontPort(this)
                                def sqlPort = Utilities.getSqlPort(this)
                                withEnv(["PLATFORM_DOCKER_TAG=${platformDockerTag}", "STOREFRONT_DOCKER_TAG=${storefrontDockerTag}", "DOCKER_PLATFORM_PORT=${platformPort}", "DOCKER_STOREFRONT_PORT=${storefrontPort}", "DOCKER_SQL_PORT=${sqlPort}", "COMPOSE_PROJECT_NAME=${env.BUILD_TAG}"]) {
                                    bat "docker-compose up -d"
                                }
                            }
                        }
                    }
                    stage('Install Modules')
                    {
                        timestamps
                        {
                            def platformHost = Utilities.getPlatformCoreHost(this)
                            def platformContainerId = Utilities.getPlatformContainer(this)
                            echo "Platform Host: ${platformHost}"
                            Utilities.runPS(this, "docker_v3/vc-setup-modules.ps1", "-ApiUrl ${platformHost} -NeedRestart -ContainerId ${platformContainerId} -Verbose -Debug")
                            Utilities.runPS(this, "docker_v3/vc-check-installed-modules.ps1", "-ApiUrl ${platformHost} -Verbose -Debug")
                        }
                    }
                    stage('Install Sample Data')
                    {
                        timestamps
                        {
                            Utilities.runPS(this, "docker_v3/vc-setup-sampledata.ps1", "-ApiUrl ${Utilities.getPlatformCoreHost(this)} -Verbose -Debug")
                        }
                    }
                    stage("Swagger Schema Validation")
                    {
                        timestamps
                        {
                            def swaggerSchemaPath = "${workspace}\\swaggerSchema${env.BUILD_NUMBER}.json"
                            Utilities.runPS(this, "docker_v3/vc-get-swagger.ps1", "-ApiUrl ${Utilities.getPlatformCoreHost(this)} -OutFile ${swaggerSchemaPath} -Verbose -Debug")
                            def swaggerResult = powershell script: "vc-build ValidateSwaggerSchema -SwaggerSchemaPath ${swaggerSchemaPath}", returnStatus: true
                            if(swaggerResult != 0)
                            {
                                UNSTABLE_CAUSES.add("Swagger Schema contains error")
                            }
                        }
                    }
                }

                if(env.BRANCH_NAME == 'release/3.0.0' || env.BRANCH_NAME == 'dev-3.0.0')
                {
                    try
                    {
                        stage('Create Test Environment')
                        {
                            timestamps
                            {
                                dir(Utilities.getComposeFolderV3(this))
                                {
                                    def platformPort = Utilities.getPlatformPort(this)
                                    def storefrontPort = Utilities.getStorefrontPort(this)
                                    def sqlPort = Utilities.getSqlPort(this)
                                    withEnv(["DOCKER_TAG=dev-branch", "DOCKER_PLATFORM_PORT=${platformPort}", "DOCKER_STOREFRONT_PORT=${storefrontPort}", "DOCKER_SQL_PORT=${sqlPort}", "COMPOSE_PROJECT_NAME=${env.BUILD_TAG}" ]) {
                                        bat "docker-compose up -d"
                                    }
                                }
                            }
                        }
                        stage('Install Modules')
                        {
                            timestamps
                            {
                                def platformHost = Utilities.getPlatformCoreHost(this)
                                def platformContainerId = Utilities.getPlatformContainer(this)
                                echo "Platform Host: ${platformHost}"
                                Utilities.runPS(this, "docker_v3/vc-setup-modules.ps1", "-ApiUrl ${platformHost} -needRestart 1 -ContainerId ${platformContainerId} -Verbose")
                            }
                        }
                        stage('Install Sample Data')
                        {
                            timestamps
                            {
                                Utilities.runPS(this, "docker_v3/vc-setup-sampledata.ps1", "-ApiUrl ${Utilities.getPlatformCoreHost(this)} -Verbose")
                            }
                        }
                        stage("Swagger Schema Validation")
                        {
                            timestamps
                            {
                                Utilities.runPS(this, "docker_v3/vc-get-swagger.ps1", "-ApiUrl ${Utilities.getPlatformCoreHost(this)} -OutFile ${workspace}\\artifacts\\swaggerSchema${env.BUILD_NUMBER}.json -Verbose")
                            }
                        }
                    }
                    catch(any)
                    {
                        echo any.getMessage()
                    }

                    stage('Publish')
                    {
                        // powershell "vc-build PublishPackages -ApiKey ${env.NUGET_KEY} -skip Clean+Restore+Compile+Test"
						def artifacts = findFiles(glob: 'artifacts\\*.zip')
						Packaging.saveArtifact(this, 'vc', Utilities.getProjectType(this), '', artifacts[0].path)

                        if(env.BRANCH_NAME == 'dev-3.0.0')
                        {
                            def gitversionOutput = powershell (script: "dotnet gitversion", returnStdout: true, label: 'Gitversion', encoding: 'UTF-8').trim()
                            def gitversionJson = new groovy.json.JsonSlurperClassic().parseText(gitversionOutput)
                            def commitNumber = gitversionJson['CommitsSinceVersionSource']
                            def platformArtifactName = "VirtoCommerce.Platform_3.0.0-build.${commitNumber}"
                            echo "artifact version: ${platformArtifactName}"
                            def artifactPath = "${workspace}\\artifacts\\${platformArtifactName}.zip"
                            powershell "Copy-Item ${artifacts[0].path} -Destination ${artifactPath}"
                            powershell script: "${env.Utils}\\AzCopy10\\AzCopy.exe copy \"${artifactPath}\" \"https://vc3prerelease.blob.core.windows.net/packages${env.ARTIFACTS_BLOB_TOKEN}\"", label: "AzCopy"
                            return 0
                        }
                        

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

                        def orgName = Utilities.getOrgName(this)
                        def releaseResult = powershell script: "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} -PreRelease -skip Clean+Restore+Compile+Test", returnStatus: true
                        if(releaseResult == 422){
                            UNSTABLE_CAUSES.add("Release already exists on github")
                        } else if(releaseResult !=0 ) {
                            throw new Exception("Github release error")
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
                if(env.BRANCH_NAME == 'release/3.0.0' || env.BRANCH_NAME == 'dev-3.0.0')
                {
                    dir(Utilities.getComposeFolderV3(this))
                    {
                        def platformPort = Utilities.getPlatformPort(this)
                        def storefrontPort = Utilities.getStorefrontPort(this)
                        def sqlPort = Utilities.getSqlPort(this)
                        withEnv(["PLATFORM_DOCKER_TAG=${platformDockerTag}", "STOREFRONT_DOCKER_TAG=${storefrontDockerTag}", "DOCKER_PLATFORM_PORT=${platformPort}", "DOCKER_STOREFRONT_PORT=${storefrontPort}", "DOCKER_SQL_PORT=${sqlPort}", "COMPOSE_PROJECT_NAME=${env.BUILD_TAG}"]) {
                            bat "docker-compose down -v"
                        }
                    }
                }
                if(currentBuild.resultIsBetterOrEqualTo('SUCCESS') && UNSTABLE_CAUSES.size()>0){
                    currentBuild.result = 'UNSTABLE'
                    for(cause in UNSTABLE_CAUSES){
                        echo cause
                    }
                }
			    Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
                dir(Utilities.getComposeFolderV3(this))
                {
                    withEnv(["DOCKER_TAG=dev-branch", "COMPOSE_PROJECT_NAME=${env.BUILD_TAG}"]) {
                        bat "docker-compose down -v"
                    }
                }
                Utilities.cleanPRFolder(this)
            }
        }
    }
}