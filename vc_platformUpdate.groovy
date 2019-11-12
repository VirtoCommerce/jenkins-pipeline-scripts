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

        stage('E2E'){
            timestamps{
                timeout(20){ // minutes by default
                    try{
                        Utilities.runE2E(this)
                        def e2eStatus = "E2E Success"
                    }
                    catch(any){
                        e2eStatus = "E2E Failed"
                    }
                    finally{
                        //Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "${e2eStatus}")
                        msg = "${e2eStatus}."
                        if(!(e2eStatus == 'E2E Success')) {
                            input(message: msg, submitter: env.APPROVERS)
                        }
                    }
                }
            }
        }

        stage('SwapSlot'){
            timestamps{
                powershell "${psfolder}\\SwapSlot.ps1"
            }
        }
        
        stage('Cleanup'){
            timestamps{
                Packaging.cleanSolutions(this)
			}
		}
    }
}
