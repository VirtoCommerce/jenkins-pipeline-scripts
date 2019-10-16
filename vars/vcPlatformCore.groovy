import jobs.scripts.*

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

    node {
        def escapedBranch = env.BRANCH_NAME.replaceAll('/', '_')
        def repoName = Utilities.getRepoName(this)
        def workspace = "D:\\Buildsv3\\${repoName}\\${escapedBranch}"
        dir(workspace){
            def SETTINGS
            def settingsFileContent
            configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
                settingsFileContent = readFile(SETTINGS_FILE)
            }
            SETTINGS = new Settings(settingsFileContent)
            SETTINGS.setRegion('platform-core')
            SETTINGS.setEnvironment(env.BRANCH_NAME)
            stage('Checkout'){
                deleteDir()
                checkout scm
            }

            stage('Build'){
                bat "dotnet build-server shutdown"
                bat "vc-build Compress"
                //bat "vc-build Pack"
            }

            stage('Unit Tests'){
                bat "vc-build Test"
            }   

            stage('Deploy'){
                // $ZipFile,
                // $WebAppName,
                // $ResourceGroupName,
                // $SubscriptionID,
                // $DestContentPath = ""
                def artifacts = findFiles(glob: "artifacts/*.zip")
                def artifactPath = artifacts[0].path
                Utilities.runSharedPS(this, "v3\\DeployTo-Azure.ps1", "-ZipFile \"${artifactPath}\" -WebAppName ${SETTINGS['webAppName']} -ResourceGroupName ${SETTINGS['resourceGroupName']} -SubscriptionID ${SETTINGS['subscriptionID']} -DestContentPath \"platform\"")
            }
        }
    }
}