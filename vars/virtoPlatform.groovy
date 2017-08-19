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

			var tests = Utilities.getTestDlls(this)
			if(tests.size() > 0)
			{
				stage('Tests') {
					Packaging.runUnitTests(this, tests)
				}
			}
			stage('Prepare Release') {
				//def packaging = new Packaging(this)
				Packaging.createReleaseArtifact(this, Utilities.getAssemblyVersion(this), webProject, zipArtifact, websiteDir)
			}

			bat "\"${tool 'Git'}\" log -1 --pretty=%%B > LAST_COMMIT_MESSAGE"
			git_last_commit = readFile('LAST_COMMIT_MESSAGE')

			if (env.BRANCH_NAME == 'master' && git_last_commit.contains('[publish]')) {
				stage('Publishing'){
					publishRelease(Utilities.getAssemblyVersion(this))
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

def publishRelease(def version)
{
	tokens = "${env.JOB_NAME}".tokenize('/')
	def REPO_NAME = tokens[1]
	def REPO_ORG = "VirtoCommerce"

	def tempFolder = pwd(tmp: true)
	def wsFolder = pwd()
	def packagesDir = "$wsFolder\\artifacts"

	dir(packagesDir)
	{
		def artifacts = findFiles(glob: '*.zip')
		if (artifacts.size() > 0) {
			for (int i = 0; i < artifacts.size(); i++)
			{
				def artifact = artifacts[i]
				bat "${env.Utils}\\github-release release --user $REPO_ORG --repo $REPO_NAME --tag v${version}"
				bat "${env.Utils}\\github-release upload --user $REPO_ORG --repo $REPO_NAME --tag v${version} --name \"${artifact}\" --file \"${artifact}\""
				echo "uploaded to https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
				return "https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
			}
		}
	}
}

def runTests()
{
	def xUnit = env.XUnit
	def xUnitExecutable = "${xUnit}\\xunit.console.exe"
	
	def testDlls = findFiles(glob: '**\\bin\\Debug\\*Test.dll')
	if(testDlls.size() > 0)
	{
		stage('Running tests'){
			String paths = ""
			for(int i = 0; i < testDlls.size(); i++)
			{
				def testDll = testDlls[i]
				paths += "\"$testDll.path\" "
			}
			
			bat "${xUnitExecutable} ${paths} -xml xUnit.Test.xml -trait \"category=ci\" -parallel none"
			step([$class: 'XUnitPublisher', testTimeMargin: '3000', thresholdMode: 1, thresholds: [[$class: 'FailedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: ''], [$class: 'SkippedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']], tools: [[$class: 'XUnitDotNetTestType', deleteOutputFiles: true, failIfNotNew: false, pattern: '*.xml', skipNoTestFiles: true, stopProcessingIfError: false]]])
		}
	}
}