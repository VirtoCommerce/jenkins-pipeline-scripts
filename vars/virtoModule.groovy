#!groovy
import groovy.json.*
import groovy.util.*

def Modules
def Packaging
def Utilities

def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node
    {
		properties([disableConcurrentBuilds()])

		def workspace = env.WORKSPACE.replaceAll('%2F', '_')
		dir(workspace)
		{
			def globalLib = library('global-shared-lib').com.test
			Utilities = globalLib.Utilities
			Packaging = globalLib.Packaging
			Modules = globalLib.Modules

			def deployScript = 'VC-Module2AzureDev.ps1'
			def dockerTag = "${env.BRANCH_NAME}-branch"
			def buildOrder = Utilities.getNextBuildOrder(this)
			projectType = config.projectType
			if (env.BRANCH_NAME == 'support/2.x' || env.BRANCH_NAME == '1.1.3') {
				deployScript = 'VC-Module2AzureQA.ps1'
				dockerTag = "latest"
			}

			def SETTINGS
			def settingsFileContent
			configFileProvider([configFile(fileId: 'shared_lib_settings', variable: 'SETTINGS_FILE')]) {
				settingsFileContent = readFile(SETTINGS_FILE)
			}
			SETTINGS = globalLib.Settings.new(settingsFileContent)
			SETTINGS.setBranch(env.BRANCH_NAME)
			SETTINGS.setProject('module')
			if(env.BRANCH_NAME == '1.1.3')
				SETTINGS.setBranch('support/2.x')

			try {
				//step([$class: 'GitHubCommitStatusSetter', contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: 'ci.virtocommerce.com'], statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: 'Building on Virto Commerce CI', state: 'PENDING']]]])		
				//Utilities.updateGithubCommitStatus(this, 'PENDING', 'Building on Virto Commerce CI')
				Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], '', 'STARTED')

				stage('Checkout') {
					timestamps {
						deleteDir()
						checkout scm
					}				
				}			

				if(Utilities.checkAndAbortBuild(this))
				{
					return true
				}

				stage('Build')
				{
					timestamps { 
													
						Packaging.startAnalyzer(this)
						Packaging.buildSolutions(this)
					}
				}

				stage('Package Module')
				{
					timestamps { 				
						processManifests(false) // prepare artifacts for testing
					}
				}

				stage('Unit Tests')
				{
					timestamps { 				
						Modules.runUnitTests(this)
					}
				}

				stage('Code Analysis') {
					timestamps { 
						Packaging.endAnalyzer(this)
						Packaging.checkAnalyzerGate(this)
					}
				}			

				if (env.BRANCH_NAME=='1.1.3' || env.BRANCH_NAME == 'support/2.x' || env.BRANCH_NAME == 'release') {
					stage('Create Test Environment') {
						timestamps { 
							// Start docker environment
							Packaging.startDockerTestEnvironment(this, dockerTag)
						}
					}

					stage('Install VC Modules'){
						timestamps{

							// install modules
							Packaging.installModules(this, 0)

							// install module
							Modules.installModuleArtifacts(this)

							//check installed modules
							Packaging.checkInstalledModules(this)

						}
					}

					stage('Install Sample Data'){
						timestamps{
							// now create sample data
							Packaging.createSampleData(this)
						}
					}

					stage('Theme Build and Deploy'){
						timestamps {
							def themePath = "${env.WORKSPACE}@tmp\\theme.zip"
							def themeJobName = "../vc-theme-default/${env.BRANCH_NAME}"
							if(env.BRANCH_NAME == "1.1.3")
								themeJobName = "../vc-theme-default/master"
							build(job: themeJobName, parameters: [string(name: 'themeResultZip', value: themePath)])
							Packaging.installTheme(this, themePath)
						}
					}

					stage("Swagger Schema Validation"){
						timestamps{
							def tempFolder = Utilities.getTempFolder(this)
							def schemaPath = "${tempFolder}\\swagger.json"

							Utilities.validateSwagger(this, schemaPath)
						}
					}	

					stage('Integration Tests')
					{
						timestamps {
							Modules.runIntegrationTests(this)
						}
					}

					// stage('E2E'){
					// 	timestamps{
					// 		Utilities.runE2E(this)
					// 	}
					// }
				}

				if (env.BRANCH_NAME == '1.1.3' || env.BRANCH_NAME == 'support/2.x-dev' || env.BRANCH_NAME == 'support/2.x' || env.BRANCH_NAME == 'dev-eventhandler-dynamicproperties'){
					stage('Publish')
					{
						timestamps {
							def moduleId = Modules.getModuleId(this)
							def artifacts = findFiles(glob: 'artifacts\\*.zip')
							Packaging.saveArtifact(this, 'vc', 'module', moduleId, artifacts[0].path)
							if (env.BRANCH_NAME == 'support/2.x' || env.BRANCH_NAME =='1.1.3') {
								processManifests(true) // publish artifacts to github releases
							}
							switch(env.BRANCH_NAME){
								case ['support/2.x', '1.1.3']:
									Packaging.createNugetPackages(this)
									break
								case 'support/2.x-dev':
									Utilities.runSharedPS(this, "${deployScript}", "-SubscriptionID ${SETTINGS['subscriptionID']} -WebAppName ${SETTINGS['appName']} -ResourceGroupName ${SETTINGS['resourceGroupName']}")
									break
							}
						}
					}
				}

				if(Utilities.getRepoName(this) == 'vc-module-pagebuilder'){
					stage('Delivery to virtocommerce.com'){
						timestamps{
							SETTINGS.setProject('virtocommerce')
							SETTINGS.setBranch('support/2.x-dev')
							if(env.BRANCH_NAME == 'support/2.x'){
								SETTINGS.setBranch('support/2.x')
							}
							Utilities.runSharedPS(this, "${deployScript}", "-SubscriptionID ${SETTINGS['subscriptionID']} -WebAppName ${SETTINGS['appName']} -ResourceGroupName ${SETTINGS['resourceGroupName']}")
						}
					}
				}

				stage('Cleanup') {
					timestamps { 
						Packaging.cleanSolutions(this)
					}
				}				
			}
			catch (any) {
				currentBuild.result = 'FAILURE'
				throw any //rethrow exception to prevent the build from proceeding
			}
			finally {
				Packaging.stopDockerTestEnvironment(this, dockerTag)
				Utilities.generateAllureReport(this)
				Utilities.notifyBuildStatus(this, SETTINGS['of365hook'], "Build finished", currentBuild.currentResult)
				step([$class: 'LogParserPublisher',
					failBuildOnError: false,
					parsingRulesPath: env.LOG_PARSER_RULES,
					useProjectRule: false])
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
				Utilities.cleanPRFolder(this)
			}
		}

		//step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
		//Utilities.updateGithubCommitStatus(this, currentBuild.result, '')
    }
}

