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
        stage("User Input")
        {
            steps
            {
                script
                {
                    UPDATE_CS = true
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
