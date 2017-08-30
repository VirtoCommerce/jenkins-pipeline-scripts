package jobs.scripts;

class Packaging {

    private static String DefaultBranchOrCommitPR = '${sha1}'
    private static String DefaultBranchOrCommitPush = '*/master'
    private static String DefaultRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
    private static String DefaultMSBuild = 'MSBuild 15.0'
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
        context.echo "Building docker image \"${dockerImageName}\" using \"${dockerContextFolder}\" as context folder"
        context.bat "copy \"..\\workspace@libs\\virto-shared-library\\resources\\docker\\${dockerFileFolder}\\Dockerfile\" \"${dockerContextFolder}\" /Y"
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
                context.bat "docker-compose rm -f"
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
        def tag = context.env.BUILD_TAG.replace("-", "").toLowerCase()
        context.echo "Checking ${tag}_${containerId}_1 state ..."
        String result = context.bat(returnStdout: true, script: "docker inspect -f {{.State.Running}} ${tag}_${containerId}_1").trim()

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
                context.bat "docker-compose stop"
                context.bat "docker-compose rm -f"
            }
        }
    }

    def static createSampleData(context)
    {
    	def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\azure\\vc-setup-sampledata.ps1\" -apiurl \"${Utilities.getPlatformHost(context)}\" -ErrorAction Stop"
    }

    def static installModules(context)
    {
    	def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\azure\\vc-setup-modules.ps1\" -apiurl \"${Utilities.getPlatformHost(context)}\" -ErrorAction Stop"
    }    

    def static pushDockerImage(context, dockerImage, String dockerTag)
    {
		context.withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'docker-hub-credentials', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
			context.sh "docker login --password=${context.PASSWORD} --username=${context.USERNAME}"
		}
		dockerImage.push(dockerTag)
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
        context.bat "\"${context.tool DefaultMSBuild}\" \"${webProject}\" /nologo /verbosity:m /p:Configuration=Release /p:Platform=\"Any CPU\" /p:DebugType=none \"/p:OutputPath=$tempFolder\""

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
		context.bat "Nuget restore ${solution}"
        context.bat "\"${context.tool DefaultMSBuild}\" \"${solution}\" /p:Configuration=Debug /p:Platform=\"Any CPU\" /m"        
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

    def static startAnalyzer(context)
    {
        def sqScannerMsBuildHome = context.tool 'Scanner for MSBuild'
        def fullJobName = Utilities.getRepoName(context)
        def coverageFolder = Utilities.getCoverageFolder(context)
        context.withSonarQubeEnv('VC Sonar Server') {
            // Due to SONARMSBRU-307 value of sonar.host.url and credentials should be passed on command line
            context.bat "\"${sqScannerMsBuildHome}\\SonarQube.Scanner.MSBuild.exe\" begin /d:\"sonar.branch=${context.env.BRANCH_NAME}\" /n:\"${fullJobName}\" /k:\"${fullJobName}\" /d:\"sonar.organization=virtocommerce\" /d:sonar.host.url=%SONAR_HOST_URL% /d:sonar.login=%SONAR_AUTH_TOKEN% /d:sonar.cs.vscoveragexml.reportsPaths=\"${coverageFolder}\\VisualStudio.Unit.coveragexml\""
        }        
    }

    def static endAnalyzer(context)
    {
        def sqScannerMsBuildHome = context.tool 'Scanner for MSBuild'
        def fullJobName = Utilities.getRepoName(context)
        context.withSonarQubeEnv('VC Sonar Server') {
            context.bat "\"${sqScannerMsBuildHome}\\SonarQube.Scanner.MSBuild.exe\" end"
        }        
    }

    def static runGulpBuild(context)
    {
        def packagesDir = Utilities.getArtifactFolder(context)

        context.dir(packagesDir)
        {
            context.deleteDir()
        }        
        context.bat "npm install"
        context.bat "node node_modules\\gulp\\bin\\gulp.js compress"
    }    

    def static runUnitTests(context, tests)
    {        
        String paths = ""
        for(int i = 0; i < tests.size(); i++)
        {
            def test = tests[i]
            paths += "\"$test.path\" "
        }

        Utilities.runUnitTest(context, "-trait \"category=ci\" -trait \"category=Unit\"", paths, "xUnit.UnitTests.xml")
   }

    def static publishRelease(context, version)
    {
        def tempFolder = Utilities.getTempFolder(context)
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

    def static publishGithubRelease(context, version, artifact)   
    {
        def REPO_NAME = Utilities.getRepoName(context)
        def REPO_ORG = Utilities.getOrgName(context)

        context.bat "${context.env.Utils}\\github-release release --user $REPO_ORG --repo $REPO_NAME --tag v${version}"
        context.bat "${context.env.Utils}\\github-release upload --user $REPO_ORG --repo $REPO_NAME --tag v${version} --name \"${artifact}\" --file \"${artifact}\""
        context.echo "uploaded to https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
        return "https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
    }

    def static getShouldPublish(context)
    {
        /*
		context.bat "\"${context.tool 'Git'}\" log -1 --pretty=%%B > LAST_COMMIT_MESSAGE"
		def git_last_commit = context.readFile('LAST_COMMIT_MESSAGE')			
        */

		if (context.env.BRANCH_NAME == 'master' /* && git_last_commit.contains('[publish]')*/) {
			return true
		}

        return false
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
        def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\azure\\vc-install-module.ps1\" -apiurl \"${Utilities.getPlatformHost(context)}\" -moduleZipArchievePath \"${path}\" -ErrorAction Stop"
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
}