import jobs.scripts.*

node {
    def SETTINGS
    
    stage('Init'){
        deleteDir()
        checkout scm
        def settingsFileContent
		configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
			settingsFileContent = readFile(SETTINGS_FILE)
		}
		SETTINGS = new Settings(settingsFileContent)
		SETTINGS.setRegion('virtocommerce')
    }
    psfolder = "${env.WORKSPACE}\\resources\\virtocommerce"
    dir(psfolder){
        stage('Platform Update'){
            timestamps {
                SETTINGS.setEnvironment('platform')
                withEnv(["AzureSubscriptionIDProd=${SETTINGS['subscriptionID']}", "AzureResourceGroupNameProd=${SETTINGS['resourceGroupName']}", "AzureWebAppAdminNameProd=${SETTINGS['appName']}"]){
                    powershell "${psfolder}\\PlatformUpdate.ps1"
                }
            }
        }

        stage('SwapSlot'){
            timestamps{
                powershell "${psfolder}\\SwapSlot.ps1"
            }
        }
    }
}
