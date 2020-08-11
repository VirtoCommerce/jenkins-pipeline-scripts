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
def docStatus = "Docs build success"

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
                    SETTINGS.setEnvironment('master')
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
                    def psfolder = "${env.WORKSPACE}\\resources\\virtocommerce"
                    powershell script: "Remove-Item -Path ${csRoot}\\* -Recurse -Force -ErrorAction Continue", label: "Clean Workspace"
                    dir(csRoot)
                    {
                        try
                        {
                            pwsh "${psfolder}\\vc_docs_get_sources.ps1"
                        }
                        catch(any)
                        {
                            docStatus = "Docs build failed"
                        }
                        finally
                        {
                            echo "${docStatus}"
                        }
                    }
                    dir(platformRoot)
                    {
                        pwsh script: "mkdocs build", label: "Build mkdocs"
                        def zipFile = "${csRoot}\\site.zip"
                         zip zipFile: zipFile, dir: "site"
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
                    def artifact = "${csRoot}\\site.zip"
                    def psfolder = "${env.WORKSPACE}\\resources\\virtocommerce"

                    withEnv(["AzureSubscriptionIDProd=${SETTINGS['subscriptionID']}", "AzureResourceGroupNameProd=${SETTINGS['resourceGroupName']}", "AzureWebAppNameProd=${SETTINGS['webAppNameProd']}", "ArtifactPath=${artifact}"]){
                        powershell script: "${psfolder}\\DocsUpdate.ps1", label: "Upload artifact."
                    }
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
