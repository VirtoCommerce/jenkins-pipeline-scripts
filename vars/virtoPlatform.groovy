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
		def hmacAppId = env.HMAC_APP_ID
		def hmacSecret = env.HMAC_SECRET
		def solution = config.solution
		projectType = config.projectType
		
		def webProject = 'VirtoCommerce.Platform.Web\\VirtoCommerce.Platform.Web.csproj'
		def zipArtifact = 'VirtoCommerce.Platform'
		def websiteDir = 'VirtoCommerce.Platform.Web'
		def deployScript = 'VC-Platform2AzureDev.ps1'
		def dockerTag = env.BRANCH_NAME
		def buildOrder = Utilities.getNextBuildOrder(this)
		if (env.BRANCH_NAME == 'master') {
			deployScript = 'VC-Platform2AzureQA.ps1'
			dockerTag = "latest"
		}
		
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
			deployScript = 'VC-Storefront2AzureDev.ps1'
			if (env.BRANCH_NAME == 'master') {
				deployScript = 'VC-Storefront2AzureQA.ps1'
			}
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

			stage('Build + Analyze') {		
				timestamps { 					
					// clean folder for a release
					if (Packaging.getShouldPublish(this)) {
						deleteDir()
						checkout scm
					}		
					
					Packaging.startAnalyzer(this)
					Packaging.runBuild(this, solution)
				}
			}
		
			def version = Utilities.getAssemblyVersion(this, webProject)
			def dockerImage

			stage('Package') {
				timestamps { 
					Packaging.createReleaseArtifact(this, version, webProject, zipArtifact, websiteDir)
					if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
						def websitePath = Utilities.getWebPublishFolder(this, websiteDir)
						dockerImage = Packaging.createDockerImage(this, zipArtifact.replaceAll('\\.','/'), websitePath, ".", dockerTag)			
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

			stage('Submit Analysis') {
				timestamps { 
					Packaging.endAnalyzer(this)
				}
			}			

			// No need to occupy a node
			stage("Quality Gate"){
				Packaging.checkAnalyzerGate(this)
			}

			if(!Utilities.isNetCore(projectType)) {
				stage("Swagger schema validation"){
					timestamps{
						def apiPaths = Utilities.getWebApiDll(this)
						def tempFolder = Utilities.getTempFolder(this)
						def schemaPath = "${tempFolder}\\swagger.json"

						apiPaths = "\"${env.WORKSPACE}\\VirtoCommerce.Platform.Web\\bin\\VirtoCommerce.Platform.Web.dll\"" //temporarily

						Utilities.validateSwagger(this, apiPaths, schemaPath)
					}
				}
			}
			

			if(solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2') // skip docker and publishing for NET4
			{
				if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
					stage('Docker Sample') {
						timestamps { 
							// Start docker environment				
							Packaging.startDockerTestEnvironment(this, dockerTag)
							
							// install modules
							Packaging.installModules(this)	

							// now create sample data
							Packaging.createSampleData(this)					
						}
					}
				}			
			}

			if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
				stage('Publish'){
					timestamps { 
						if(solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2')
						{
							Packaging.pushDockerImage(this, dockerImage, dockerTag)
						}
						if (Packaging.getShouldPublish(this)) {
							def notes = Utilities.getReleaseNotes(this, webProject)
							Packaging.publishRelease(this, version, notes)
						}

						if(solution == 'VirtoCommerce.Platform.sln' || projectType == 'NETCORE2')
						{
							Utilities.runSharedPS(this, "resources\\azure\\${deployScript}")
						}
					}
				}
			}


/*
			stage('Cleanup') {
				timestamps { 
					Packaging.cleanBuild(this, solution)
				}
			}	
*/		
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			Utilities.notifyBuildStatus(this, currentBuild.result)
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
			Packaging.stopDockerTestEnvironment(this, dockerTag)
			if(currentBuild.result != 'FAILURE') {
				step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
			}
			else {
				def log = currentBuild.rawBuild.getLog(100)
				def failedStageLog = Utilities.getFailedStageStr(log)
				def failedStageName = Utilities.getFailedStageName(failedStageLog)
				def mailBody = "Failed Stage: ${failedStageName}\n${env.JOB_URL}\n\n\n${failedStageLog}"
				step([$class: 'Mailer', notifyEveryUnstableBuild: true, body: mailBody, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
			}
	    	//step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'dev@virtoway.com', sendToIndividuals: true])
		}
	
	  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
		Utilities.notifyBuildStatus(this, currentBuild.result)
	}
}
