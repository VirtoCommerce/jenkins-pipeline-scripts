def TMP_DIR
def ZIP_NAME
def SETTINGS

pipeline {
    agent any

    options
    {
        timestamps()
    }

    stages
    {
        stage('Init')
        {
            steps
            {
                script
                {
                    ZIP_NAME = "${currentBuild.startTimeInMillis}_${env.BUILD_ID}.zip"
                    ZIP_PATH = "${env.WORKSPACE}@tmp\\${ZIP_NAME}"
                    configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
                        SETTINGS = new Settings(readFile(SETTINGS_FILE))
                    }
                    SETTINGS.setRegion("qaenv")
                    SETTINGS.setEnvironment("master")
                }
            }
        }

        stage("Collecting Data")
        {
            steps
            {
                script
                {
                    def targetFiles = []
                    targetFiles.add("${env.JENKINS_HOME}\\org.jenkinsci.plugins.configfiles.GlobalConfigFiles.xml")
                    def tmpDir = "${env.WORKSPACE}@tmp\\tmpDir"
                    TMP_DIR = tmpDir
                    dir(tmpDir)
                    {
                        deleteDir()
                        powershell script: "Copy-Item -Path \"${targetFiles.join(',')}\" -Destination ${tmpDir} -Recurse -Force", label: "Copy"
                    }
                    zip zipFile: ZIP_PATH, dir: tmpDir
                }
            }
        }

        stage("Sending Archive")
        {
            steps
            {
                script
                {
                    powershell script: "${env.Utils}\\AzCopy10\\AzCopy copy ${ZIP_PATH} ${SETTINGS['blobUrl']}", label: "AzCopy"
                }
            }
        }
    }
}