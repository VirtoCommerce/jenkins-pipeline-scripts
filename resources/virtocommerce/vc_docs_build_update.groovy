import groovy.json.JsonSlurperClassic
import jobs.scripts.Settings

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
                    def csRoot = "${env.WORKSPACE}\\CS"
                    def platformRoot = "${env.WORKSPACE}\\CS\\vc-platform"
                    def artifactPath = "${env.WORKSPACE}\\CS\\vc-platform\\site"
                    def psfolder = "${env.WORKSPACE}\\resources\\virtocommerce"
                    powershell script: "Remove-Item -Path ${csRoot}\\* -Recurse -Force -ErrorAction Continue", label: "Clean Workspace"
                    dir(csRoot)
                    {
                        pwsh "${psfolder}\\vc_docs_get_sources.ps1"
                    }
                    dir(platformRoot)
                    {
                        pwsh script: "mkdocs build", label: "Build mkdocs"
                        def zipFile = "${csRoot}\\site.zip"
                        zip zipFile: zipFile, dir: "./"
                    }
                }
            }
        }

        stage("Update Solution"){
            when {
                expression {
                    UPDATE_CS
                }
            }
            steps {
                script{
                    def csRoot = "${env.WORKSPACE}\\CS"
                    // def webAppPublicName = SETTINGS['webAppPublicName']
                    // def resourceGroupName = SETTINGS['resourceGroupName']
                    // def subscriptionID = SETTINGS['subscriptionID']
                    // def blobToken = SETTINGS['blobToken']
                    // withEnv(["AzureBlobToken=${blobToken}"]){
                    //     Utilities.runSharedPS(this, 'delivery/upload-CS.ps1', "-StorefrontDir ${csRoot}\\storefront")
                    // }
                }
            }
        }
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
