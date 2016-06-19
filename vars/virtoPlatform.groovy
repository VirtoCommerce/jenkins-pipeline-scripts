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
		def solution = 'VirtoCommerce.Platform.sln'
		try 
		    {
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
	    		step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'dev@virtoway.com', sendToIndividuals: true])
		}
	
	  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
	}
}

def runTests()
{
	def xUnitExecutable = "packages\\xunit.runner.console.2.1.0\\tools\\xunit.console.exe"
	stage 'Running tests'
		def testDlls = findFiles(glob: '**\bin\Debug\*Test.dll')
		if(testDlls.size() > 0)
		{
			def paths = testDlls.map { it.path }.join(" ")
			bat "${xUnitExecutable} ${paths} -xml xUnit.Test.xml -trait `category=ci` -parallel none"

/*
			for(int i = 0; i < testDlls.size(); i++)
			{
				def testDll = testDlls[i]
				bat "${xUnitExecutable}" $cFiles -xml xUnit.Test.xml -trait `"category=ci`" -parallel none"
				bat "Nuget restore ${solution.name}"
				bat "\"${tool 'MSBuild 12.0'}\" \"${solution.name}\" /p:Configuration=Debug /p:Platform=\"Any CPU\""
			}
*/
		}
}
