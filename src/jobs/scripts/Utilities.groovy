package jobs.scripts;

class Utilities {

    private static String DefaultSharedLibName = 'virto-shared-library'

    /**
     * Get the folder name for a job.
     *
     * @param project Project name (e.g. dotnet/coreclr)
     * @return Folder name for the project. Typically project name with / turned to _
     */
    def static getFolderName(String project) {
        return project.replace('/', '_')
    }

    def static runSharedPS(context, scriptName)
    {
    	def wsFolder = context.pwd()
 	    context.bat "powershell.exe -File \"${wsFolder}\\..\\workspace@libs\\${DefaultSharedLibName}\\vars\\${scriptName}\""
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

    def static getTempFolder(context)
    {
        def tempFolder = context.pwd(tmp: true)
        return tempFolder
    }
}