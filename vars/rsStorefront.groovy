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
		def solution = 'VirtoCommerce.Storefront'
		projectType = "NETCORE2"

		
		def globalLib = library('global-shared-lib').com.test
		def Utilities = globalLib.Utilities
		def Packaging = globalLib.Packaging
		def Docker = globalLib.Docker
		
		def webProject = 'VirtoCommerce.Platform.Web\\VirtoCommerce.Platform.Web.csproj'
		def zipArtifact = 'VirtoCommerce.Platform'
		def websiteDir = 'VirtoCommerce.Platform.Web'
		def deployScript = 'VC-WebApp2Azure.ps1'
		def dockerTag = "${env.BRANCH_NAME}-branch"
		def buildOrder = Utilities.getNextBuildOrder(this)
		if (env.BRANCH_NAME == 'master') {
			dockerTag = "latest"
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

		def escapedBranch = env.BRANCH_NAME.replaceAll('/', '_')
        def repoName = Utilities.getRepoName(this)
        def workspace = "D:\\Buildsv3\\${repoName}\\${escapedBranch}"
        dir(workspace){
			try {
				Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')

				stage('Checkout') {
					timestamps { 
						if (Packaging.getShouldPublish(this)) {
							deleteDir()
						}
						checkout scm
					}				
				}

				if(Utilities.checkAndAbortBuild(this))
				{
					return true
				}

				stage('Build') {		
					timestamps { 						
						// Packaging.startAnalyzer(this)
						Packaging.runBuild(this, solution)
					}
				}
			
				def version = Utilities.getAssemblyVersion(this, webProject)
				def dockerImage

				stage('Packaging') {
					timestamps { 
						Packaging.createReleaseArtifact(this, version, webProject, zipArtifact, websiteDir)
						// if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
						// 	def websitePath = Utilities.getWebPublishFolder(this, websiteDir)
						// 	dockerImage = Packaging.createDockerImage(this, zipArtifact.replaceAll('\\.','/'), websitePath, ".", dockerTag)			
						// }
					}
				}

				def tests = Utilities.getTestDlls(this)
				if(tests.size() > 0)
				{
					stage('Unit Tests') {
						timestamps { 
							Packaging.runUnitTests(this, tests)
						}
					}
				}		

				// stage('Code Analysis') {
				// 	timestamps { 
				// 		Packaging.endAnalyzer(this)
				// 		Packaging.checkAnalyzerGate(this)
				// 	}
				// }

				// if(solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2') // skip docker and publishing for NET4
				// {
				// 	if (env.BRANCH_NAME == 'master' || env.BRANCH_NAME == 'release') {
				// 		stage('Create Test Environment') {
				// 			timestamps { 
				// 				// Start docker environment				
				// 				Packaging.startDockerTestEnvironment(this, dockerTag)
				// 			}
				// 		}
				// 		stage('Install VC Modules'){
				// 			timestamps{
				// 				// install modules
				// 				Packaging.installModules(this, 1)
				// 				// check installed modules
				// 				Packaging.checkInstalledModules(this)
				// 			}
				// 		}

				// 		stage('Install Sample Data'){
				// 			timestamps{
				// 				// now create sample data
				// 				Packaging.createSampleData(this)	
				// 			}
				// 		}

				// 		stage('Theme Build and Deploy'){
				// 			timestamps{
				// 				def themePath = "${env.WORKSPACE}@tmp\\theme.zip"
				// 				build(job: "../vc-theme-default/${env.BRANCH_NAME}", parameters: [string(name: 'themeResultZip', value: themePath)])
				// 				Packaging.installTheme(this, themePath)
				// 			}
				// 		}

				// 		// if(!Utilities.isNetCore(projectType)) {
				// 		// 	stage("Swagger Schema Validation"){
				// 		// 		timestamps{
				// 		// 			def tempFolder = Utilities.getTempFolder(this)
				// 		// 			def schemaPath = "${tempFolder}\\swagger.json"

				// 		// 			Utilities.validateSwagger(this, schemaPath)
				// 		// 		}
				// 		// 	}
				// 		// }

				// 		// stage('E2E') {
				// 		// 	timestamps {
				// 		// 		Utilities.runE2E(this)
				// 		// 	}
				// 		// }
						
				// 		// if (env.BRANCH_NAME == 'dev') {
				// 		// 	stage('Infrastructure Check and Deploy'){
				// 		// 		timestamps{
				// 		// 			Utilities.createInfrastructure(this)
				// 		// 		}
				// 		// 	}
				// 		// }
				// 	}
				// }

				if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
					stage('Publish'){
						timestamps { 
							def packagesDir = Utilities.getArtifactFolder(this)
							def artifacts
							dir(packagesDir)
							{ 
								artifacts = findFiles(glob: '*.zip')
							}
							Packaging.saveArtifact(this, 'rs', Utilities.getProjectType(this), '', "artifacts/${artifacts[0].path}")

							// if(solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2')
							// {
							// 	Packaging.pushDockerImage(this, dockerImage, dockerTag)
							// }
							// if (Packaging.getShouldPublish(this)) {
							// 	Packaging.createNugetPackages(this)
							// 	def notes = Utilities.getReleaseNotes(this, webProject)
							// 	Packaging.publishRelease(this, version, notes)
							// }

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
				//Packaging.stopDockerTestEnvironment(this, dockerTag)
				//Utilities.generateAllureReport(this)
				//Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
				step([$class: 'LogParserPublisher',
					failBuildOnError: false,
					parsingRulesPath: env.LOG_PARSER_RULES,
					useProjectRule: false])
				//bat "docker image prune --force"
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