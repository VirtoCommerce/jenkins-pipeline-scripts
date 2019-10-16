import jobs.scripts.*

def call(body) {
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

    node {
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
            bat "vc-build Compress"
        }

        stage('Unit Tests'){
            bat "vc-build Test"
        }   

        stage('Deploy'){
        }
    }
}