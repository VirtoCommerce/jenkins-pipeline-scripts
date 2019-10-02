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
		properties([disableConcurrentBuilds()])
	    def storeName = config.sampleStore
		projectType = config.projectType
		if(projectType==null){
			projectType = 'Theme'
		}

		def SETTINGS
		def settingsFileContent
		configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
			settingsFileContent = readFile(SETTINGS_FILE)
		}
		SETTINGS = new Settings(settingsFileContent)
		SETTINGS.setEnvironment(env.BRANCH_NAME)
		SETTINGS.setRegion('themeMix')

		try {

			echo "Building branch ${env.BRANCH_NAME}"
			Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')

			stage('Checkout') {
				timestamps { 
					deleteDir()
					checkout scm
				}
			}

			stage('Code Analysis'){
				timestamps{
					bat "npm install -g typescript"
					bat "npm install typescript"
					echo "Packaging.startSonarJS"
        			def fullJobName = Utilities.getRepoName(this)

					def sqScanner = tool 'SonarScannerJS'
					withSonarQubeEnv('VC Sonar Server') {
						bat "\"${sqScanner}\\bin\\sonar-scanner.bat\" scan -Dsonar.projectKey=theme_default_${env.BRANCH_NAME} -Dsonar.sources=. -Dsonar.branch=${env.BRANCH_NAME} -Dsonar.projectName=\"${fullJobName}\" -Dsonar.host.url=%SONAR_HOST_URL% -Dsonar.login=%SONAR_AUTH_TOKEN%"
					}
				}
			}

			stage('Build')
			{
				timestamps
				{
					if (env.BRANCH_NAME == 'dev')
					{
						dir("${env.WORKSPACE}\\ng-app")
						{
							bat "npm install --prefer-offline"
							bat "npm run build-prod"
						}
					}
				}
			}

			stage('Quality Gate')
			{
				timestamps
				{
					withSonarQubeEnv("VC Sonar Server"){
						dir("${env.WORKSPACE}\\ng-app")
						{
							Packaging.checkAnalyzerGate(this)
						}
					}
                }
            }

			def version
			dir("${env.WORKSPACE}\\ng-app")
			{
				version = Utilities.getPackageVersion(this)
			}
			// bat "rmdir .git .vs .vscode .scannerwork node_modules ng-app@tmp ng-app\\node_modules /s /q"
			// bat "del .deployment .gitignore Jenkinsfile package-lock.json deploy.cmd /s /q"
			//powershell "Remove-Item -Path .git .vs .vscode .scannerwork node_modules ng-app@tmp ng-app\\node_modules .deployment .gitignore Jenkinsfile package-lock.json deploy.cmd -Force -ErrorAction Ignore"
			//def excludes_list = "@(\"artifacts\", \".git\", \".vs\", \".vscode\", \".scannerwork\", \"node_modules\", \"ng-app@tmp\", \"ng-app\\node_modules\", \".deployment\", \".gitignore\", \"Jenkinsfile\", \"package-lock.json\", \"deploy.cmd\")"
			def exclude_list = "artifacts .git .vs .vscode .scannerwork node_modules ng-app@tmp ng-app\\node_modules .deployment .gitignore Jenkinsfile package-lock.json deploy.cmd"
			def zipFile = "${env.WORKSPACE}\\artifacts\\dental-theme-${version}.zip"
			stage('Packaging')
			{
				timestamps {
					//zip zipFile: zipFile, dir: "./"
					powershell "New-Item -ItemType Directory -Force -Path ${env.WORKSPACE}\\artifacts"
					powershell "Copy-Item -Path .\\ -Destination ${env.WORKSPACE}\\artifacts\\tmp -Exclude ${exclude_list} -Recurse"
					zip zipFile: zipFile, dir: "${env.WORKSPACE}\\artifacts\\tmp\\"
					powershell "Remove-Item ${env.WORKSPACE}\\artifacts\\tmp -Recurse -Force"
					//powershell "Get-ChildItem ./ -Directory | where { \$_.Name -notin ${excludes_list}} | Compress-Archive -DestinationPath ${zipFile} -Update"

					if(params.themeResultZip != null) {
						bat "copy /Y \"${zipFile}\" \"${params.themeResultZip}\""
					}
				}
			}

			if(params.themeResultZip == null)
			{
				stage('Publish')
				{
					timestamps
					{
						if (Packaging.getShouldPublish(this))
						{
							Packaging.publishRelease(this, version, "")
						}
						if (env.BRANCH_NAME == 'dev')
						{
							def stagingName = Utilities.getStagingNameFromBranchName(this)
							withEnv(["AzureBlobName=${SETTINGS['azureBlobName']}", "AzureBlobKey=${SETTINGS['azureBlobKey']}"]){
								Utilities.runSharedPS(this, "VC-ThemeMix2Azure.ps1", "-StagingName ${stagingName} -StoreName ${storeName}")
							}
						}
					}
				}
			}
		}
		catch (any) {
			currentBuild.result = 'FAILURE'
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
			step([$class: 'LogParserPublisher',
				  failBuildOnError: false,
				  parsingRulesPath: env.LOG_PARSER_RULES,
				  useProjectRule: false])
			Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
			// if(currentBuild.result != 'FAILURE') {
			// 	step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
			// }
			// else {
			// 	def log = currentBuild.rawBuild.getLog(300)
			// 	def failedStageLog = Utilities.getFailedStageStr(log)
			// 	def failedStageName = Utilities.getFailedStageName(failedStageLog)
			// 	def mailBody = Utilities.getMailBody(this, failedStageName, failedStageLog)
			// 	emailext body:mailBody, subject: "${env.JOB_NAME}:${env.BUILD_NUMBER} - ${currentBuild.currentResult}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
			// }
		}
	
	  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
	}
}