def processManifests(publish)
{
	// find all manifests
	def manifests = findFiles(glob: '**\\module.manifest')

	if (manifests.size() > 0) {
		for (int i = 0; i < manifests.size(); i++)
		{
			def manifest = manifests[i]
			processManifest(publish, manifest.path)
		}
	}
	else {
		echo "no module.manifest files found"
	}
}

def processManifest(def publish, def manifestPath)
{
	def wsDir = pwd()
	def fullManifestPath = "$wsDir\\$manifestPath"

	echo "parsing $fullManifestPath"
	def manifest = new XmlSlurper().parse(fullManifestPath)

	echo "Upading module ${manifest.id}"
	def id = manifest.id.toString()

	def version = manifest.version.toString()
	def platformVersion = manifest.platformVersion.toString()
	def title = manifest.title.toString()
	def description = manifest.description.toString()
	def projectUrl = manifest.projectUrl.toString()
	def packageUrl = manifest.packageUrl.toString()
	def iconUrl = manifest.iconUrl.toString()
	def releaseNotes = manifest.releaseNotes.toString()

	// get dependencies
	echo "parsing dependencies"
	def dependencies = []
	for (int i = 0; i < manifest.dependencies.dependency.size(); i++)
	{
		def dependency = manifest.dependencies.dependency[i]
		def dependencyObj = [id: dependency['@id'].text(), version: dependency['@version'].text()]
		dependency = null
		dependencies.add(dependencyObj)
	}

	def owners = []
	echo "parsing owners"
	for (int i = 0; i < manifest.owners.owner.size(); i++)
	{
		def owner = manifest.owners.owner[i]
		owners.add(owner.text())
	}

	def authors = []
	echo "parsing authors"
	for (int i = 0; i < manifest.authors.author.size(); i++)
	{
		def author = manifest.authors.author[i]
		authors.add(author.text())
	}

	echo "manifest = null"
	manifest = null

	def manifestDirectory = manifestPath.substring(0, manifestPath.length() - 16)
	echo "prepare release $manifestDirectory"
	Modules.createModuleArtifact(this, manifestDirectory)

	if (publish) {
		packageUrl = Packaging.publishRelease(this, version, releaseNotes)

		if(env.BRANCH_NAME != '1.1.3'){
			updateModule(
				id,
				version,
				platformVersion,
				title,
				authors,
				owners,
				description,
				dependencies,
				projectUrl,
				packageUrl,
				iconUrl)

			publishTweet("${title} ${version} published ${projectUrl} #virtocommerceci")
		}
	}
}

