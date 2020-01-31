package jobs.scripts;

class Packaging {

    private static String DefaultBranchOrCommitPR = '${sha1}'
    private static String DefaultBranchOrCommitPush = '*/master'
    private static String DefaultRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
    private static String DefaultMSBuild = 'MSBuild'
    private static String DefaultSharedLibName = 'virto-shared-library'

    /*
    private def Context;

    Packaging(script) {
        Context = script;
    }
    */

    /**
     * Creates new docker image
     *
     * @param context Execution Contex, typically "this"
     * @param dockerImageName name of the docker image to create, something like virtocommerce/storefront
     * @param dockerContextFolder folder that docker build will use for context, files from that folder can be added to the docker image
     * @param dockerSourcePath Source of the files to be included in the docker image, can be files within contextFolder or remote source referred by http
     * @param version current version of the build
     * @return reference to a docker image created
     */
    def static createDockerImage(context, String dockerImageName, String dockerContextFolder, String dockerSourcePath, String version) {
        def dockerFileFolder = dockerImageName.replaceAll("/", ".")
		def dockerFolder = ""
        if(context.projectType == 'NETCORE2') {
		    dockerFolder = "docker.core\\windowsnano"
			dockerImageName = dockerImageName // + "-core" 
        }
        else {
		    dockerFolder = "docker"
        }
        context.echo "Building docker image \"${dockerImageName}\" using \"${dockerContextFolder}\" as context folder"
        context.bat "xcopy \"..\\workspace@libs\\virto-shared-library\\resources\\${dockerFolder}\\${dockerFileFolder}\\*\" \"${dockerContextFolder}\\\" /Y /E"
        def dockerImage
        context.dir(dockerContextFolder)
        {
            dockerImage = context.docker.build("${dockerImageName}:${version}".toLowerCase(), "--build-arg SOURCE=\"${dockerSourcePath}\" .")
        }
        return dockerImage
    }

    def static startDockerTestEnvironment(context, String dockerTag)
    {
        def composeFolder = Utilities.getComposeFolder(context)
        context.dir(composeFolder)
        {
            def platformPort = Utilities.getPlatformPort(context)
            def storefrontPort = Utilities.getStorefrontPort(context)
            def sqlPort = Utilities.getSqlPort(context)

            context.echo "DOCKER_PLATFORM_PORT=${platformPort}"
            // 1. stop containers
            // 2. remove instances including database
            // 3. start up new containers
            context.withEnv(["DOCKER_TAG=${dockerTag}", "DOCKER_PLATFORM_PORT=${platformPort}", "DOCKER_STOREFRONT_PORT=${storefrontPort}", "DOCKER_SQL_PORT=${sqlPort}", "COMPOSE_PROJECT_NAME=${context.env.BUILD_TAG}" ]) {
                context.bat "docker-compose stop"
                context.bat "docker-compose rm -f -v"
                context.bat "docker-compose up -d"
            }

            // 4. check if all docker containers are running
            if(!Packaging.checkAllDockerTestEnvironments(context)) {
                // 5. try running it again
                context.withEnv(["DOCKER_TAG=${dockerTag}", "DOCKER_PLATFORM_PORT=${platformPort}", "DOCKER_STOREFRONT_PORT=${storefrontPort}", "DOCKER_SQL_PORT=${sqlPort}", "COMPOSE_PROJECT_NAME=${context.env.BUILD_TAG}" ]) {
                    context.bat "docker-compose up -d"
                }            

                // 6. check one more time
                if(!Packaging.checkAllDockerTestEnvironments(context)) {
                    throw new Exception("can't start one or more docker containers"); 
                }
            }
        }
    }

    def static checkAllDockerTestEnvironments(context)
    {
        if(!Packaging.checkDockerTestEnvironment(context, "vc-platform-web")) { return false }
        if(!Packaging.checkDockerTestEnvironment(context, "vc-storefront-web")) { return false }
        if(!Packaging.checkDockerTestEnvironment(context, "vc-db")) { return false }

        return true
    }

