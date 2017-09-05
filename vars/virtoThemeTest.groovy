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
	    def deployScript = 'VC-Theme2AzureDev.ps1'
		if (env.BRANCH_NAME == 'master') {
			deployScript = 'VC-Theme2AzureQA.ps1'
		}
		try {
			echo "Building branch ${env.BRANCH_NAME}"
			Utilities.notifyBuildStatus(this, "Started")

			stage('Checkout') {
				timestamps { 
					checkout scm
				}				
			}

			if(Utilities.checkAndAbortBuild(this))
			{
				return true
			}

			stage('Build') {
				timestamps { 
					Packaging.runGulpBuild(this)
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