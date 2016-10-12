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
		def solution = config.solution
		if(solution == null)
			 solution = 'VirtoCommerce.Platform.sln'
		try {
	    		// you can call any valid step functions from your code, just like you can from Pipeline scripts
			echo "Building branch ${env.BRANCH_NAME}"
			stage 'Checkout'
				checkout scm
			stage 'Build'
				bat "Nuget restore ${solution}"
				bat "\"${tool 'MSBuild 14.0'}\" \"${solution}\" /p:Configuration=Debug /p:Platform=\"Any CPU\""
		
		    	runTests()
			stage 'Prepare Release'
				prepareRelease(getVersion())

			bat "\"${tool 'Git'}\" log -1 --pretty=%%B > LAST_COMMIT_MESSAGE"
			git_last_commit = readFile('LAST_COMMIT_MESSAGE')

			if (env.BRANCH_NAME == 'master' && git_last_commit.contains('[publish]')) {
				stage 'Publishing'
					publishRelease(getVersion())
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

def prepareRelease(def version)
{
	def tempFolder = pwd(tmp: true)
	def wsFolder = pwd()
	def websiteDir = "$tempFolder\\_PublishedWebsites"
	def packagesDir = "$wsFolder\\artifacts"

	dir(packagesDir)
	{
		deleteDir()
	}

	// create artifacts
	bat "\"${tool 'MSBuild 12.0'}\" \"VirtoCommerce.Platform.Web\\VirtoCommerce.Platform.Web.csproj\" /nologo /verbosity:m /p:Configuration=Release /p:Platform=\"Any CPU\" /p:DebugType=none \"/p:OutputPath=$tempFolder\""
	(new AntBuilder()).zip(destfile: "${packagesDir}\\virtocommerce.platform.${version}.zip", basedir: "${websiteDir}")
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

def getVersion()
{
	echo "getting version"

	def matcher = readFile('CommonAssemblyInfo.cs') =~ /AssemblyFileVersion\(\"(\d+\.\d+\.\d+)/

  	if (matcher) {
    	echo "Building version ${matcher[0][1]}"
  	}

	def version = matcher[0][1]
	echo "found version ${version}"
	return version
}

def runTests()
{
	def xUnit = env.XUnit
	def xUnitExecutable = "${xUnit}\\xunit.console.exe"
	
	def testDlls = findFiles(glob: '**\\bin\\Debug\\*Test.dll')
	if(testDlls.size() > 0)
	{
		stage 'Running tests'
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
