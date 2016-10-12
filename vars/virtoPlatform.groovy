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
				//processManifests(true) // publish artifacts to github releases
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
	(new AntBuilder()).zip(destfile: "${packagesDir}\\virtocommerce.platform.${version}.zip", basedir: "${websiteDir}\\VirtoCommerce.Platform.Web")
}

def getVersion()
{
	def assemblyInfo = readFile('CommonAssemblyInfo.cs')
	// extract version string from assembly info file
	return assemblyInfo.find(/AssemblyFileVersion\(\"(\d+\.\d+\.\d+)/) { fullMatch, version -> return version}
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
