package jobs.scripts;

class Packaging {

    private static String DefaultBranchOrCommitPR = '${sha1}'
    private static String DefaultBranchOrCommitPush = '*/master'
    private static String DefaultRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
    private static String DefaultMSBuild = 'MSBuild 15.0'

    /*
    private def Context;

    Packaging(script) {
        Context = script;
    }
    */

    /**
     * Creates new docker image
     *
     * @param jobName Base name of the job
     * @param isPR True if PR job, false otherwise
     * @param folder (optional) If folder is specified, project is not used as the folder name
     * @return Full job name.  If folder prefix is specified,
     */
    def static createDockerImage(context, dockerImageName, dockerContextFolder, version) {
        context.echo "Building docker image \"${dockerImageName}\" using \"${folder}\" folder"
        def dockerImage = context.docker.build("${dockerImageName}:${version}".toLowerCase(), "-f Dockerfile --build-arg SOURCE=\".\" \"${dockerContextFolder}\"")
        return "";//getFullJobName('', jobName, isPR, folder);
    }

    def static createReleaseArtifact(context, version, webProject, zipArtifact, websiteDir)
    {
        context.echo "Preparing release for ${version}"
        def tempFolder = context.pwd(tmp: true)
        def wsFolder = context.pwd()
        def websitePath = "$tempFolder\\_PublishedWebsites\\$websiteDir"
        def packagesDir = "$wsFolder\\artifacts"

        context.dir(packagesDir)
        {
            context.deleteDir()
        }

        // create artifacts
        context.bat "\"${context.tool DefaultMSBuild}\" \"${webProject}\" /nologo /verbosity:m /p:Configuration=Release /p:Platform=\"Any CPU\" /p:DebugType=none \"/p:OutputPath=$tempFolder\""

        (new AntBuilder()).zip(destfile: "${packagesDir}\\${zipArtifact}.${version}.zip", basedir: "${websitePath}")

        // create docker image
        Packaging.createDockerImage(context, "${zipArtifact}", websitePath, version)
    }

    def static runBuild(context, solution)
    {
		context.bat "Nuget restore ${solution}"
		context.bat "\"${context.tool DefaultMSBuild}\" \"${solution}\" /p:Configuration=Debug /p:Platform=\"Any CPU\""        
    }
    
    def static runUnitTests(context, tests)
    {
        def xUnit = context.env.XUnit
        def xUnitExecutable = "${xUnit}\\xunit.console.exe"
        
        String paths = ""
        for(int i = 0; i < tests.size(); i++)
        {
            def test = tests[i]
            paths += "\"$test.path\" "
        }
                
        context.bat "${xUnitExecutable} ${paths} -xml xUnit.Test.xml -trait \"category=ci\" -parallel none"
        context.step([$class: 'XUnitPublisher', testTimeMargin: '3000', thresholdMode: 1, thresholds: [[$class: 'FailedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: ''], [$class: 'SkippedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']], tools: [[$class: 'XUnitDotNetTestType', deleteOutputFiles: true, failIfNotNew: false, pattern: '*.xml', skipNoTestFiles: true, stopProcessingIfError: false]]])
    }

    def static publishRelease(context, version)
    {
        def tokens = "${context.env.JOB_NAME}".tokenize('/')
        def REPO_NAME = tokens[1]
        def REPO_ORG = "VirtoCommerce"

        def tempFolder = context.pwd(tmp: true)
        def wsFolder = context.pwd()
        def packagesDir = "$wsFolder\\artifacts"

        context.dir(packagesDir)
        {
            def artifacts = context.findFiles(glob: '*.zip')
            if (artifacts.size() > 0) {
                for (int i = 0; i < artifacts.size(); i++)
                {
                    def artifact = artifacts[i]
                    context.bat "${context.env.Utils}\\github-release release --user $REPO_ORG --repo $REPO_NAME --tag v${version}"
                    context.bat "${context.env.Utils}\\github-release upload --user $REPO_ORG --repo $REPO_NAME --tag v${version} --name \"${artifact}\" --file \"${artifact}\""
                    context.echo "uploaded to https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
                    return "https://github.com/$REPO_ORG/$REPO_NAME/releases/download/v${version}/${artifact}"
                }
            }
        }
    }    

    def static getShouldPublish(context)
    {
		context.bat "\"${context.tool 'Git'}\" log -1 --pretty=%%B > LAST_COMMIT_MESSAGE"
		def git_last_commit = context.readFile('LAST_COMMIT_MESSAGE')			

		if (context.env.BRANCH_NAME == 'master' && git_last_commit.contains('[publish]')) {
			return true
		}

        return false
    }    
}