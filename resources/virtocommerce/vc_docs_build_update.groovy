import groovy.json.JsonSlurperClassic

def globalLib = library('global-shared-lib').com.test
def Utilities = globalLib.Utilities
def Packaging = globalLib.Packaging
def Modules = globalLib.Modules
def Settings = globalLib.Settings

def SETTINGS

def UPDATE_CS = false

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
        stage("Init")
        {
            steps
            {
                script
                {
                    UPDATE_CS = true
                    def settingsFileContent
                    configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')])
                    {
                        settingsFileContent = readFile(SETTINGS_FILE)
                    }
                    SETTINGS = new Settings(settingsFileContent)
                    SETTINGS.setRegion('virtocommerce')
                }
            }
        }
        stage("Preparing Solution"){
            steps
            {
                script
                {
                    def solutionRoot = "${env.WORKSPACE}\\CS"
                    def platformRoot = "${env.WORKSPACE}\\CS\\vc-platform"
                    def artifactPath = "${env.WORKSPACE}\\CS\\vc-platform\\site"
                    powershell script: "Remove-Item -Path ${solutionRoot}\\* -Recurse -Force -ErrorAction Continue", label: "Clean Workspace"
                    dir(solutionRoot)
                    {
                        Utilities.runPS(this, "virtocommerce/vc_docs_get_sources.ps1", "-Verbose -Debug")
                    }
                    dir(platformRoot)
                    {
                        pwsh script: "mkdocs build", label: "Build mkdocs"
                        zip zipFile: artifactPath, dir: solutionRoot
                    }
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
