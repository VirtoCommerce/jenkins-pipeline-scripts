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
		def dockerTag = env.BRANCH_NAME
		if (env.BRANCH_NAME == 'master') {
			deployScript = 'VC-Platform2AzureQA.ps1'
			dockerTag = "latest"
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

			stage('Build') {
				timestamps { 
					checkout scm
					Packaging.runBuild(this, solution)
				}
			}
		
			def version = Utilities.getAssemblyVersion(this)
			def dockerImage

			stage('Package') {
				timestamps { 
					//def packaging = new Packaging(this)
					Packaging.createReleaseArtifact(this, version, webProject, zipArtifact, websiteDir)
					if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
						def websitePath = Utilities.getWebPublishFolder(this, websiteDir)
						dockerImage = Packaging.createDockerImage(this, zipArtifact.replaceAll('\\.','/'), websitePath, ".", dockerTag)
						//Packaging.startDockerTestEnvironment(this, dockerTag)
						//Packaging.stopDockerTestEnvironment(this, dockerTag)
					}
				}
			}

			def tests = Utilities.getTestDlls(this)
			if(tests.size() > 0)
			{
				stage('Tests') {
					timestamps { 
						Packaging.runUnitTests(this, tests)
					}
				}
			}			

			if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
				stage('Publish'){
					timestamps { 
						Packaging.pushDockerImage(this, dockerImage, dockerTag)

						if (Packaging.getShouldPublish(this)) {
							Packaging.publishRelease(this,version)
						}

						Utilities.runSharedPS(this, "resources\\azure\\${deployScript}")
					}
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