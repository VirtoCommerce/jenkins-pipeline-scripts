#!groovy

// module script
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // you can call any valid step functions from your code, just like you can from Pipeline scripts
    echo "Building ${config.name} with branch ${env.BRANCH_NAME}, job: ${env.JOB_NAME}"

  	env.WORKSPACE = pwd()
  	stage 'Checkout'
  		checkout scm
	
  	buildSolutions()
  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
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
