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
        
        def envChoices
        stage('User Input'){
            timeout(time: 30, unit: 'MINUTES'){
                envChoices = input(message: "Choose environment to update", parameters: [choice(name: 'Environments', choices:"Dev\nProduction")])
                if(envChoices == 'Dev'){
                    envChoices = ""
                }
                else if (envChoices == 'Production'){
                    envChoices = "staging"
                }
            }
        }

         stage('Storefront Update'){
            timestamps {
                SETTINGS.setEnvironment('dev_storefront')
                withEnv(["AzureSubscriptionIDProd=${SETTINGS['subscriptionID']}", "AzureResourceGroupNameProd=${SETTINGS['resourceGroupName']}", "AzureWebAppNameProd=${SETTINGS['appName']}"]){
                    powershell "${psfolder}\\StorefrontUpdate.ps1"
                }
            }
        }

        stage('ARM Deploy'){
            timestamps{
                if(envChoices == ""){
                    Utilities.createInfrastructure(this, "DEV-VC")  // DEV-VC ? PROD-VC
                }
                else {
                    Utilities.createInfrastructure(this, "PROD-VC")
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
        
        stage('Cleanup') {
            timestamps {
                Packaging.cleanSolutions(this)
			}
		}
    }
}
