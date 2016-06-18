#!groovy
def solution = 'VirtoCommerce.Platform.sln'
node
{
	try 
    {
    	// you can call any valid step functions from your code, just like you can from Pipeline scripts
		echo "Building branch ${env.BRANCH_NAME}"
		stage 'Checkout'
			checkout scm
		stage 'Build'
			bat "Nuget restore ${solution}"
			bat "\"${tool 'MSBuild 12.0'}\" \"${solution}\" /p:Configuration=Debug /p:Platform=\"Any CPU\""
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
