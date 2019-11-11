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
         stage('Storefront Update'){
            timestamps {
                SETTINGS.setEnvironment('dev_storefront')
                withEnv(["AzureSubscriptionIDProd=${SETTINGS['subscriptionID']}", "AzureResourceGroupNameProd=${SETTINGS['resourceGroupName']}", "AzureWebAppNameProd=${SETTINGS['appName']}"]){
                    powershell "${psfolder}\\StorefrontUpdate.ps1"
                }
            }
        }
        
        stage('E2E'){
            timestamps{
                Utilities.runE2E(this)
            }
        }
        
        stage('Cleanup') {
            timestamps {
                Packaging.cleanSolutions(this)
			}
		}
    }
}
