import jobs.scripts.*

node
{
    def SETTINGS

    stage('Init')
    {
        deleteDir()
        checkout scm
        def settingsFileContent
		configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')])
        {
			settingsFileContent = readFile(SETTINGS_FILE)
		}
		SETTINGS = new Settings(settingsFileContent)
		SETTINGS.setRegion('virtocommerce')
    }

    def psfolder = "${env.WORKSPACE}\\resources\\virtocommerce"
    dir("${psfolder}")
    {
         stage('Content backup to staging (QA)')
         {
            timestamps
            {
                SETTINGS.setEnvironment('backUp')
                withEnv([
                        "SubscriptionID=${SETTINGS['subscriptionID']}",
                        "ResourceGroupName=${SETTINGS['resourceGroupName']}",
                        "WebAppName=${SETTINGS['appName']}",
                        "sourceStorage=${SETTINGS['sourceStorage']}",
                        "destStorage=${SETTINGS['destStorage']}",
                        "sourceSAS=${SETTINGS['sourceSAS']}",
                        "destSAS=${SETTINGS['destSAS']}"
                        ]) {
                    powershell "${psfolder}\\vc_backUp.ps1"
                }
            }
        }

        stage('Cleanup')
        {
            timestamps
            {
                Packaging.cleanSolutions(this)
			}
		}
    }
}
