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
    	try 
      	{
      		// you can call any valid step functions from your code, just like you can from Pipeline scripts
    		echo "Building branch ${env.BRANCH_NAME}, job: ${env.JOB_NAME}"
		stage 'Checkout'
  			checkout scm
  		
  		buildSolutions()
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

def buildSolutions()
{
	def solutions = findFiles(glob: '*.sln')

	if(solutions.size() > 0)
	{
		stage 'Build'
			for(int i = 0; i < solutions.size(); i++)
			{
				def solution = solutions[i]
				bat "Nuget restore ${solution.name}"
				bat "\"${tool 'MSBuild 12.0'}\" \"${solution.name}\" /p:Configuration=Debug /p:Platform=\"Any CPU\""
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
		stage 'Running tests'
			String paths = ""
			for(int i = 0; i < testDlls.size(); i++)
			{
				def testDll = testDlls[i]
				paths += "\"$testDll.path\""
				
			}
			echo paths
			//def paths = testDlls.path.join(", ")
			//def paths = testDlls.map
			//def paths = testDlls.map { it.path }.join(" ")
			//echo "${xUnitExecutable} ${paths} -xml xUnit.Test.xml -trait `category=ci` -parallel none"
			//bat "${xUnitExecutable} ${paths} -xml xUnit.Test.xml -trait `category=ci` -parallel none"
	}
}
