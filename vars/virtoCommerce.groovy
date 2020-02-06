#!groovy
import groovy.json.*
import groovy.util.*
import jobs.scripts.*

	def call(body){

	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

	node
	{
		properties([disableConcurrentBuilds()])
	    def deployScript = 'VC-2AzureDEV.ps1'
		def dockerTag = "${env.BRANCH_NAME}-branch"
		def buildOrder = Utilities.getNextBuildOrder(this)
		projectType = config.projectType

		def SETTINGS
		def settingsFileContent
		configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
			settingsFileContent = readFile(SETTINGS_FILE)
		}
		SETTINGS = new Settings(settingsFileContent)
		SETTINGS.setRegion('virtocommerce')
		SETTINGS.setEnvironment(env.BRANCH_NAME)

		if (env.BRANCH_NAME == 'deploy')
		{
			dockerTag = "latest"
		}

		try
		{
			//Utilities.notifyBuildStatus(this, "started")
			stage('Checkout')
			{
				timestamps
				{
					deleteDir()
					checkout scm
				}
			}

			if(Utilities.checkAndAbortBuild(this))
			{
				return true
			}

			stage('Copy to DEV-VC')
			{
				timestamps
				{
					if(env.BRANCH_NAME == 'dev')
					{
						def stagingName = "dev"
						def storeName = "cms-content"
						def azureBlobName = SETTINGS['azureBlobName']
						def azureBlobKey = SETTINGS['azureBlobKey']
						def webAppName = SETTINGS['webAppName']
						def resourceGroupName = SETTINGS['resourceGroupName']
						def subscriptionID = SETTINGS['subscriptionID']
						def blobToken = SETTINGS['tokenSas']
						withEnv(["AzureBlobToken=${blobToken}"]){
							Utilities.runSharedPS(this, "${deployScript}", "-StagingName ${stagingName} -StoreName ${storeName} -AzureBlobName ${azureBlobName} -AzureBlobKey ${azureBlobKey} -WebAppName ${webAppName} -ResourceGroupName ${resourceGroupName} -SubscriptionID ${subscriptionID}")
						}
					}
				}
			}

			stage('Cleanup')
			{
				timestamps
				{
					Packaging.cleanSolutions(this)
				}
			}
		}
		catch (any)
		{
			currentBuild.result = 'FAILURE'
			Utilities.notifyBuildStatus(this, currentBuild.result)
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally
		{
			Packaging.stopDockerTestEnvironment(this, dockerTag)
			Utilities.generateAllureReport(this)
			step([$class: 'LogParserPublisher',
				  failBuildOnError: false,
				  parsingRulesPath: env.LOG_PARSER_RULES,
				  useProjectRule: false])
			if(currentBuild.result != 'FAILURE')
			{
				//step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
			}
			else
			{
				def log = currentBuild.rawBuild.getLog(300)
				def failedStageLog = Utilities.getFailedStageStr(log)
				def failedStageName = Utilities.getFailedStageName(failedStageLog)
				def mailBody = Utilities.getMailBody(this, failedStageName, failedStageLog)
				emailext body:mailBody, subject: "${env.JOB_NAME}:${env.BUILD_NUMBER} - ${currentBuild.currentResult}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
			}
		}

		//step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
	}
}
