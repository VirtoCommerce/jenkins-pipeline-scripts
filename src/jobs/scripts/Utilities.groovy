package jobs.scripts;

import groovy.io.FileType

class Utilities {
    private static String DefaultSharedLibName = 'virto-shared-library'
    private static String DefaultAdminDockerPrefix = 'http://localhost'
    private static String DefaultPlatformCoreDockerPrefix = 'http://localhost'
    private static String DefaultOrgName = 'VirtoCommerce'
    private static Integer DefaultPlatformPort = 9101
    private static Integer DefaultStorefrontPort = 9081
    private static Integer DefaultSqlPort = 1434 

    def static getOrgName(context){
        return DefaultOrgName
    }
    def static getRepoName(context)
    {
        def tokens = "${context.env.JOB_NAME}".tokenize('/')
        def REPO_NAME = tokens[1]
        return REPO_NAME
    }
    def static getRepoNamePrefix(context){
        def repoName = getRepoName(context)
        def tokens = repoName.tokenize("-")
        return tokens[0]
    }
    def static getProjectType(context){
        def repoName = getRepoName(context)
        def tokens = repoName.tokenize("-")
        return tokens[1]
    }

    def static runSharedPS(context, scriptName, args = '')
    {
        context.bat script: "powershell.exe -File \"${context.env.WORKSPACE}\\..\\workspace@libs\\${DefaultSharedLibName}\\resources\\azure\\${scriptName}\" ${args} -ErrorAction Stop".replaceAll('%', '%%'), label: scriptName
    }
    def static runPS(context, scriptName, args = '')
    {
        def scriptRoot = "${context.env.WORKSPACE}/../workspace@libs/${DefaultSharedLibName}/resources/"
        if(context.env.NODE_NAME != 'master')
            scriptRoot = "${context.env.WORKSPACE}/resources/"
        context.pwsh script: "${scriptRoot}${scriptName} ${args} -ErrorAction Stop", label: scriptName
    }
    def static setSharedLibName(name){
        DefaultSharedLibName = name
    }

    def static runGlobalScript(context, scriptName, args = ''){
        def scriptsRoot = "scripts"
        def scriptContent = context.libraryResource("${scriptsRoot}\\${scriptName}")
        def tmpScriptFile = "${context.env.WORKSPACE}@tmp\\scripts\\${scriptName}"
        context.writeFile file: tmpScriptFile, text: scriptContent
        context.powershell script: "${tmpScriptFile} ${args}", label: scriptName
        new File(tmpScriptFile).delete()
    }

