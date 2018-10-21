#!groovy
import jobs.scripts.*

// module script
def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
    
	node {
	    def storeName = config.sampleStore
		projectType = config.projectType
		if(projectType==null){
			projectType = 'Theme'
		}
		try {
			echo "Building branch ${env.BRANCH_NAME}"
			Utilities.notifyBuildStatus(this, "Started")

			def checkAndAbortBuild = false
			stage('Checkout') {
				timestamps { 
					checkout scm
				}
				checkAndAbortBuild = Utilities.checkAndAbortBuild(this)
				// clean folder for a release
				if(Packaging.getShouldPublish(this) && !checkAndAbortBuild) {
					deleteDir()
					checkout scm
				}
			}

			if(checkAndAbortBuild) {
				return true
			}

			stage('Build + Analyze') {
				timestamps { 
					Packaging.startAnalyzer(this)
					Packaging.runGulpBuild(this)
				}
			}
			
			def version = Utilities.getPackageVersion(this)

			if (Packaging.getShouldStage(this)) {
				stage('Stage') {
					timestamps {
					    def stagingName = Utilities.getStagingNameFromBranchName(this)
						Utilities.runSharedPS(this, "resources\\azure\\VC-Theme2Azure.ps1", /-StagingName "${stagingName}" -StoreName "${storeName}"/)
					}
				}			
			}

			if (Packaging.getShouldPublish(this)) {
				stage('Publish') {
					timestamps { 
						Packaging.publishRelease(this, version, "")
					}
				}
			}
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			Utilities.notifyBuildStatus(this, currentBuild.result)
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
			if(currentBuild.result != 'FAILURE') {
				step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
			}
			else {
				def log = currentBuild.rawBuild.getLog(300)
				def failedStageLog = Utilities.getFailedStageStr(log)
				def failedStageName = Utilities.getFailedStageName(failedStageLog)
				def mailBody = Utilities.getMailBody(this, failedStageName, failedStageLog)
				emailext body:mailBody, subject: "${env.JOB_NAME}:${env.BUILD_NUMBER} - ${currentBuild.currentResult}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
			}
		}
	
	  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
		Utilities.notifyBuildStatus(this, currentBuild.result)
	}
}
