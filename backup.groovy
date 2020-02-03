def TMP_DIR
def ZIP_NAME

pipeline {
    agent any

    options
    {
        timestamps()
    }

    stages
    {
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
                        powershell "Copy-Item -Path \"${targetFiles.join(',')}\" -Destination ${tmpDir} -Recurse -Force"
                    }
                    ZIP_NAME = "${env.BUILD_ID}.zip"
                    zip zipFile: ZIP_NAME, dir: tmpDir
                }
            }
        }

        stage("Sending Archive")
        {
            steps
            {
                script
                {
                    echo "stub"
                }
            }
        }
    }
}