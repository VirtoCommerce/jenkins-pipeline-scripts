#!groovy
import groovy.json.*
import groovy.util.*

// module script
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    node
    {
    	try 
      	{
      		stage 'Checkout'
			checkout scm
		
		stage 'Build'
			buildSolutions()
		
		stage 'Testing'
			runTests()
		
		bat "\"${tool 'Git'}\" log -1 --pretty=%%B > LAST_COMMIT_MESSAGE"
		git_last_commit=readFile('LAST_COMMIT_MESSAGE')
	
		if (env.BRANCH_NAME == 'master' && git_last_commit.contains('[publish]'))
		{
			stage 'Publishing'
				processManifests()
		}
      	}
	catch (any) {
      		currentBuild.result = 'FAILURE'
      		throw any //rethrow exception to prevent the build from proceeding
	} 
    	finally {
      		step([$class: 'Mailer', notifyEveryUnstableBuild: true, recipients: 'sasha@virtoway.com', sendToIndividuals: true])
    	}

  	step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])
    }
}

def processManifests()
{
	// find all manifests
	def manifests = findFiles(glob: '**\\module.manifest')
		
	if(manifests.size() > 0)
	{
		for(int i = 0; i < manifests.size(); i++)
		{
			def manifest = manifests[i]
			processManifest(manifest.path)
		}
	}
	else
	{
		echo "no module.manifest files found"
	}
}

def processManifest(def manifestPath)
{
	echo "reading $manifestPath"
	def manifestFile = readFile file: "$manifestPath", encoding: 'utf-8'
	def manifest = new XmlSlurper().parseText(manifestFile)
	manifestFile = null

	//echo manifestFile
    	echo "Upading module ${manifest.id}"
    	def id = manifest.id.toString()
    	
    	def version = manifest.version.toString() 
    	def platformVersion = manifest.platformVersion.toString()
    	def title = manifest.title.toString()
    	def description = manifest.description.toString()
    	def projectUrl = manifest.projectUrl.toString()
    	def packageUrl = manifest.packageUrl.toString()
    	def iconUrl = manifest.iconUrl.toString()
    	
    	// get dependencies
    	def dependencies = []
    	for(int i = 0; i < manifest.dependencies.size(); i++)
	{
		def dependency = manifest.dependencies[i]
		def dependencyObj = [id: dependency.@id, version: dependency.@version]
		dependencies.add(dependencyObj)
	}
	
    	manifest = null
    	
    	def manifestDirectory = manifestPath.substring(0, manifestPath.length() - 16)
    	packageUrl = publishRelease(manifestDirectory, version)
    	
    	updateModule(
    		id, 
    		version, 
    		platformVersion,
    		title,
    		description,
    		dependencies,
    		projectUrl,
    		packageUrl,
    		iconUrl)
}

def updateModule(def id, def version, def platformVersion, def title, def description, def dependencies, def projectUrl, def packageUrl, def iconUrl)
{
	// MODULES
        dir('modules') {
            checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'sasha-jenkins', url: 'git@github.com:VirtoCommerce/vc-modules.git']]])

            def inputFile = readFile file: 'modules.json', encoding: 'utf-8'
            def parser = new JsonSlurper()
            def json = parser.parseText(inputFile)
            def builder = new JsonBuilder(json)
            
            for (rec in json) {
               if ( rec.id == id) {
               	    rec.description = description
               	    rec.title = title
               	    rec.description = description
               	    rec.dependencies = dependencies
               	    if (projectUrl!=null && projectUrl.length()>0)
               	    {
               	    	rec.projectUrl = projectUrl
               	    }
               	    if (packageUrl!=null && packageUrl.length()>0)
               	    {
               	    	rec.packageUrl = packageUrl
               	    }
               	    if (iconUrl!=null && iconUrl.length()>0)
               	    {
               	    	rec.iconUrl = iconUrl
               	    }
		break
               }
            }
            
            println(builder.toString())
        }
}

def publishRelease(def manifestDirectory, def version)
{
	tokens = "${env.JOB_NAME}".tokenize('/')
    	//org = tokens[0]
    	def REPO_NAME = tokens[1]
    	//branch = tokens[2]
	def REPO_ORG = "VirtoCommerce"

	def tempFolder = pwd(tmp: true)
	def wsFolder = pwd()
	def tempDir = "$tempFolder\\vc-module"
	def modulesDir = "$tempDir\\_PublishedWebsites"
	def packagesDir = "$wsFolder\\artifacts"
	def foundProjects = false
		    		
	dir(packagesDir)
	{
		deleteDir()
	}
			
	dir(manifestDirectory)
	{
		def projects = findFiles(glob: '*.csproj')
		if(projects.size() > 0)
		{
			for(int i = 0; i < projects.size(); i++)
			{
				def project = projects[i]
				bat "\"${tool 'MSBuild 12.0'}\" \"$project.name\" /nologo /verbosity:m /t:PackModule /p:Configuration=Release /p:Platform=\"Any CPU\" /p:DebugType=none /p:AllowedReferenceRelatedFileExtensions=: \"/p:OutputPath=$tempDir\" \"/p:VCModulesOutputDir=$modulesDir\" \"/p:VCModulesZipDir=$packagesDir\""			
			}
			
			foundProjects = true
		}
	}

	if(foundProjects)
	{
		dir(packagesDir)
		{
			def artifacts = findFiles(glob: '*.zip')
			if(artifacts.size() > 0)
			{
				for(int i = 0; i < artifacts.size(); i++)
				{
					def artifact = artifacts[i]
					bat "${env.Utils}\\github-release release --user $REPO_ORG --repo $REPO_NAME --tag v${version}"
					bat "${env.Utils}\\github-release upload --user $REPO_ORG --repo $REPO_NAME --tag v${version} --name \"${artifact}\" --file \"${artifact}\""
					echo "uploaded to https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
					return  "https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
				}
			}
		}
	}

	//bat "${env.Utils}\\github-release info -u VirtoCommerce -r vc-module-jenkinssample"
}

def buildSolutions()
{
	def solutions = findFiles(glob: '*.sln')

	if(solutions.size() > 0)
	{
		for(int i = 0; i < solutions.size(); i++)
		{
			def solution = solutions[i]
			bat "Nuget restore ${solution.name}"
			bat "\"${tool 'MSBuild 12.0'}\" \"${solution.name}\" /p:Configuration=Debug /p:Platform=\"Any CPU\""
		}
	}
}

def runTests()
{
	def xUnit = env.XUnit
	def xUnitExecutable = "${xUnit}\\xunit.console.exe"
	
	def testDlls = findFiles(glob: '**\\bin\\Debug\\*Test.dll')
	if(testDlls.size() > 0)
	{
		stage 'Running tests'
			String paths = ""
			for(int i = 0; i < testDlls.size(); i++)
			{
				def testDll = testDlls[i]
				paths += "\"$testDll.path\" "
				
			}
			
			bat "${xUnitExecutable} ${paths} -xml xUnit.Test.xml -trait 'category=ci' -parallel none"
			step([$class: 'XUnitPublisher', testTimeMargin: '3000', thresholdMode: 1, thresholds: [[$class: 'FailedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: ''], [$class: 'SkippedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']], tools: [[$class: 'XUnitDotNetTestType', deleteOutputFiles: true, failIfNotNew: false, pattern: '*.xml', skipNoTestFiles: true, stopProcessingIfError: false]]])
	}
}
