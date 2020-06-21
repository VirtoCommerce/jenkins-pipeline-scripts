import groovy.json.JsonSlurperClassic

def globalLib = library('global-shared-lib').com.test
def Utilities = globalLib.Utilities
def Packaging = globalLib.Packaging
def Modules = globalLib.Modules
def Settings = globalLib.Settings

def SETTINGS

def TRG_BRANCH
def SRC_BRANCH

def UNSTABLE_CAUSES = []

pipeline
{
    agent any

    options
    {
        timestamps()
    }
    stages
    {
        stage("User Input")
        {
            steps
            {
                script
                {
                    checkout scm
                    def settingsFileContent
                    configFileProvider([configFile(fileId: 'delivery_settings', variable: 'SETTINGS_FILE')]) {
                        settingsFileContent = readFile(SETTINGS_FILE)
                    }
                    SETTINGS = Settings.new(settingsFileContent)
                    def regionChoices = SETTINGS.getProjects().join("\n")
                    def buildOrder = Utilities.getNextBuildOrder(this)
                    def userInputRegion = input message: "Select Region", parameters: [
                        choice(name: 'Region', choices: regionChoices)
                    ]
                    PROJECT_TYPE = 'SOLUTION'
                    REGION = userInputRegion
                    UPDATE_CS = true
                    SETTINGS.setProject(REGION)

                    def envChoices = SETTINGS.getBranches().join("\n")
                    def userEnvInput = input message: "Select Environment", parameters: [
                        choice(name: 'Environments', choices: envChoices)
                    ]
                    ENV_NAME = userEnvInput
                    SETTINGS.setBranch(ENV_NAME)
                    
                    def srcBranches = SETTINGS['branch'] as String[]
                    def targetBranches = Utilities.getSubfolders("${env.SOLUTION_FOLDER}\\vc").join("\n")
                    def userInputBranch = input message: "Select Branch and docker state", parameters: [
                            choice(name:'Source Branch', choices:srcBranches.join("\n"))
                        ]
                    SRC_BRANCH = userInputBranch['Source Branch']
                    echo "Vars: REGION - ${REGION}, DELIVERY_AZURE - ${DELIVERY_AZURE}, ENV_NAME - ${ENV_NAME}, SRC_BRANCH - ${SRC_BRANCH}, TRG_BRANCH - ${TRG_BRANCH}"
                }
            }
        }
        stage("Preparing Solution"){
            steps
            {
                script
                {
                    def solutionRoot = "${env.SOLUTION_FOLDER}\\vc"
                    def csSrc = "${solutionRoot}\\master"
                    def modulesRoot = "${env.WORKSPACE}\\CS\\module"
                    def platformRoot = "${env.WORKSPACE}\\CS\\platform"
                    def platformDocsSite = "${env.WORKSPACE}\\CS\\platform\\site"
                    powershell script: "Remove-Item -Path ${env.WORKSPACE}\\CS\\* -Recurse -Force -ErrorAction Continue", label: "Clean Workspace"
                    // powershell script: "Copy-Item -Path ${csSrc}\\* -Destination ${env.WORKSPACE}\\CS -Recurse -Force", label: "Copy Solution to Workspace"
                    powershell script: "Copy-Item -Path ${csSrc}\\module\\* -Destination ${env.WORKSPACE}\\CS -Recurse -Force", label: "Copy Solution to Workspace"
                    powershell script: "Copy-Item -Path ${csSrc}\\platform\\* -Destination ${env.WORKSPACE}\\CS -Recurse -Force", label: "Copy Solution to Workspace"

                    dir(modulesRoot)
                    {
                        powershell "Get-ChildItem ${modulesRoot} -Name | Rename-Item $_ $_.Name.Replace("VirtoCommerce.", "") -ErrorAction SilentlyContinue -Force -Recurse"
                    }

                    powershell script: "Copy-Item -Path ${modulesRoot}\\* -Destination ${platformRoot}\\docs\\modules -Recurse -Force", label: "Copy Modules to Platform Docs"

                    powershell script: "${env.WORKSPACE}\\CS\\platform\\mkdocs build", label: "Build docs"
                }
            }
        }

        // stage("Update Solution"){
        //     when {
        //         expression {
        //             UPDATE_CS
        //         }
        //     }
        //     steps {
        //         script{
        //             def csRoot = "${env.WORKSPACE}\\CS"
        //             def webAppName = SETTINGS['webAppName']
        //             def webAppPublicName = SETTINGS['webAppPublicName']
        //             def resourceGroupName = SETTINGS['resourceGroupName']
        //             def subscriptionID = SETTINGS['subscriptionID']
        //             def blobToken = SETTINGS['blobToken']
        //             def themeSrcDir = "${csRoot}\\theme"
        //             def themeBlobPath = ""
        //             def themeBlobPathParam = ""
        //             withEnv(["AzureBlobToken=${blobToken}"]){
        //                 Utilities.runSharedPS(this, 'delivery/upload-CS.ps1', 
        //                     "-PlatformDir ${csRoot}\\platform -ModulesDir ${csRoot}\\modules -StorefrontDir ${csRoot}\\storefront -ThemeDir ${themeSrcDir} -WebAppName ${webAppName} -WebAppPublicName ${webAppPublicName} -ResourceGroupName ${resourceGroupName} -SubscriptionID ${subscriptionID} -StorageAccount ${SETTINGS['storageAccount']} -BlobContainerName ${SETTINGS['blobContainerName']} ${themeBlobPathParam}")
        //             }
        //         }
        //     }
        // }
    }

    post
    {
        always
        {
            script
            {
                if(currentBuild.resultIsBetterOrEqualTo('SUCCESS') && UNSTABLE_CAUSES.size()>0){
                    currentBuild.result = 'UNSTABLE'
                    for(cause in UNSTABLE_CAUSES){
                        echo cause
                    }
                }
            }
        }
    }
}
