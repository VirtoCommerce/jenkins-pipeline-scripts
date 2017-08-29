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

    def stat getOrgName(context)
    {
        return "VirtoCommerce"
    }

    def static runSharedPS(context, scriptName)
    {
    	def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\${scriptName}\""
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

    def static getNextBuildOrder(context)
    {
        return context.env.EXECUTOR_NUMBER;
    }

    def static getNextBuildOrderFromSettings(context)
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

    @NonCPS
    def static jsonParse(def json) {
        new groovy.json.JsonSlurperClassic().parseText(json)
    }    
}