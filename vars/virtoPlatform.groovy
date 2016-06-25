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
				bat "\"${tool 'MSBuild 12.0'}\" \"${solution}\" /p:Configuration=Debug /p:Platform=\"Any CPU\""
		
		    	runTests()
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
	    		step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'sasha@virtoway.com', sendToIndividuals: true])
		}
	
	  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
	}
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
			
			bat "${xUnitExecutable} ${paths} -xml xUnit.Test.xml -trait 'category=ci' -parallel none"
			step([$class: 'XUnitPublisher', testTimeMargin: '3000', thresholdMode: 1, thresholds: [[$class: 'FailedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: ''], [$class: 'SkippedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']], tools: [[$class: 'XUnitDotNetTestType', deleteOutputFiles: true, failIfNotNew: false, pattern: '*.xml', skipNoTestFiles: true, stopProcessingIfError: false]]])
	}
}
