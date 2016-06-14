// module script
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // you can call any valid step functions from your code, just like you can from Pipeline scripts
    echo "Building plugin ${config.name}"

    def solution = "${config.solution}"
    
	env.WORKSPACE = pwd()
	stage 'Checkout'
		checkout([$class: 'GitSCM', extensions: [[$class: 'PathRestriction', excludedRegions: 'CommonAssemblyInfo\\.cs', includedRegions: '']]])

		stage 'Build'
			bat "Nuget restore ${solution}"
			bat "\"${tool 'MSBuild 12.0'}\" "${solution}" /p:Configuration=Debug /p:Platform=\"Any CPU\""

	if (env.BRANCH_NAME == 'master') {
				
		stage 'Publish'
		    	updateVersion(env.WORKSPACE)
	   		bat 'Nuget\\build.bat'
	} 
	
	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
}

def updateVersion(workspace)
{
    bat "powershell.exe -File \"${env.JENKINS_HOME}\\workflow-libs\\vars\\version.ps1\" -solutiondir \"${workspace}\""
    
    bat "\"${tool 'Git'}\" config user.email \"ci@virtocommerce.com\""
    bat "\"${tool 'Git'}\" config user.name \"Virto Jenkins\""
    bat "\"${tool 'Git'}\" commit -am \"Updated version number ${env.BUILD_TAG}\""
    bat "\"${tool 'Git'}\" push origin HEAD:master -f"
}
