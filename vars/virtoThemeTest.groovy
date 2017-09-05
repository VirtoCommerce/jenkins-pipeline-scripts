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
		try {
			echo "Building branch ${env.BRANCH_NAME}"
			Utilities.notifyBuildStatus(this, "Started")

			stage('Checkout') {
				timestamps { 
					checkout scm
				}
				// clean folder for a release
				// we need keep first checkout to get latest info from GitHub before checking
				// we can't return from try block here, it's reason why Utilities.checkAndAbortBuild may be called twice 
				if(Packaging.getShouldPublish(this) && !Utilities.checkAndAbortBuild(this)) {
					deleteDir()
					checkout scm
				}
			}

			if(Utilities.checkAndAbortBuild(this))
			{
				return true
			}

			stage('Build + Analyze') {
				timestamps { 
					Packaging.startAnalyzer(this)
					Packaging.runGulpBuild(this)
				}
			}

			if (Packaging.getShouldStage(this)) {
				stage('Stage') {
					timestamps {
					    def stagingName = Utilities.getStagingNameFromBranchName(this)
						Utilities.runSharedPS(this, "resources\\azure\\VC-Theme2Azure.ps1", "-StagingName=${stagingName}, -StoreName=${storeName}")
					}
				}			
			}

			if (Packaging.getShouldPublish(this)) {
				stage('Publish') {
					timestamps { 
						Packaging.publishThemePackage(this)
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
			step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
		}
	
	  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
		Utilities.notifyBuildStatus(this, currentBuild.result)
	}
}