    def static checkDockerTestEnvironment(context, String containerId)
    {
        //def tag = context.env.BUILD_TAG.replace("-", "").toLowerCase()
        def tag = context.env.BUILD_TAG.toLowerCase()
        def containerName = "${tag}_${containerId}_1"
        containerName = containerName.replaceAll("\\.", '')
        context.echo "Checking ${containerName} state ..."
        String result = context.bat(returnStdout: true, script: "docker inspect -f {{.State.Running}} ${containerName}").trim()

        if(result.endsWith('true'))
        {
            context.echo "Docker ${containerId} is RUNNING"
            return true
        }
        else
        {
            context.echo "Docker ${containerId} FAILED"
            return false
        }
    }

    def static stopDockerTestEnvironment(context, String dockerTag)
    {
        def composeFolder = Utilities.getComposeFolder(context)
        context.dir(composeFolder)
        {
            context.withEnv(["DOCKER_TAG=${dockerTag}", "COMPOSE_PROJECT_NAME=${context.env.BUILD_TAG}"]) {
                context.bat "docker-compose down -v"
            }
        }
    }

    def static createSampleData(context)
    {
    	def wsFolder = context.pwd()
         Utilities.runSharedPS(context, "vc-setup-sampledata.ps1", "-apiurl \"${Utilities.getPlatformHost(context)}\"")
    }

    def static installModules(context, needRestart)
    {
    	def wsFolder = context.pwd()
        Utilities.runSharedPS(context, 'vc-setup-modules.ps1', "-apiurl \"${Utilities.getPlatformHost(context)}\" -needRestart ${needRestart}")
    }    

