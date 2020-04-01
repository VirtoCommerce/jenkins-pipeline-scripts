#!groovy
import jobs.scripts.*

 

// module script
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    node {
        def storeName = config.sampleStore
        def deployScript = 'rs-theme2Dev.ps1'
        def packageFile = 'package.json'
        
        if (env.BRANCH_NAME == 'qa') {
            deployScript = 'rs-theme2QA.ps1'
        }
        
        try { 
            stage('Checkout') {
                timestamps { 
                    checkout scm
                }

            stage('Build') {
                timestamps { 
                    runGulpBuild()
                }
            }

            stage('Publish')
            {
                timestamps
                {
                    def artifacts = findFiles glob: "artifacts/*.zip"
                    Packaging.saveArtifact(this, 'rs', 'theme', config.sampleStore, artifacts[0].path)
                }
            }
            
            // if (env.BRANCH_NAME == 'dev') {
            //     stage('PreRelease') {
            //         timestamps {
            //             deployToAzure(deployScript)
            //         }
            //     }            
            // }

 

            // if (env.BRANCH_NAME == 'qa') {
            //     stage('Release') {
            //         timestamps { 
            //             publishRelease(getVersion(packageFile))
            //             deployToAzure(deployScript)
            //         }
            //     }
            // }
        }
        catch (any) {
            currentBuild.result = 'FAILURE'
            throw any //rethrow exception to prevent the build from proceeding
        }
        finally {
            step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'rs.support@virtoway.com', sendToIndividuals: true])
        }
    }
}

 

def runGulpBuild()
{
    def wsFolder = pwd()
    def packagesDir = "$wsFolder\\artifacts"

 

    dir(packagesDir)
    {
        deleteDir()
    } 
    
    bat "npm install --cache-min 999999"
    bat "node node_modules\\gulp\\bin\\gulp.js compress"
}

 

def deployToAzure(def deployScript)
{
     bat "powershell.exe -File \"${env.JENKINS_HOME}\\workflow-libs\\vars\\${deployScript}\" -ErrorAction Stop"
}

 

def getVersion(def packageFile)
{
    echo "Searching for version inside $packageFile file"
    def wsFolder = pwd()
    def fullPath = "$wsFolder\\$packageFile"
    def json = new groovy.json.JsonSlurperClassic().parse(new java.io.File(fullPath))
    def version = json.version
    
    echo "Found version ${version}"
    return version
}

 

def publishRelease(def version)
{
    tokens = "${env.JOB_NAME}".tokenize('/')
    def REPO_NAME = tokens[1]
    def REPO_ORG = "Rainbow-Sandals"

 

    def tempFolder = pwd(tmp: true)
    def wsFolder = pwd()
    def packagesDir = "$wsFolder\\artifacts"

 

    dir(packagesDir)
    {
        def artifacts = findFiles(glob: '*.zip')
        if (artifacts.size() > 0) {
            for (int i = 0; i < artifacts.size(); i++)
            {
                def artifact = artifacts[i]
                bat "${env.Utils}\\github-release release --user $REPO_ORG --repo $REPO_NAME --tag v${version}"
                bat "${env.Utils}\\github-release upload --user $REPO_ORG --repo $REPO_NAME --tag v${version} --name \"${artifact}\" --file \"${artifact}\""
                echo "uploaded to https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
                return "https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
            }
        }
    }
}