    def static getAssemblyVersion(context, projectFile)
    {
        if(context.projectType == 'NETCORE2')
        {
            context.echo "Reading $projectFile file"

            def wsDir = context.pwd()
            def fullManifestPath = "$wsDir\\$projectFile"
            def manifest = new XmlSlurper().parse(fullManifestPath)

            def version = manifest.PropertyGroup.Version.toString()
            context.echo "Found version ${version}"
            return version
        }
        else
        {
            context.echo "Searching for version inside CommonAssemblyInfo.cs file"
            def matcher = context.readFile('CommonAssemblyInfo.cs') =~ /AssemblyFileVersion\(\"(\d+\.\d+\.\d+)/
            def version = matcher[0][1]
            context.echo "Found version ${version}"
            return version
        }
    }

    def static getPackageVersion(context)
    {
        context.echo "Searching for version inside package.json file"
        def inputFile = context.readFile('package.json')
        def json = jsonParse(inputFile)

        def version = json.version
        context.echo "Found version ${version}"
        return version
    }

    def static getReleaseNotes(context, projectFile)
    {
        if(context.projectType == 'NETCORE2')
        {
            context.echo "Reading $projectFile file"

            def wsDir = context.pwd()
            def fullManifestPath = "$wsDir\\$projectFile"
            def manifest = new XmlSlurper().parse(fullManifestPath)

            def notes = manifest.PropertyGroup.PackageReleaseNotes.toString()
            context.echo "Found notes ${notes}"
            return notes
        }
        else
        {
            return ""
        }
    }    

    def static getTestDlls(context)
    {
        String pattern = '**\\bin\\Debug\\*Test.dll'
        if(isNetCore(context.projectType))
            pattern = '**\\bin\\Debug\\*\\*Tests.dll'
        def testDlls = context.findFiles(glob: pattern)
        return testDlls
    }

    def static getComposeFolder(context)
    {
        def wsFolder = context.env.WORKSPACE
		def composeDir = "$wsFolder\\..\\workspace@libs\\${DefaultSharedLibName}\\resources"
		if(context.projectType == 'NETCORE2') {
		    composeDir = "$composeDir\\docker.core\\windowsnano"
        } else {		   
		    composeDir = "$composeDir\\docker"
		}
        return composeDir
    }

    def static getComposeFolderV3(context)
    {
        def wsFolder = context.env.WORKSPACE
        def composeDir = "$wsFolder\\..\\workspace@libs\\${DefaultSharedLibName}\\resources"
        composeDir = "$composeDir\\docker_v3"
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
        def tempFolder = getTempFolder(context)
        def websitePath = "${tempFolder}\\_PublishedWebsites\\${websiteDir}"
        return websitePath 
    }

    def static getPlatformHost(context)
    {
        return "${DefaultAdminDockerPrefix}:${getPlatformPort(context)}"
    }
    def static getPlatformCoreHost(context)
    {
        return "${DefaultPlatformCoreDockerPrefix}:${getPlatformPort(context)}"
    }

    def static getTempFolder(context)
    {
        def tempFolder = context.pwd(tmp: true)
        return tempFolder
    }

    def static notifyBuildStatus(context, webHook, message='', status = '')
    {
        def warnings = context.env.WARNINGS ? "WARNINGS:<br/>${context.env.WARNINGS}" : ''
        if(message != '')
            message = "${message}<br/>${warnings}"
        def color
        switch(status) {
            case 'STARTED':
                color = '428bca'
                break
            case 'SUCCESS':
                color = '00e60c'
                break
            case 'E2E Success':
                color = '00e60c'
                break
            case 'UNSTABLE':
                color = 'e6e600'
                break
            case 'FAILURE':
                color = 'e60000'
                break
            case 'E2E Failed':
                color = 'e60000'
                break
            default:
                color = 'e60000'
                break
        }
        context.echo "Status is: ${status}; color: ${color}"
        context.office365ConnectorSend message: message, status:status, webhookUrl:webHook, color: color
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
            
            if(currentOrder >= 20) // reset, we can't have more than 10 builders at the same time
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
        def coverageExecutable = "${context.env.CodeCoverage}\\CodeCoverage.exe"
        def coverageFolder = Utilities.getCoverageFolder(context)

        // remove old folder
        context.dir(coverageFolder)
        {
            context.deleteDir()
        }   

        if(paths.size() < 1)
        {
            return
        }     

        // recreate it now
        File folder = new File(coverageFolder); 
        if (!folder.mkdir()) { 
            throw new Exception("can't create coverage folder: " + coverageFolder); 
        } 

        def oldStyle = Utilities.getProjectType(context) == 'storefront' ? '-oldStyle' : ''
            
        def pdbDirs = getPDBDirsStr(context)
        if(isNetCore(context.projectType)){
            context.powershell "${context.env.OPENCOVER}\\opencover.console.exe --% -returntargetcode ${oldStyle} -searchdirs:\"${pdbDirs}\" -register:user -filter:\"+[Virto*]* -[xunit*]*\" -output:\"${coverageFolder}\\VisualStudio.Unit.coveragexml\" -target:\"${context.env.DOTNET_PATH}\\dotnet.exe\" -targetargs:\"vstest ${paths} /TestCaseFilter:(${traits}) /Enablecodecoverage\""
        }
        else{
            context.powershell "${context.env.OPENCOVER}\\opencover.console.exe --% -returntargetcode ${oldStyle} -searchdirs:\"${pdbDirs}\" -register:user -filter:\"+[*]* -[Moq]* -[xunit*]* -[Common.*]*\" -output:\"${coverageFolder}\\VisualStudio.Unit.coveragexml\" -target:\"${context.env.DOTNET_PATH}\\dotnet.exe\" -targetargs:\"vstest ${paths} /TestCaseFilter:(${traits}) /Enablecodecoverage\""
        }
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

    @NonCPS
    def static getPDBDirs(context){
        def pdbDirs = []
        def currentDir = new File(context.pwd())
        currentDir.eachDirRecurse(){ dir->
            if(dir.getPath() =~ /.*\\bin/ && !dir.getPath().contains("runtimes"))
                pdbDirs << dir.path
        }
        return pdbDirs
    }
    def static getPDBDirsStr(context)
    {
        return getPDBDirs(context).join(';')
    }
    def static isNetCore(projectType){
        return projectType == 'NETCORE2'
    }

    def static getWebApiDll(context){
        String swagPaths = ""
        def swagDlls = context.findFiles(glob: "**\\bin\\*.Web.dll")
        if(swagDlls.size() > 0)
        {
            for(swagDll in swagDlls){
                if(!swagDll.path.contains("VirtoCommerce.Platform.Core.Web.dll"))
                    swagPaths += "\"$swagDll.path\""
            }
        }
        return swagPaths
    }

    def static validateSwagger(context, schemaPath)
    {
        Packaging.createSwaggerSchema(context, schemaPath)

        def schemaFile = new File(schemaPath)
        if(schemaFile.exists() && schemaFile.length()>500){
            context.bat "node.exe ${context.env.NODE_MODULES}\\swagger-cli\\bin\\swagger-cli.js validate ${schemaPath}"
        }
    }

    def static getFailedStageStr(logArray)
    {
        def log = logArray
        def startIndex = 30
        def i = 1
        for(logRow in log.reverse()){
            if(logRow =~ /\{\s\(.*\)/) {
                startIndex = i
                break
            }
            ++i
        }
        def result = logArray[logArray.size() - startIndex..-1].join("\n")
        return result
    }
    def static getFailedStageName(logText){
        def res = logText =~/(?ms).*\{\s\((.*)\).*/
        def name = ''
		if(res.matches())
			name = res.group(1)
		else
			name = 'Not found'
        return name
    }
    def static getMailBody(context, stageName, stageLog)
    {
        def result = "Failed Stage: ${stageName}\n${context.env.JOB_URL}\n\n\n${stageLog}"
        return result
    }

    def static getPlatformContainer(context)
    {
        def tag = context.env.BUILD_TAG.toLowerCase()
        tag = tag.replaceAll("\\.", '').replaceAll('%', '')
        if(context.isUnix())
        {
            tag = tag.replaceAll("-", "")
        }
        def containerId = 'vc-platform-web'
        return "${tag}_${containerId}_1"
    }
    def static getStorefrontContainer(context){
        def tag = context.env.BUILD_TAG.toLowerCase()
        tag = tag.replaceAll("%", '')
        tag = tag.replaceAll("\\.", '')
        if(context.isUnix())
        {
            tag = tag.replaceAll("-", "")
        }
        def containerId = 'vc-storefront-web'
        return "${tag}_${containerId}_1"
    }

    @NonCPS
    def static cleanNugetFolder(context)
    {
        String folderPath = "${context.pwd()}\\NuGet"
        new File(folderPath).eachFile (FileType.FILES) { file ->
            context.echo "found file: ${file.name}"
            if (file.name.contains('nupkg')) {
                context.echo "remove ${file.name}"
                file.delete()
            }
        }
    }

    def static getE2EDir(context)
    {
        def tmp = Utilities.getTempFolder(context)
		return "${tmp}\\e2e"
    }


    def static getE2ETests(context, repoUrl, branchName)
    {
        //context.git credentialsId: 'github', url: 'https://github.com/VirtoCommerce/vc-platform-qg.git'
        context.git credentialsId: 'github', url: repoUrl, branch: branchName
    }

    def static runE2E(context, e2eDir, config)
    {
        context.dir(e2eDir){
            context.deleteDir()
            getE2ETests(context)
            def sfPort = getStorefrontPort(context)
            def DefaultAdminDockerPrefix = "https://vc-public-test.azurewebsites.net/"
            def autoPilot = "https://api2.autopilothq.com/"
            def allureResultsPath = "${context.env.WORKSPACE}\\allure-results"
            def allureReportPath = "${context.env.WORKSPACE}\\allure-report"
            context.dir(allureReportPath){
                context.deleteDir()
            }
            def allureResultsEsc = allureResultsPath.replace("\\", "\\\\")
            def jsonConf = config//"{\\\"output\\\":\\\"${allureResultsEsc}\\\",\\\"helpers\\\":{\\\"REST\\\":{\\\"endpoint\\\":\\\"${autoPilot}\\\"},\\\"WebDriver\\\":{\\\"url\\\":\\\"${DefaultAdminDockerPrefix}\\\"}}}"
            context.withEnv(["vcapikey=${context.env.vcapikey_e2e}"]){
                context.bat "${context.env.NODE_MODULES}\\.bin\\codeceptjs.cmd run -o \"${jsonConf}\""
            }
        }
    }
    def static generateAllureReport(context)
    {
        context.allure includeProperties: false, jdk: '', results: [[path: "./../workspace@tmp/output"]]
    }

    def static isPullRequest(context)
    {
        return context.env.BRANCH_NAME.startsWith("PR-")
    }
    def static getPullRequestNumber(context)
    {
        def number = context.env.BRANCH_NAME.replace('PR-', '')
        return number
    }

    
    @NonCPS
    def getUserCause()
    {
        for (cause in currentBuild.rawBuild.getCauses())
        {
            if (cause instanceof Cause.UserIdCause) {
                return cause.getUserName()
            }
        }
    }

    def static getRepoURL(context) {
        context.bat "git config --get remote.origin.url > .git/remote-url"
        return context.readFile(".git/remote-url").trim()
    }
    
    def static getCommitSha(context) {
        context.bat "git rev-parse HEAD > .git/current-commit"
        return context.readFile(".git/current-commit").trim()
    }
    
    def static updateGithubCommitStatus(context, state, message) {
        // workaround https://issues.jenkins-ci.org/browse/JENKINS-38674
        def repoUrl = getRepoURL(context)
        def commitSha = getCommitSha(context)
        
        context.step([
            $class: 'GitHubCommitStatusSetter',
            reposSource: [$class: "ManuallyEnteredRepositorySource", url: repoUrl],
            commitShaSource: [$class: "ManuallyEnteredShaSource", sha: commitSha],
            errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
            statusResultSource: [
            $class: 'ConditionalStatusResultSource',
            results: [
                    [$class: 'AnyBuildResult', message: message, state: state]
                ]
            ]
        ])
    }

    def static cleanPRFolder(context)
    {
        if(Utilities.isPullRequest(context)){
            context.cleanWs notFailBuild: true
        }
    }

    def static runBatchScript(context, command)
    {
        def outFile = "log${context.env.BUILD_NUMBER}.out"
        def exitCode = context.bat script: "${command} > ${outFile}", returnStatus:true, label: command
        def res = [:]
        def fileContent = context.readFile(outFile).trim()
        context.echo fileContent
        res["status"] = exitCode
        res["stdout"] = fileContent.split("\n")
        context.bat "del ${outFile} /F /Q"
        return res
    }

    def static checkLogForWarnings(context)
    {
        if(context.env['WARNINGS'] && context.currentBuild.result != 'FAILED'){
            context.currentBuild.result = 'UNSTABLE'
            context.echo "WARNINGS:\n${context.env.WARNINGS}"
        }
    }

    @NonCPS
    def static getSubfolders(path)
    {
        def  dirsl = []
        new File(path).eachDir()
            {
            dirs ->
                if (!dirs.getName().startsWith('.')) {
                    dirsl.add(dirs.getName())
                }
            }
        return dirsl
    }

    def static checkReleaseVersion(context, version)
    {
        if(Utilities.isReleaseBranch(context)){
            def ghAssetNameStartsWith = "v"
            def latestAsset = GithubRelease.getLatestGithubRelease(context, Utilities.getOrgName(context), Utilities.getRepoName(context), ghAssetNameStartsWith)
            def versionOnGithub = latestAsset.tag_name.trim().replace(ghAssetNameStartsWith, '')
            context.echo "version: ${version}, version on github ${versionOnGithub}"
            if(version == versionOnGithub){
                throw new Exception("Version: ${version} already exists on github")
            }
        }
    }

    def static getRepoChanges(context)
    {
        String result = context.bat (returnStdout: true, script: "@\"${context.tool 'Git'}\" log -1 --pretty=\"format:\" --name-only", label: "git log -1").trim()
        def lines = result.split("\r?\n")
        return lines as String[]
    }
    def static areThereCodeChanges(context)
    {
        def changes = Utilities.getRepoChanges(context)
        def result = false
        for(change in changes)
        {
            if(!change.startsWith('docs/') && !change.startsWith('build/') && !change.equalsIgnoreCase('readme.md'))
            {
                result = true
            }
        }
        return result
    }
    def static createInfrastructure(context, project = 'blank')
    {
        //def AzureTempDir = Utilities.getAzureTemplateDir(context)
        def AzureTempDir = "..\\workspace@libs\\virto-shared-library\\resources\\azure\\arm-templates" // Azure Resource Manager templates path
        context.dir(AzureTempDir)
        {
            switch(project)
            {
                case 'DEV-VC':
                    Utilities.runSharedPS(context, "arm-templates//vc-CreateInfrastructureDEV-VC.ps1")
                    break
                case 'DEV-demoVC':
                    Utilities.runSharedPS(context, "arm-templates//vc-CreateInfrastructureDEV-demoVC.ps1")
                    break
                case 'QA-demoVC':
                    Utilities.runSharedPS(context, "arm-templates//vc-CreateInfrastructureQA-demoVC.ps1")
                    break
                case 'DEV-demoVC-3':
                    Utilities.runSharedPS(context, "arm-templates//vc-CreateInfrastructureDEV-demoVC-3.ps1")
                    break
                case 'QA-demoVC-3':
                    Utilities.runSharedPS(context, "arm-templates//vc-CreateInfrastructureQA-demoVC-3.ps1")
                    break
                default:
                    context.echo "No matches found"
            }
        }
    }
    @NonCPS
    def static cleanReleaseNotes(context, text)
    {
        def jiraTasksRegex = /(?m)^#*[A-Z]{2,5}-\d{2,4}:{0,1}\s*/
        def mergeRegex = /(?m)^Merge.*$/
        def result = text.replaceAll(jiraTasksRegex, "").replaceAll(mergeRegex, "")
        return result
    }
    def static getReleaseNotesFromCommits(context, since)
    {
        def gitOut = context.pwsh(script: "git log --pretty=format:\"%s (%h)\" --since=\"${since}\"", returnStdout: true).trim()
        return cleanReleaseNotes(context, gitOut).replaceAll("\r", "").replaceAll("\n", "<br />").replaceAll("\"", "").replaceAll("<br /><br />", "<br />")
    }

    def static getCommitNumber(context)
    {
        def result = context.pwsh(script: "git rev-list --count ${context.env.BRANCH_NAME}", returnStdout: true).trim()
        if(!result.isNumber())
        {
            throw new Exception("Wrong commit number!")
        }
        return result
    }
    def static getCommitHash(context)
    {
        if(Utilities.isPullRequest(context))
        {
            return null
        }
        def result = context.pwsh(script: "git log --pretty=format:\"%h\" -1", returnStdout: true).trim()
        return result
    }

    def static getReleaseBranch(context)
    {
        return context.env.BRANCH_NAME
    }
}