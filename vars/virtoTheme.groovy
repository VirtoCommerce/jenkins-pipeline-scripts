#!groovy

// module script
def call(body) {

	
	def globalLib = library('test-shared-lib').com.test
	def Utilities = globalLib.Utilities
	def Packaging = globalLib.Packaging

	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

	
	
    
	node {
		properties([disableConcurrentBuilds()])
	    def storeName = config.sampleStore
		projectType = config.projectType
		if(projectType==null){
			projectType = 'Theme'
		}

		def SETTINGS
		def settingsFileContent
		configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
			settingsFileContent = readFile(SETTINGS_FILE)
		}
		SETTINGS = globalLib.Settings.new(settingsFileContent)
		SETTINGS.setBranch(env.BRANCH_NAME)
		SETTINGS.setProject('theme')

		try {
			echo "Building branch ${env.BRANCH_NAME}"
			echo "_Settings: ${SETTINGS._settings}"
			Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')

			stage('Checkout') {
				timestamps { 
					deleteDir()
					checkout scm
				}
			}

			stage('Code Analysis'){
				timestamps{
					Packaging.startSonarJS(this)
				}
			}

			stage('Build') {
				timestamps { 
					Packaging.runGulpBuild(this)
				}
			}

			stage('Quality Gate'){
                timestamps{
                    Packaging.checkAnalyzerGate(this)
                }
            }

			def artifacts = findFiles(glob: 'artifacts/*.zip')
			if(params.themeResultZip != null){
                for(artifact in artifacts){
                    bat "copy /Y \"${artifact.path}\" \"${params.themeResultZip}\""
                }
            }
			
			def version = Utilities.getPackageVersion(this)

			if(params.themeResultZip == null)
			{
				stage('Publish') {
					timestamps {
						Packaging.saveArtifact(this, 'vc', 'theme', config.sampleStore, artifacts[0].path)
						if (Packaging.getShouldPublish(this)) {
							Packaging.publishRelease(this, version, "")
						}
						if (env.BRANCH_NAME == 'dev') {
							def stagingName = Utilities.getStagingNameFromBranchName(this)
							echo "${SETTINGS.getProjects()}"
							Utilities.runSharedPS(this, "VC-Theme2Azure.ps1", "-StagingName ${stagingName} -StoreName ${storeName} -AzureBlobName ${SETTINGS['azureBlobName']} -AzureBlobKey ${SETTINGS['azureBlobKey']}")
							SETTINGS.setProject('theme-core')
							SETTINGS.setBranch('release/3.0.0')
							Utilities.runSharedPS(this, "VC-Theme2Azure.ps1", "-StagingName ${stagingName} -StoreName ${storeName} -AzureBlobName ${SETTINGS['azureBlobName']} -AzureBlobKey ${SETTINGS['azureBlobKey']}")
							SETTINGS.setProject('theme')
							SETTINGS.setBranch(env.BRANCH_NAME)
						}
					}
				}
			}
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
			Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
			step([$class: 'LogParserPublisher',
				  failBuildOnError: false,
				  parsingRulesPath: env.LOG_PARSER_RULES,
				  useProjectRule: false])
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
			Utilities.cleanPRFolder(this)
		}
	
	  	//step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
		//Utilities.updateGithubCommitStatus(this, currentBuild.result, '')
	}
}
