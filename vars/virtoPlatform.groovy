#!groovy
import jobs.scripts.*

// module script
def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()
    
	node
	{
		// configuration parameters
		def solution = config.solution
		def webProject = 'VirtoCommerce.Platform.Web\\VirtoCommerce.Platform.Web.csproj'
		def zipArtifact = 'VirtoCommerce.Platform'
		def websiteDir = 'VirtoCommerce.Platform.Web'
		def deployScript = 'VC-Platform2AzureDev.ps1'
		if (env.BRANCH_NAME == 'master') {
			deployScript = 'VC-Platform2AzureQA.ps1'
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
			deployScript = 'VC-Storefront2AzureDev.ps1'
			if (env.BRANCH_NAME == 'master') {
				deployScript = 'VC-Storefront2AzureQA.ps1'
			}
		}
		
		try {
			echo "Building branch ${env.BRANCH_NAME}"

			stage('Checkout') {
				checkout scm
			}
			stage('Build') {
				Packaging.runBuild(this, solution)
			}

			def tests = Utilities.getTestDlls(this)
			if(tests.size() > 0)
			{
				stage('Tests') {
					Packaging.runUnitTests(this, tests)
				}
			}
			def version = Utilities.getAssemblyVersion(this)
			stage('Prepare Release') {
				//def packaging = new Packaging(this)
				Packaging.createReleaseArtifact(this, version, webProject, zipArtifact, websiteDir)
			}

			if (Packaging.getShouldPublish(this)) {
				stage('Publishing'){
					Packaging.publishRelease(this,version)
				}
			}
			
			if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
				stage('Deploy Azure'){
					Utilities.runSharedPS(this, deployScript)
				}
			}		
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
	    		step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'dev@virtoway.com', sendToIndividuals: true])
		}
	
	  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
	}
}