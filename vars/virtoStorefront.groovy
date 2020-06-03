#!groovy

// module script
def call(body) {

	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
    
	node
	{
		properties([disableConcurrentBuilds()])
		// configuration parameters
		def hmacAppId = env.HMAC_APP_ID
		def hmacSecret = env.HMAC_SECRET
		def solution = config.solution
		projectType = config.projectType

		def globalLib = library('global-shared-lib').com.test
		def Utilities = globalLib.Utilities
		def Packaging = globalLib.Packaging
		def Docker = globalLib.Docker

        def workspace = env.WORKSPACE.replaceAll('%2F', '_')
		dir(workspace)
		{
			def webProject = 'VirtoCommerce.Platform.Web\\VirtoCommerce.Platform.Web.csproj'
			def zipArtifact = 'VirtoCommerce.Platform'
			def websiteDir = 'VirtoCommerce.Platform.Web'
			def deployScript = 'VC-WebApp2Azure.ps1'
			def dockerTag = "${env.BRANCH_NAME}-branch"
			def dockerTagLinux = "3.0-dev-linux"
			def runtimeImage = ""
			def storefrontImageName = 'virtocommerce/storefront'
			def buildOrder = Utilities.getNextBuildOrder(this)
			def themeBranch
        	def releaseNotesPath = "${workspace}\\release_notes.txt"
			if (env.BRANCH_NAME == 'support/2.x') {
				
			}
			switch(env.BRANCH_NAME)
			{
				case 'support/2.x':
					dockerTag = "2.0"
					dockerTagLinux = '2.0-linux'
					themeBranch = 'master'
				break
				case 'support/2.x-dev':
					dockerTag = "2.0-dev-branch"
					dockerTagLinux = '2.0-dev-linux'
					themeBranch = 'dev'
				break
				case 'dev':
					dockerTag = "3.0-dev"
					dockerTagLinux = '3.0-dev-linux'
					runtimeImage = "mcr.microsoft.com/dotnet/core/aspnet:3.1"
					themeBranch = 'dev'
				break
				case 'master':
					dockerTag = "3.0"
					dockerTagLinux = '3.0-linux'
					runtimeImage = "mcr.microsoft.com/dotnet/core/aspnet:3.1"
					themeBranch = 'master'
				break
			}

			def SETTINGS
			def settingsFileContent
			configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
				settingsFileContent = readFile(SETTINGS_FILE)
			}
			SETTINGS = globalLib.Settings.new(settingsFileContent)
			SETTINGS.setBranch(env.BRANCH_NAME)
			
			if(projectType == null)
			{
				projectType = "NET4"
			}

			if(solution == null)
			{
				solution = 'VirtoCommerce.Platform.sln'
			}
			else
			{
				websiteDir = 'VirtoCommerce.Storefront'
				webProject = 'VirtoCommerce.Storefront\\VirtoCommerce.Storefront.csproj'
				zipArtifact = 'VirtoCommerce.StoreFront'
			}
			if(Utilities.isNetCore(projectType)){
				SETTINGS.setProject('storefront')
			} else {
				SETTINGS.setProject('platform')
			}

			def commitNumber
			def versionSuffixArg
			
			try {
				Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')

				stage('Checkout') {
					timestamps { 
						deleteDir()

						checkout scm

						powershell "if(!(Test-Path -Path .\\.nuke)){ Get-ChildItem *.sln -Name > .nuke }"

						commitNumber = Utilities.getCommitHash(this)
                    	versionSuffixArg = env.BRANCH_NAME == 'dev' ? "-CustomTagSuffix \"_build_${commitNumber}\"" : ""

						try
						{
							def release = GithubRelease.getLatestGithubReleaseV3(this, Utilities.getOrgName(this), Utilities.getRepoName(this))
							echo release.published_at
							def releaseNotes = Utilities.getReleaseNotesFromCommits(this, release.published_at)
							echo releaseNotes
							writeFile file: releaseNotesPath, text: releaseNotes
						}
						catch(any)
						{
							echo "exception:"
							echo any.getMessage()
						}
					}				
				}

				if(Utilities.checkAndAbortBuild(this))
				{
					return true
				}

				stage('Build') {		
					timestamps { 						
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
				}

				stage('Unit Tests'){
                    powershell "vc-build Test -TestsFilter \"Category=Unit|Category=CI\" -skip Restore+Compile"
                } 

                stage('Quality Gate'){
                    // Packaging.endAnalyzer(this)
                    withSonarQubeEnv('VC Sonar Server'){
                        powershell "vc-build SonarQubeEnd -SonarUrl ${env.SONAR_HOST_URL} -SonarAuthToken ${env.SONAR_AUTH_TOKEN} -skip Restore+Compile+SonarQubeStart"
                    }
                    Packaging.checkAnalyzerGate(this)
                }  
			
				def version = Utilities.getAssemblyVersion(this, webProject)
				def dockerImage
				def dockerImageLinux

				stage('Packaging') {
					timestamps { 
						powershell "vc-build Compress ${versionSuffixArg} -skip Clean+Restore+Compile+Test"
						if (env.BRANCH_NAME == 'support/2.x-dev' || env.BRANCH_NAME == 'support/2.x' || env.BRANCH_NAME == 'dev' || env.BRANCH_NAME =='master') {
							def websitePath = Utilities.getWebPublishFolder(this, websiteDir)
							dir(env.BRANCH_NAME == 'support/2.x-dev' || env.BRANCH_NAME == 'support/2.x' ? env.WORKSPACE : workspace)
							{
								dockerImage = Packaging.createDockerImage(this, zipArtifact.replaceAll('\\.','/'), websitePath, ".", dockerTag, runtimeImage)	
							}	
							if(Utilities.isNetCore(projectType) && (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME =='master'))
							{
								stash includes: 'VirtoCommerce.Storefront/**', name: 'artifact'
								node('linux')
								{
									unstash 'artifact'
									def dockerfileContent = libraryResource 'docker.core/linux/storefront/Dockerfile'
									writeFile file: "${env.WORKSPACE}/Dockerfile", text: dockerfileContent
									dockerImageLinux = docker.build("${storefrontImageName}:${dockerTagLinux}", "--build-arg SOURCE=./VirtoCommerce.Storefront .")
								}
							}	
						}
					}
				}
				if(solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2') // skip docker and publishing for NET4
				{
					if (env.BRANCH_NAME == 'support/2.x') {
						stage('Create Test Environment') {
							timestamps { 
								// Start docker environment				
								Packaging.startDockerTestEnvironment(this, dockerTag)
							}
						}
						stage('Install VC Modules'){
							timestamps{
								// install modules
								Packaging.installModules(this, 1)
								// check installed modules
								Packaging.checkInstalledModules(this)
							}
						}

						stage('Install Sample Data'){
							timestamps{
								// now create sample data
								Packaging.createSampleData(this)	
							}
						}

						stage('Theme Build and Deploy'){
							timestamps{
								def themePath = "${workspace}@tmp\\theme.zip"
								build(job: "../vc-theme-default/${themeBranch}", parameters: [string(name: 'themeResultZip', value: themePath)])
								Packaging.installTheme(this, themePath)
							}
						}

						if(!Utilities.isNetCore(projectType)) {
							stage("Swagger Schema Validation"){
								timestamps{
									def tempFolder = Utilities.getTempFolder(this)
									def schemaPath = "${tempFolder}\\swagger.json"

									Utilities.validateSwagger(this, schemaPath)
								}
							}
						}

						// stage('E2E') {
						// 	timestamps {
						// 		Utilities.runE2E(this)
						// 	}
						// }
						
						if (env.BRANCH_NAME == 'support/2.x-dev') {
							stage('Infrastructure Check and Deploy'){
								timestamps{
									Utilities.createInfrastructure(this)
								}
							}
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

				if (env.BRANCH_NAME == 'support/2.x-dev' || env.BRANCH_NAME == 'support/2.x' || env.BRANCH_NAME == 'dev' || env.BRANCH_NAME =='master') 
				{
					stage('Publish')
					{
						timestamps 
						{
							Packaging.pushDockerImage(this, dockerImage, dockerTag)
							if(Utilities.isNetCore(projectType) && dockerImageLinux != null)
							{
								node('linux')
								{
									Packaging.pushDockerImage(this, dockerImageLinux, dockerTagLinux)
								}
							}
							if (env.BRANCH_NAME == 'support/2.x' ) 
							{
								Packaging.createNugetPackages(this)
								def notes = Utilities.getReleaseNotes(this, webProject)
								Packaging.publishRelease(this, version, notes)
							}
							else if (env.BRANCH_NAME == 'master')
							{
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
								def releaseNotesFile = new File(releaseNotesPath)
								def releaseNotesArg = releaseNotesFile.exists() ? "-ReleaseNotes ${releaseNotesFile}" : ""
								def releaseResult = powershell script: "vc-build Release -GitHubUser ${orgName} -GitHubToken ${env.GITHUB_TOKEN} ${releaseNotesArg} -skip Clean+Restore+Compile+Test", returnStatus: true
								if(releaseResult == 422){
									UNSTABLE_CAUSES.add("Release already exists on github")
								} else if(releaseResult !=0 ) {
									throw new Exception("Github release error")
								}
							}

							// if((solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2') && env.BRANCH_NAME == 'dev')
							// {
							// 	Utilities.runSharedPS(this, "${deployScript}", "-SubscriptionID ${SETTINGS['subscriptionID']} -WebAppName ${SETTINGS['appName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -KuduPath ${SETTINGS['kuduPath']}")
							// 	if(projectType == 'NETCORE2'){
							// 		SETTINGS.setProject('storefront-core')
							// 		SETTINGS.setBranch('release/3.0.0')
							// 		Utilities.runSharedPS(this, "${deployScript}", "-SubscriptionID ${SETTINGS['subscriptionID']} -WebAppName ${SETTINGS['appName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -KuduPath ${SETTINGS['kuduPath']}")
							// 		SETTINGS.setProject('storefront')
							// 		SETTINGS.setBranch(env.BRANCH_NAME)
							// 	}
							// }
						}
					}
				}
			}
			catch (any) {
				currentBuild.result = 'FAILURE'
				echo any.getMessage()
				throw any //rethrow exception to prevent the build from proceeding
			}
			finally {
				Packaging.stopDockerTestEnvironment(this, dockerTag)
				Utilities.generateAllureReport(this)
				Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
				step([$class: 'LogParserPublisher',
					failBuildOnError: false,
					parsingRulesPath: env.LOG_PARSER_RULES,
					useProjectRule: false])
				bat "docker image prune --force"
				// if(currentBuild.result != 'FAILURE') {
				// 	step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
				// }
				// else {
				// 	def log = currentBuild.rawBuild.getLog(300)
				// 	def failedStageLog = Utilities.getFailedStageStr(log)
				// 	def failedStageName = Utilities.getFailedStageName(failedStageLog)
				// 	def mailBody = Utilities.getMailBody(this, failedStageName, failedStageLog)
				// 	emailext body:mailBody, subject: "${env.JOB_NAME}:${env.BUILD_NUMBER} - ${currentBuild.currentResult}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
				// }
				//step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'dev@virtoway.com', sendToIndividuals: true])
				Utilities.cleanPRFolder(this)
			}
		}
	  	//step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
		//Utilities.updateGithubCommitStatus(this, currentBuild.result, '')
	}
}