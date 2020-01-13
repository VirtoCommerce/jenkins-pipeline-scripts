import jobs.scripts.*

node {
    def SETTINGS
    
    stage('Init'){
        deleteDir()
        checkout scm
        def envChoices
        def settingsFileContent
		configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
			settingsFileContent = readFile(SETTINGS_FILE)
		}
		SETTINGS = new Settings(settingsFileContent)
		SETTINGS.setRegion('virtocommerce')
    }

    psfolder = "${env.WORKSPACE}\\resources\\virtocommerce"
    dir(psfolder){

        stage('User Input'){
            timeout(time: 30, unit: 'MINUTES'){
                envChoices = input(message: "Choose environment to update", parameters: [choice(name: 'Environments', choices:"Dev\nProduction")])
                if(envChoices == 'Production'){
                    envChoices = "staging"
                }
                else if(envChoices == 'Dev'){
                    envChoices = ""
                }
            }
        }

        stage('Platform Update'){
            timestamps{
                if(envChoices =='staging'){
                    SETTINGS.setEnvironment('platform')
                }
                else if(envChoices == ''){
                    SETTINGS.setEnvironment('dev_platform')
                }
                withEnv([
                    "AzureSubscriptionIDProd=${SETTINGS['subscriptionID']}", "AzureResourceGroupNameProd=${SETTINGS['resourceGroupName']}",
                    "AzureWebAppAdminNameProd=${SETTINGS['appName']}", "devOrStaging=${'envChoices'}"]){
                    powershell "${psfolder}\\PlatformUpdate.ps1"
                }
            }
        }

        stage('E2E'){
            timestamps{
                timeout(time: 20, unit: 'MINUTES'){
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
            if (envChoices == 'staging')
                timestamps{
                    def subscriptionID = SETTINGS['subscriptionID']
                    def appName = SETTINGS['appName']
                    def slotName = SETTINGS['slotName']
                    def resourceGroupName = SETTINGS['resourceGroupName']
                    powershell "${psfolder}\\SwapSlot.ps1 -SubscriptionID ${subscriptionID} -WebSiteName ${appName} -SlotName ${slotName} -DestResourceGroupName ${resourceGroupName}"
                }
        }

        stage('Cleanup'){
            timestamps{
                Packaging.cleanSolutions(this)
			}
		}
    }
}
