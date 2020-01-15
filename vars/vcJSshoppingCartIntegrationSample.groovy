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
		def SETTINGS = new Settings(readFile("${env.WORKSPACE}@libs\\virto-shared-library\\resources\\settings.json"))
		SETTINGS.setRegion("vcJSshoppingCartIntegrationSample")
		SETTINGS.setEnvironment(env.BRANCH_NAME)

		solution = "JsShoppngCartIntergationSample\\VirtoCommerce.JavaScriptShoppingCart.IntegrationSample.sln"
		projectType = ""

		def websiteDir = 'JsShoppngCartIntergationSample\\VirtoCommerce.JavaScriptShoppingCart.IntegrationSample'
		def webProject = 'JsShoppngCartIntergationSample\\VirtoCommerce.JavaScriptShoppingCart.IntegrationSample.csproj'
		def prefix = '' //Utilities.getRepoNamePrefix(this)
		def slave = Utilities.getSlave(this)
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
			def version = Utilities.getAssemblyVersion(this, webProject)

			//Utilities.checkReleaseVersion(this, version)

			stage('Build'){
				timestamps{
					Packaging.startAnalyzer(this)
					Packaging.runBuild(this, solution)
				}
			}

			def dockerImage

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
				stage('Publish'){
					timestamps{
						def notes = Utilities.getReleaseNotes(this, webProject)
						//Packaging.publishRelease(this, version, notes)

						def subscriptionID = SETTINGS['subscriptionID']
						def resourceGroupName = SETTINGS['resourceGroupName']
						def webAppName = SETTINGS['webAppName-dev']
						withEnv(["AzureSubscriptionID=${subscriptionID}", "AzureResourceGroupName=${resourceGroupName}", , "AzureWebAppName=${webAppName}"]){
							Utilities.runSharedPS(this, "${deployScript}", "-Prefix ${prefix}")
						}
						webAppName = SETTINGS['webAppName-qa']
						webAppName = SETTINGS['webAppName-demo']
					}
				}
			}

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
