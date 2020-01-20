import jobs.scripts.*

node {
    properties([disableConcurrentBuilds()])

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

        stage('Platform Update'){
            timestamps{
                SETTINGS.setEnvironment('platform')
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
            timestamps{
                SETTINGS.setEnvironment('platform')
                withEnv([
                    "SubscriptionID=${SETTINGS['subscriptionID']}", "DestResourceGroupName=${SETTINGS['resourceGroupName']}",
                    "WebSiteName=${SETTINGS['appName']}", "SlotName=${SETTINGS['slotName']}"]){
                    powershell "${psfolder}\\SwapSlot.ps1"
                }
            }
        }

        stage('Cleanup'){
            timestamps{
                Packaging.cleanSolutions(this)
			}
		}
    }
}
