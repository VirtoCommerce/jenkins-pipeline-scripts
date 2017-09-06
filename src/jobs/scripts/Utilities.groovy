package jobs.scripts;

class Utilities {

    private static String DefaultSharedLibName = 'virto-shared-library'
    private static String DefaultAdminDockerPrefix = 'http://ci.virtocommerce.com'
    private static Integer DefaultPlatformPort = 8090
    private static Integer DefaultStorefrontPort = 8080
    private static Integer DefaultSqlPort = 1433    

    /**
     * Get the folder name for a job.
     *
     * @param project Project name (e.g. dotnet/coreclr)
     * @return Folder name for the project. Typically project name with / turned to _
     */
    def static getFolderName(String project) {
        return project.replace('/', '_')
    }

    def static getRepoName(context)
    {
        def tokens = "${context.env.JOB_NAME}".tokenize('/')
        def REPO_NAME = tokens[1]
        return REPO_NAME
    }

    def static getOrgName(context)
    {
        return "VirtoCommerce"
    }

    def static runSharedPS(context, scriptName, args = '')
    {
    	def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\${scriptName}\" ${args} -ErrorAction Stop"
    }

    def static getAssemblyVersion(context)
    {
        context.echo "Searching for version inside CommonAssemblyInfo.cs file"
        def matcher = context.readFile('CommonAssemblyInfo.cs') =~ /AssemblyFileVersion\(\"(\d+\.\d+\.\d+)/
        def version = matcher[0][1]
        context.echo "Found version ${version}"
        return version
    }

    def static getTestDlls(context)
    {
        def testDlls = context.findFiles(glob: '**\\bin\\Debug\\*Test.dll')
        return testDlls
    }

    def static getComposeFolder(context)
    {
        def wsFolder = context.pwd()
        def composeDir = "$wsFolder\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\docker"
        return composeDir
    }    

    def static getArtifactFolder(context)
    {
        def wsFolder = context.pwd()
        def packagesDir = "$wsFolder\\artifacts"
        return packagesDir
    }

    def static getCoverageFolder(context)
    {
        def wsFolder = Utilities.getTempFolder(context)
        def packagesDir = "$wsFolder\\.coverage"
        return packagesDir
    }    

    def static getWebPublishFolder(context, String websiteDir)
    {
        def tempFolder = Utilities.getTempFolder(context)
        def websitePath = "$tempFolder\\_PublishedWebsites\\${websiteDir}"
        return websitePath
    }

    def static getPlatformHost(context)
    {
        return "${DefaultAdminDockerPrefix}:${getPlatformPort(context)}"
    }

    def static getTempFolder(context)
    {
        def tempFolder = context.pwd(tmp: true)
        return tempFolder
    }

    def static notifyBuildStatus(context, status)
    {
        context.office365ConnectorSend status:context.currentBuild.result, webhookUrl:context.env.O365_WEBHOOK
    }    

    def static getPlatformPort(context)
    {
        return DefaultPlatformPort.toInteger() + context.env.VC_BUILD_ORDER.toInteger();
    }

    def static getStorefrontPort(context)
    {
        return DefaultStorefrontPort.toInteger() + context.env.VC_BUILD_ORDER.toInteger();
    }    

    def static getSqlPort(context)
    {
        return DefaultSqlPort.toInteger() + context.env.VC_BUILD_ORDER.toInteger();
    }

    def static getNextBuildOrderExecutor(context)
    {
        return context.env.EXECUTOR_NUMBER;
    }

    def static getNextBuildOrder(context)
    {
        def instance = Jenkins.getInstance()
        def globalNodeProperties = instance.getGlobalNodeProperties()
        def envVarsNodePropertyList = globalNodeProperties.getAll(hudson.slaves.EnvironmentVariablesNodeProperty.class)

        def newEnvVarsNodeProperty = null
        def envVars = null

        if ( envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0 ) {
            newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();
            globalNodeProperties.add(newEnvVarsNodeProperty)
            envVars = newEnvVarsNodeProperty.getEnvVars()
        } else {
            envVars = envVarsNodePropertyList.get(0).getEnvVars()
        }

        def tempCurrentOrder = envVars.get("VC_BUILD_ORDER")    
        def currentOrder = 0

        if(tempCurrentOrder) // exists
        {
            currentOrder = tempCurrentOrder.toInteger() + 1
            
            if(currentOrder >= 10) // reset, we can't have more than 10 builders at the same time
            {
                currentOrder = 0
            }
        }

        envVars.put("VC_BUILD_ORDER", currentOrder.toString())
        instance.save()

        // save in current context
        context.env.VC_BUILD_ORDER = currentOrder

        return currentOrder
    }

    Object withDockerCredentials(Closure body) {
        withCredentials([[$class: 'ZipFileBinding', credentialsId: 'docker-hub-credentials', variable: 'DOCKER_CONFIG']]) {
            return body.call()
        }
    }    

    def static runUnitTest(context, traits, paths, resultsFileName)
    {
        def xUnitExecutable = "${context.env.XUnit}\\xunit.console.exe"
        def coverageExecutable = "${context.env.CodeCoverage}\\CodeCoverage.exe"
        def coverageFolder = Utilities.getCoverageFolder(context)

        // remove old folder
        context.dir(coverageFolder)
        {
            context.deleteDir()
        }        

        // recreate it now
        File folder = new File(coverageFolder); 
        if (!folder.mkdir()) { 
            throw new Exception("can't create coverage folder: " + coverageFolder); 
        } 

        context.bat "\"${coverageExecutable}\" collect /output:\"${coverageFolder}\\VisualStudio.Unit.coverage\" \"${xUnitExecutable}\" ${paths} -xml \"${resultsFileName}\" ${traits} -parallel none"
        context.bat "\"${coverageExecutable}\" analyze /output:\"${coverageFolder}\\VisualStudio.Unit.coveragexml\" \"${coverageFolder}\\VisualStudio.Unit.coverage\""
        context.step([$class: 'XUnitPublisher', testTimeMargin: '3000', thresholdMode: 1, thresholds: [[$class: 'FailedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: ''], [$class: 'SkippedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']], tools: [[$class: 'XUnitDotNetTestType', deleteOutputFiles: true, failIfNotNew: false, pattern: resultsFileName, skipNoTestFiles: true, stopProcessingIfError: false]]])
    }

    def static checkAndAbortBuild(context)
    {
		if(!Utilities.getShouldBuild(context))
		{
			Utilities.abortBuild(context)
		}
    }

    def static getShouldBuild(context)
    {
        String result = context.bat(returnStdout: true, script: "\"${context.tool 'Git'}\" log -1 --pretty=\"format:\" --name-only").trim()        
        def lines = result.split("\r?\n")

        //context.echo "size: ${lines.size()}, 2:${lines[1]}"
        if(lines.size() == 2 && lines[1].equalsIgnoreCase('readme.md'))
        {
            context.echo "Found only change to readme.md file, so build should be aborted."
            return false
        }

        return true
    }    

	def static getStagingNameFromBranchName(context){
	    def stagingName = ""
		if (context.env.BRANCH_NAME == 'dev')
		{
			stagingName = "dev"
		}
		if (context.env.BRANCH_NAME == 'master')
		{
			stagingName = "qa"
		}
		return stagingName
	}

    @NonCPS
    def static jsonParse(def json) {
        new groovy.json.JsonSlurperClassic().parseText(json)
    }    

    @NonCPS
    def static abortBuildIfTriggeredByJenkins(context) {
        def validChangeDetected = false
        def changeLogSets = context.currentBuild.changeSets
        for (int i = 0; i < changeLogSets.size(); i++) {
            def entries = changeLogSets[i].items
            for (int j = 0; j < entries.length; j++) {
                def entry = entries[j]
                if(!entry.msg.matches("\\[ci-skip\\].*")){
                    validChangeDetected = true
                    println "Found commit by ${entry.author}"
                }
            }
        }
        // We are building if there are some walid changes or if there are no changes(so the build was triggered intentionally or it is the first run.)
        if(!validChangeDetected && changeLogSets.size() != 0) {
            context.currentBuild.setResult(context.currentBuild.rawBuild.getPreviousBuild()?.result?.toString())
            context.error("Stopping current build")
        }
    }    

    @NonCPS
    def static abortBuild(context) {
        def validChangeDetected = false

        def changeLogSets = context.currentBuild.changeSets

        // We are building if there are some walid changes or if there are no changes(so the build was triggered intentionally or it is the first run.)
        if(changeLogSets.size() != 0) {
            context.echo "Aborting build and setting result to ${context.currentBuild.rawBuild.getPreviousBuild()?.result?.toString()}"
            context.currentBuild.setResult(context.currentBuild.rawBuild.getPreviousBuild()?.result?.toString())
            //context.echo "current build aborted"
            //context.error("Stopping current build")
            return true
        }
    }    
}