def publishTweet(def status)
{
	//bat "powershell.exe -File \"${env.JENKINS_HOME}\\workflow-libs\\vars\\twitter.ps1\" -status \"${status}\""
}

def updateModule(def id, def version, def platformVersion, def title, def authors, def owners, def description, def dependencies, def projectUrl, def packageUrl, def iconUrl)
{
	// MODULES
	dir('modules') {
		//checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'vc-ci', url: 'git@github.com:VirtoCommerce/vc-modules.git']]])
		git credentialsId: 'vc-ci', url: 'https://github.com/VirtoCommerce/vc-modules.git'


		def inputFile = readFile file: 'modules.json', encoding: 'utf-8'
		def json = Utilities.jsonParse(inputFile)
		parser = null
		def builder = new JsonBuilder(json)

		def foundRecord = false

		for (rec in json) {
			if (rec.id == id) {
               	echo "Modifying existing record in modules.json"
				rec.description = description
				rec.title = title
				rec.version = version
				rec.platformVersion = platformVersion
				rec.description = description
				rec.dependencies = dependencies
				if (projectUrl != null && projectUrl.length() > 0) {
					rec.projectUrl = projectUrl
				}
				if (packageUrl != null && packageUrl.length() > 0) {
					rec.packageUrl = packageUrl
				}
				if (iconUrl != null && iconUrl.length() > 0) {
					rec.iconUrl = iconUrl
				}

				rec.dependencies = dependencies
				rec.authors = authors
				rec.owners = owners

                foundRecord = true
				break
			}
		}

		if (!foundRecord) {
			// create new
			echo "Creating new record in modules.json"
			json.add([
				id: id,
				title: title,
				version: version,
				platformVersion: platformVersion,
				authors: authors,
				owners: owners,
				description: description,
				dependencies: dependencies,
				projectUrl: projectUrl,
				packageUrl: packageUrl,
				iconUrl: iconUrl
			])
		}

		def moduleJson = builder.toString()
		builder = null
		def prettyModuleJson = JsonOutput.prettyPrint(moduleJson.toString())
		//println(moduleJson)
		writeFile file: 'modules.json', text: prettyModuleJson
	}

	Packaging.updateModulesDefinitions(this, 'modules', id, version)
}
