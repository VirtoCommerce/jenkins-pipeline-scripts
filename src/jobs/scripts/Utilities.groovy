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
}