    def static pushDockerImage(context, dockerImage, String dockerTag)
    {
		context.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'docker-hub-credentials', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
			context.sh "docker login --password=${context.PASSWORD} --username=${context.USERNAME}"
		}
		dockerImage.push(dockerTag)
    }

    def static checkInstalledModules(context){
        Utilities.runSharedPS(context, "vc-check-installed-modules.ps1", "-apiurl \"${Utilities.getPlatformHost(context)}\"")
    }

    def static createSwaggerSchema(context, swaggerFile) {
        Utilities.runSharedPS(context, "vc-get-swagger.ps1", "-apiurl \"${Utilities.getPlatformHost(context)}\" -swaggerFile \"${swaggerFile}\"")
    }

    def static createReleaseArtifact(context, version, webProject, zipArtifact, websiteDir)
    {
        context.echo "Preparing release for ${version}"
        def tempFolder = Utilities.getTempFolder(context)
        def websitePath = Utilities.getWebPublishFolder(context, websiteDir)     
        def packagesDir = Utilities.getArtifactFolder(context)

        context.dir(packagesDir)
        {
            context.deleteDir()
        }

        // create artifacts
        if(context.projectType == 'NETCORE2')
        {
            context.bat "dotnet publish \"${webProject}\" -c Release -o \"$tempFolder\\_PublishedWebsites\\${websiteDir}\""
        }
        else
        {
            context.bat "\"${context.tool DefaultMSBuild}\" \"${webProject}\" /nologo /verbosity:m /p:Configuration=Release /p:Platform=\"Any CPU\" /p:DebugType=none \"/p:OutputPath=$tempFolder\""
        }

        (new AntBuilder()).zip(destfile: "${packagesDir}\\${zipArtifact}.${version}.zip", basedir: "${websitePath}")
    }

    def static buildSolutions(context)
    {
        def solutions = context.findFiles(glob: '*.sln')

        if (solutions.size() > 0) {
            for (int i = 0; i < solutions.size(); i++)
            {
                def solution = solutions[i]
                Packaging.runBuild(context, solution.name)
            }
        } 
    }    

    def static runBuild(context, solution)
    {
        if(context.projectType == 'NETCORE2')
        {
            //context.bat "dotnet restore" // no need to run it in .net core 2.0, it should run as part of dotnet msbuild
            //context.bat "dotnet msbuild \"${solution}\" -c Debug"
            // we need to use MSBuild directly to allow sonar analysis to work
            // DebugType=Full is for OpenCover
            context.bat "\"${context.tool DefaultMSBuild}\" \"${solution}\" /p:Configuration=Debug /p:Platform=\"Any CPU\" /t:restore /t:rebuild /m /p:DebugType=Full"
        }
        else
        {
		    context.bat "${context.env.NUGET}\\nuget.exe restore ${solution}"
            context.bat "\"${context.tool DefaultMSBuild}\" \"${solution}\" /p:Configuration=Debug /p:Platform=\"Any CPU\" /t:rebuild /m"
        }
    }

    def static cleanSolutions(context)
    {
        def solutions = context.findFiles(glob: '*.sln')

        if (solutions.size() > 0) {
            for (int i = 0; i < solutions.size(); i++)
            {
                def solution = solutions[i]
                Packaging.cleanBuild(context, solution.name)
            }
        }
    } 

    def static cleanBuild(context, solution)
    {
        context.bat "\"${context.tool DefaultMSBuild}\" \"${solution}\" /t:clean /p:Configuration=Debug /p:Platform=\"Any CPU\" /m"
    }    

    def static startAnalyzer(context, dotnet = false)
    {
        def sqScannerMsBuildHome = context.tool 'Scanner for MSBuild'
        def fullJobName = Utilities.getRepoName(context)
        def coverageFolder = Utilities.getCoverageFolder(context)
        def coverageReportType = 'opencover'
        def scannerPath = "\"${sqScannerMsBuildHome}\\SonarScanner.MSBuild.exe\""
        if(dotnet)
        {
            scannerPath = "dotnet sonarscanner"
        }
        // if(Utilities.isNetCore(context.projectType)){
        //     coverageReportType = 'opencover'
        // }
        context.withSonarQubeEnv('VC Sonar Server') {
            def repoName = Utilities.getRepoName(context)
            def prNumber = Utilities.getPullRequestNumber(context)
            def orgName = Utilities.getOrgName(context)
            if(Utilities.isPullRequest(context)){
                context.bat "${scannerPath} begin /d:\"sonar.branch=${context.env.BRANCH_NAME}\" /n:\"${fullJobName}\" /k:\"${fullJobName}\" /d:sonar.verbose=true /d:sonar.github.oauth=${context.env.GITHUB_TOKEN} /d:sonar.analysis.mode=preview /d:sonar.github.pullRequest=\"${prNumber}\" /d:sonar.github.repository=${orgName}/${repoName} /d:sonar.host.url=%SONAR_HOST_URL% /d:sonar.login=%SONAR_AUTH_TOKEN% /d:sonar.cs.${coverageReportType}.reportsPaths=\"${coverageFolder}\\VisualStudio.Unit.coveragexml\""
            }
            else{
                // Due to SONARMSBRU-307 value of sonar.host.url and credentials should be passed on command line
                context.bat "${scannerPath} begin /d:\"sonar.branch=${context.env.BRANCH_NAME}\" /n:\"${fullJobName}\" /k:\"${fullJobName}\" /d:sonar.verbose=true /d:sonar.host.url=%SONAR_HOST_URL% /d:sonar.login=%SONAR_AUTH_TOKEN% /d:sonar.cs.${coverageReportType}.reportsPaths=\"${coverageFolder}\\VisualStudio.Unit.coveragexml\""
            }
        }        
    }

    def static startSonarJS(context){
        def sqScanner = context.tool 'SonarScannerJS'
        def fullJobName = Utilities.getRepoName(context)
        def sources = "./assets"
        def repoName = Utilities.getRepoName(context)
        def prNumber = Utilities.getPullRequestNumber(context)
        def orgName = Utilities.getOrgName(context)
        def projectKey = "${fullJobName}_${context.env.BRANCH_NAME}".replaceAll('/', '_')

        context.withSonarQubeEnv('VC Sonar Server') {
            context.timeout(activity: true, time: 15){
                if(Utilities.isPullRequest(context)){
                    context.bat "\"${sqScanner}\\bin\\sonar-scanner.bat\" scan -Dsonar.projectKey=${projectKey} -Dsonar.sources=${sources} -Dsonar.branch=${context.env.BRANCH_NAME} -Dsonar.projectName=\"${fullJobName}\" -Dsonar.host.url=%SONAR_HOST_URL% -Dsonar.login=%SONAR_AUTH_TOKEN% -Dsonar.github.oauth=${context.env.GITHUB_TOKEN} -Dsonar.analysis.mode=preview -Dsonar.github.pullRequest=\"${prNumber}\" -Dsonar.github.repository=${orgName}/${repoName}"
                }
                else{
                    context.bat "\"${sqScanner}\\bin\\sonar-scanner.bat\" scan -Dsonar.projectKey=${projectKey} -Dsonar.sources=${sources} -Dsonar.branch=${context.env.BRANCH_NAME} -Dsonar.projectName=\"${fullJobName}\" -Dsonar.host.url=%SONAR_HOST_URL% -Dsonar.login=%SONAR_AUTH_TOKEN%"
                }
            }
        }
    }

    def static endAnalyzer(context, dotnet = false)
    {
        def sqScannerMsBuildHome = context.tool 'Scanner for MSBuild'
        def fullJobName = Utilities.getRepoName(context)
        def scannerPath = "\"${sqScannerMsBuildHome}\\SonarScanner.MSBuild.exe\""
        if(dotnet)
        {
            scannerPath = "dotnet sonarscanner"
        }
        context.withSonarQubeEnv('VC Sonar Server') {
            context.bat "${scannerPath} end /d:sonar.login=%SONAR_AUTH_TOKEN%"
        }          
    }

    def static checkAnalyzerGate(context)
    {
		if(Utilities.isPullRequest(context))
        {
            return
        }
		context.timeout(time: 1, unit: 'HOURS') { // Just in case something goes wrong, pipeline will be killed after a timeout
			def qg = context.waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
			if (qg.status != 'OK' && qg.status != 'WARN') {
			    context.error "Pipeline aborted due to quality gate failure: ${qg.status}"
			}
		}        
    }

    def static runGulpBuild(context)
    {
        context.timeout(activity: true, time: 15){
            def packagesDir = Utilities.getArtifactFolder(context)

            context.dir(packagesDir)
            {
                context.deleteDir()
            }        
            context.bat "npm install --prefer-offline --dev"
            def bowerjs = new File("${context.env.WORKSPACE}\\bower.json")
            if(bowerjs.exists()){
                context.bat "node node_modules\\bower\\bin\\bower install --force-latest"
            }
            context.bat "node node_modules\\gulp\\bin\\gulp.js compress"
        }
    }    

    def static runUnitTests(context, tests)
    {        
        String paths = ""
        for(int i = 0; i < tests.size(); i++)
        {
            def test = tests[i]
            paths += "\"$test.path\" "
        }

        Utilities.runUnitTest(context, "Category=Unit|Category=CI", paths, "xUnit.UnitTests.xml")
    }

	def static publishRelease(context, version, releaseNotes)
	{
		def tempFolder = Utilities.getTempFolder(context)
		def packagesDir = Utilities.getArtifactFolder(context)
		def packageUrl

		context.dir(packagesDir)
		{
			def artifacts = context.findFiles(glob: '*.zip')
			if (artifacts.size() > 0) {
				for (int i = 0; i < artifacts.size(); i++)
				{
					packageUrl = Packaging.publishGithubRelease(context, version, releaseNotes, artifacts[i])
				}
			}
		}

		return packageUrl
	} 

	def static publishGithubRelease(context, version, releaseNotes, artifact)   
	{
		def REPO_NAME = Utilities.getRepoName(context)
		def REPO_ORG = Utilities.getOrgName(context)

        def platformLineSeparator = System.properties['line.separator']
        releaseNotes = releaseNotes.denormalize().replace(platformLineSeparator, '<br>')
        releaseNotes = releaseNotes.replace("\"", "^\"")

		context.bat "${context.env.Utils}\\github-release release --user $REPO_ORG --repo $REPO_NAME --tag v${version} --description \"${releaseNotes}\""
		context.bat "${context.env.Utils}\\github-release upload --user $REPO_ORG --repo $REPO_NAME --tag v${version} --name \"${artifact}\" --file \"${artifact}\""
		context.echo "uploaded to https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
		return "https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
	}

	def static getShouldPublish(context)
	{
		if (context.env.BRANCH_NAME == 'master') {
			return true
		}

		return false
	}

	def static getShouldStage(context)
	{
		if ((context.env.BRANCH_NAME == 'master' || context.env.BRANCH_NAME == 'dev') && !isDraft(context)) {
			return true
		}

		return false
	}

	def static isDraft(context)
	{
	    context.bat "\"${context.tool 'Git'}\" log -1 --pretty=%%B > LAST_COMMIT_MESSAGE"
		def git_last_commit = context.readFile('LAST_COMMIT_MESSAGE')
	    return git_last_commit.contains('[draft]')
	}

	def static updateModulesDefinitions(context, def directory, def module, def version)
	{
		context.dir(directory)
		{
			context.bat "\"${context.tool 'Git'}\" config user.email \"ci@virtocommerce.com\""
			context.bat "\"${context.tool 'Git'}\" config user.name \"Virto Jenkins\""
			/*
			if(!foundRecord)
				{
					bat "\"${tool 'Git'}\" commit -am \"Updated module ${id}\""
				}
				else
				{
					bat "\"${tool 'Git'}\" commit -am \"Added new module ${id}\""
				}
				*/
			context.bat "\"${context.tool 'Git'}\" commit -am \"${module} ${version}\""
			context.bat "\"${context.tool 'Git'}\" push origin HEAD:master -f"
		}
	}    

	def static installModule(context, path)
	{
        def moduleId = Modules.getModuleId(context)
        def platformContainer = Utilities.getPlatformContainer(context)
        Utilities.runSharedPS(context, "vc-install-module.ps1", "-apiurl \"${Utilities.getPlatformHost(context)}\" -moduleZipArchievePath \"${path}\" -moduleId \"${moduleId}\" -platformContainer ${platformContainer}")
	}   

    def static installTheme(context, path){
        def platformContainer = Utilities.getPlatformContainer(context)
        Utilities.runSharedPS(context, "vc-install-theme.ps1", "-themeZip \"${path}\" -platformContainer ${platformContainer}")
    }  

	def static publishThemePackage(context)
	{
		// find all manifests
		def inputFile = context.readFile file: 'package.json', encoding: 'utf-8'
		def json = Utilities.jsonParse(inputFile)

		def name = json.name;
		def version = json.version;

		def packagesDir = Utilities.getArtifactFolder(context)

		context.dir(packagesDir)
		{
			def artifacts = context.findFiles(glob: '*.zip')
			if (artifacts.size() > 0) {
				for (int i = 0; i < artifacts.size(); i++)
				{
					Packaging.publishGithubRelease(context, version, artifacts[i])
				}
			}	
		}
	}    

    def static createNugetPackages(context){
        String nugetFolder = "${context.env.WORKSPACE}\\NuGet"
        if(!(new File(nugetFolder).exists()))
            return

        def solutions = context.findFiles(glob: "**\\*.sln")
        for(solution in solutions){
            context.bat "\"${context.tool DefaultMSBuild}\" \"${solution.path}\" /nologo /verbosity:n /t:Build /p:Configuration=Release;Platform=\"Any CPU\""
        }

        Utilities.cleanNugetFolder(context)
        def nuspecs = context.findFiles glob: "**\\*.nuspec"
        def csprojs = []
        for (nuspec in nuspecs){
            def nuspecParent = new File(nuspec.path).getParent()
            def found = context.findFiles(glob: "**\\${nuspecParent}\\*.csproj")
            if(found){
                csprojs.addAll(found)
            }

        }
        context.dir(nugetFolder){
            for(csproj in csprojs){
                context.bat "${context.env.NUGET}\\nuget pack \"${context.env.WORKSPACE}\\${csproj.path}\" -IncludeReferencedProjects -Symbols -Properties Configuration=Release"
            }
            def nugets = context.findFiles(glob: "**\\*.nupkg")
            for(nuget in nugets){
                if(!nuget.name.contains("symbols")){
                    context.echo "publish nupkg: ${nuget.name}"
                    //context.bat "${context.env.NUGET}\\nuget push ${nuget.name} -Source nuget.org -ApiKey ${context.env.NUGET_KEY}"
                    Utilities.runSharedPS(context, "vc-publish-nuget.ps1", "-path \"${nuget.name}\"")
                }
            }
        }
    }

    def static saveArtifact(context, prefix, projectType, id, artifact){
        def destinationFolderPath = "${context.env.SOLUTION_FOLDER}\\${prefix}\\${context.env.BRANCH_NAME}\\${projectType}"
        switch(projectType){
            case ['module','theme']:
                destinationFolderPath = destinationFolderPath + "\\${id}"
                break
        }
        def destinationFolder = new File(destinationFolderPath)
        if(destinationFolder.exists()){
            context.dir(destinationFolderPath){
                context.deleteDir()
            }
        }
        context.powershell "Expand-Archive -Path ${artifact} -DestinationPath ${destinationFolderPath} -Force"
    }
}
