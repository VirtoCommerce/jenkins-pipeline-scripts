#!groovy
import groovy.json.*
import groovy.util.*
import jobs.scripts.*

    def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node
    {
	    def deployScript = 'VC-Module2AzureDev.ps1'
		def dockerTag = "${env.BRANCH_NAME}-branch"
		def buildOrder = Utilities.getNextBuildOrder(this)
		projectType = config.projectType
	    if (env.BRANCH_NAME == 'master') {
			deployScript = 'VC-Module2AzureQA.ps1'
			dockerTag = "latest"
		}
		try {	
			step([$class: 'GitHubCommitStatusSetter', contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: 'ci.virtocommerce.com'], statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: 'Building on Virto Commerce CI', state: 'PENDING']]]])			
			Utilities.notifyBuildStatus(this, "started")

			stage('Checkout') {
				timestamps { 		
					checkout scm
				}				
			}			

			if(Utilities.checkAndAbortBuild(this))
			{
				return true
			}

			stage('Build + Analyze')
			{
				timestamps { 
					// clean folder for a release
					if (Packaging.getShouldPublish(this)) {
						deleteDir()
						checkout scm
					}							
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

			stage('Submit Analysis') {
				timestamps { 
					Packaging.endAnalyzer(this)
				}
			}

			// No need to occupy a node
			stage("Quality Gate"){
				Packaging.checkAnalyzerGate(this)
			}	

			/*if (env.BRANCH_NAME == 'master') {
				stage('Build platform and storefront') {
					timestamps{
						build(job: "../vc-platform/${env.BRANCH_NAME}", parameters: [booleanParam(name: 'isCaused', value: true)])
                        build(job: "../vc-storefront-core/${env.BRANCH_NAME}", parameters: [booleanParam(name: 'isCaused', value: true)])
					}
				}
			}*/
			

			if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
				stage('Prepare Test Environment') {
					timestamps { 
						// Start docker environment
						Packaging.startDockerTestEnvironment(this, dockerTag)

						// install modules
						Packaging.installModules(this, 0)

                        // install module
                        Modules.installModuleArtifacts(this)

						//check installed modules
						Packaging.checkInstalledModules(this)

						// now create sample data
						Packaging.createSampleData(this)
					}
				}

				stage("Swagger schema validation"){
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
			}				

			if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'master') {
				stage('Publish')
				{
					timestamps { 	
						Utilities.runSharedPS(this, "resources\\azure\\${deployScript}")				
						if (Packaging.getShouldPublish(this)) {
							processManifests(true) // publish artifacts to github releases
						}
						if(env.BRANCH_NAME == 'master')
							Packaging.createNugetPackages(this)
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
			Utilities.notifyBuildStatus(this, currentBuild.result)
			throw any //rethrow exception to prevent the build from proceeding
		}
		finally {
			Packaging.stopDockerTestEnvironment(this, dockerTag)
			if(currentBuild.result != 'FAILURE') {
				step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']])])
			}
			else {
				def log = currentBuild.rawBuild.getLog(300)
				def failedStageLog = Utilities.getFailedStageStr(log)
				def failedStageName = Utilities.getFailedStageName(failedStageLog)
				def mailBody = Utilities.getMailBody(this, failedStageName, failedStageLog)
				emailext body:mailBody, subject: "${env.JOB_NAME}:${env.BUILD_NUMBER} - ${currentBuild.currentResult}", recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]
			}
			//step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'dev@virtoway.com', sendToIndividuals: true])
		}

		step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
		Utilities.notifyBuildStatus(this, currentBuild.result)
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

def publishTweet(def status)
{
	//bat "powershell.exe -File \"${env.JENKINS_HOME}\\workflow-libs\\vars\\twitter.ps1\" -status \"${status}\""
}

def updateModule(def id, def version, def platformVersion, def title, def authors, def owners, def description, def dependencies, def projectUrl, def packageUrl, def iconUrl)
{
	// MODULES
	dir('modules') {
		checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'sasha-jenkins', url: 'git@github.com:VirtoCommerce/vc-modules.git']]])

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
