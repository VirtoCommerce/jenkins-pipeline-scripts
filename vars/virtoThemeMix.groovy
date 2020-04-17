#!groovy
import jobs.scripts.*

// module script
def call(body)
{
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

	node
	{
		properties([disableConcurrentBuilds()])
	    def storeName = config.sampleStore
		projectType = config.projectType
		if(projectType==null)
		{
			projectType = 'Theme'
		}
		def themeStyleAndJs
		def SETTINGS
		def settingsFileContent
		configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')])
		{
			settingsFileContent = readFile(SETTINGS_FILE)
		}
		SETTINGS = new Settings(settingsFileContent)
		SETTINGS.setEnvironment(env.BRANCH_NAME)
		if(storeName == 'odt')
		{
			SETTINGS.setRegion('themeMixOdt')
			themeStyleAndJs = "${env.WORKSPACE}\\client-app"
		}
		else
		{
			SETTINGS.setRegion('themeMix')
			themeStyleAndJs = "${env.WORKSPACE}\\ng-app"
		}

		try
		{
			echo "Building branch ${env.BRANCH_NAME}"
			Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')

			stage('Checkout')
			{
				timestamps
				{
					deleteDir()
					checkout scm
				}
			}

			stage('Code Analysis')
			{
				timestamps
				{
					// bat "npm install -g typescript"
					// bat "npm install typescript"
					echo "Packaging.startSonarJS"
        			// def fullJobName = Utilities.getRepoName(this)

					// def sqScanner = tool 'SonarScannerJS'
					// withSonarQubeEnv('VC Sonar Server')
					// {
					// 	bat "\"${sqScanner}\\bin\\sonar-scanner.bat\" scan -Dsonar.projectKey=theme_default_${env.BRANCH_NAME} -Dsonar.sources=. -Dsonar.branch=${env.BRANCH_NAME} -Dsonar.projectName=\"${fullJobName}\" -Dsonar.host.url=%SONAR_HOST_URL% -Dsonar.login=%SONAR_AUTH_TOKEN%"
					// }
					Packaging.startSonarJS(this)
				}
			}

			stage('Build')
			{
				timestamps
				{
					if(storeName == 'odt')
					{
						dir("${themeStyleAndJs}")
						{
							bat "npm install"
							bat "npm run lint"
							bat "npm run build"
						}
					}
					else
					{
						dir("${themeStyleAndJs}")
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
					withSonarQubeEnv("VC Sonar Server")
					{
						dir("${themeStyleAndJs}")
						{
							Packaging.checkAnalyzerGate(this)
						}
					}
                }
            }

			def version
			dir("${themeStyleAndJs}")
			{
				version = Utilities.getPackageVersion(this)
			}
			def exclude_folder_list = "@(\"artifacts\", \".git\", \".vs\", \".vscode\", \".scannerwork\", \"node_modules\", \"ng-app@tmp\", \"ng-app\\node_modules\", \"client-app@tmp\", \"client-app\\node_modules\")"
			def exclude_list = "@(\"artifacts\", \".git\", \".vs\", \".vscode\", \".scannerwork\", \"node_modules\", \"ng-app@tmp\", \"ng-app\\node_modules\", \"client-app@tmp\", \"client-app\\node_modules\", \".deployment\", \".gitignore\", \"Jenkinsfile\", \"package-lock.json\", \"deploy.cmd\")"
			powershell returnStatus: true, script: "foreach(\$path in $exclude_folder_list){ robocopy C:\\tmp\\mir ${env.WORKSPACE}\\\$path /MIR }"
			powershell "Get-Item ${env.WORKSPACE} -Recurse -Include $exclude_list | Remove-Item -Recurse -Force"
			def zipFile = "${env.WORKSPACE}\\artifacts\\${storeName}-theme-${version}.zip"

			stage('Packaging')
			{
				timestamps
				{
					echo ":Packaging: ${themeStyleAndJs}"
					zip zipFile: zipFile, dir: "./"

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
						if(!Utilities.isPullRequest(this))
						{
							Packaging.saveArtifact(this, 'vc', 'theme', "${config.sampleStore}\\default", zipFile)
						}
						if (Packaging.getShouldPublish(this))
						{
							Packaging.publishRelease(this, version, "")
						}
						if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master')
						{
							def stagingName = Utilities.getStagingNameFromBranchName(this)
							withEnv(["AzureBlobName=${SETTINGS['azureBlobName']}", "AzureBlobKey=${SETTINGS['azureBlobKey']}", "AzureBlobToken=${SETTINGS['azureBlobToken']}"])
							{
								Utilities.runSharedPS(this, "VC-ThemeMix2Azure.ps1", "-StagingName ${stagingName} -StoreName ${storeName}")
							}

							if(storeName == 'odt' && env.BRANCH_NAME == 'dev')
							{
								timestamps
								{
									timeout(time: 15, unit: 'MINUTES')
									{
										def regionAndEnvChoices = input message: "Make publish for ", parameters: [
                        					choice(name: 'QA', choices:"qa\n")
                    					]
										echo "regionAndEnvChoices: ${regionAndEnvChoices}"
										stagingName = "${env.BRANCH_NAME}"
										SETTINGS.setEnvironment(regionAndEnvChoices)
										withEnv(["AzureBlobName=${SETTINGS['azureBlobName']}", "AzureBlobKey=${SETTINGS['azureBlobKey']}", "AzureBlobToken=${SETTINGS['azureBlobToken']}"])
										{
											Utilities.runSharedPS(this, "VC-ThemeMix2Azure.ps1", "-StagingName ${stagingName} -StoreName ${storeName}")
										}
									}
								}
							}
							else if(storeName == 'odt' && env.BRANCH_NAME == 'master')
							{
								stagingName = "${env.BRANCH_NAME}"
								SETTINGS.setEnvironment('master')
								withEnv(["AzureBlobName=${SETTINGS['azureBlobName']}", "AzureBlobKey=${SETTINGS['azureBlobKey']}", "AzureBlobToken=${SETTINGS['azureBlobToken']}"])
								{
									Utilities.runSharedPS(this, "VC-ThemeMix2Azure.ps1", "-StagingName ${stagingName} -StoreName ${storeName}")
								}
							}
						}
					}
				}
			}
		}
		catch (any)
		{
			currentBuild.result = 'FAILURE'
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally
		{
			step([$class: 'LogParserPublisher',
				  failBuildOnError: false,
				  parsingRulesPath: env.LOG_PARSER_RULES,
				  useProjectRule: false])
			Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
			Utilities.cleanPRFolder(this)
		}
	}
}
