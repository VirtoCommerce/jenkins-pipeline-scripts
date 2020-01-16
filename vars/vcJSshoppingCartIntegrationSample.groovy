#!groovy
import jobs.scripts.*

// module script
def call(body){
	// evaluate the body block, and collect configuration into the object
	def config = [:]
	body.resolveStrategy = Closure.DELEGATE_FIRST
	body.delegate = config
	body()

	node
	{
		properties([disableConcurrentBuilds()])
		// configuration parameters
		def solution = config.solution
		projectType = config.projectType
		
		def SETTINGS
		def settingsFileContent
		configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
			settingsFileContent = readFile(SETTINGS_FILE)
		}
		SETTINGS = new Settings(settingsFileContent)
		
		SETTINGS.setRegion("vcJSshoppingCartIntegrationSample")
		SETTINGS.setEnvironment(env.BRANCH_NAME)

		solution = "VirtoCommerce.JavaScriptShoppingCart.IntegrationSample.sln"

		def websiteDir = 'VirtoCommerce.JavaScriptShoppingCart.IntegrationSample'
		def webProject = 'VirtoCommerce.JavaScriptShoppingCart.IntegrationSample\\VirtoCommerce.JavaScriptShoppingCart.IntegrationSample.csproj'
		def prefix = Utilities.getRepoNamePrefix(this)

		def zipArtifact = "${prefix}-sample"
		def deployScript = 'VC-2Azure.ps1'

		//Utilities.notifyBuildStatus(this, prefix, SETTINGS['of365hook'], '', 'STARTED')
		try{
			stage('Checkout'){
				timestamps{
					if(env.BRANCH_NAME == 'master'){
						deleteDir()
					}
					checkout scm
				}
			}
			def currentProjectDir = "${env.WORKSPACE}\\JsShoppngCartIntergationSample"
			dir(currentProjectDir){
				def version = "1.0.0" //Utilities.getAssemblyVersion(this, webProject)
				//Utilities.checkReleaseVersion(this, version)

				stage('Build'){
					timestamps{
						Packaging.startAnalyzer(this)
						Packaging.runBuild(this, solution)
					}
				}

				stage('Packaging'){
					timestamps{
						Packaging.createReleaseArtifact(this, version, webProject, zipArtifact, websiteDir)
					}
				}

				def tests = Utilities.getTestDlls(this)
				if(tests.size() > 0)
				{
					stage('Unit Tests'){
						timestamps{
							Packaging.runUnitTests(this, tests)
						}
					}
				}

				stage('Code Analysis'){
					timestamps{
						Packaging.endAnalyzer(this)
						Packaging.checkAnalyzerGate(this)
					}
				}

				if (env.BRANCH_NAME == 'master'){
					stage('ARM Deploy'){
						timestamps{
							Utilities.createInfrastructure(this, "JS")
						}
					}
				}

				if (env.BRANCH_NAME == 'master'){
					stage('Publish'){
						timestamps{
							def notes = Utilities.getReleaseNotes(this, webProject)
							//Packaging.publishRelease(this, version, notes)

							withEnv([	"AzureSubscriptionID=${SETTINGS['subscriptionID']}",
										"AzureResourceGroupName=${SETTINGS['resourceGroupName']}",
										"AzureWebAppName=${SETTINGS['webAppName-dev']}"
										]){
								Utilities.runSharedPS(this, "${deployScript}", "-Prefix ${prefix}")
							}
							withEnv([	"AzureSubscriptionID=${SETTINGS['subscriptionID']}",
										"AzureResourceGroupName=${SETTINGS['resourceGroupName']}",
										"AzureWebAppName=${SETTINGS['webAppName-qa']}"
										]){
								Utilities.runSharedPS(this, "${deployScript}", "-Prefix ${prefix}")
							}
							withEnv([	"AzureSubscriptionID=${SETTINGS['subscriptionID']}",
										"AzureResourceGroupName=${SETTINGS['resourceGroupName']}",
										"AzureWebAppName=${SETTINGS['webAppName-demo']}"
										]){
								Utilities.runSharedPS(this, "${deployScript}", "-Prefix ${prefix}")
							}
						}
					}
				}
			} // dir

			stage('Cleanup'){
				timestamps{
					bat "dotnet build-server shutdown"
				}
			}
		}
		catch (any){
			currentBuild.result = 'FAILURE'
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally{
			Utilities.cleanPRFolder(this)
			Utilities.checkLogForWarnings(this)
			//Utilities.notifyBuildStatus(this, prefix, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
			step([$class: 'LogParserPublisher',
				  failBuildOnError: false,
				  parsingRulesPath: env.LOG_PARSER_RULES,
				  useProjectRule: false])
		}
		//Utilities.updateGithubCommitStatus(this, currentBuild.result, '')
	}
}
