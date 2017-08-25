package jobs.scripts;

class Modules {

    private static String DefaultBranchOrCommitPR = '${sha1}'
    private static String DefaultBranchOrCommitPush = '*/master'
    private static String DefaultRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
    private static String DefaultMSBuild = 'MSBuild 15.0'

    def static createModuleArtifact(context, def manifestDirectory)
    {
        def tempDir = Utilities.getTempFolder(context)
        def modulesDir = "$tempDir\\_PublishedWebsites"
        def packagesDir = Utilities.getArtifactFolder(context)

        context.dir(packagesDir)
        {
            context.deleteDir()
        }

        // create artifacts
        context.dir(manifestDirectory)
        {
            def projects = context.findFiles(glob: '*.csproj')
            if (projects.size() > 0) {
                for (int i = 0; i < projects.size(); i++)
                {
                    def project = projects[i]
                    context.bat "\"${context.tool DefaultMSBuild}\" \"$project.name\" /nologo /verbosity:m /t:Clean,PackModule /p:Configuration=Release /p:Platform=AnyCPU /p:DebugType=none /p:AllowedReferenceRelatedFileExtensions=.xml \"/p:OutputPath=$tempDir\" \"/p:VCModulesOutputDir=$modulesDir\" \"/p:VCModulesZipDir=$packagesDir\""
                }
            }
        }
    }

    def static installModuleArtifacts(context)
    {
        def packagesDir = Utilities.getArtifactFolder(context)
        context.dir(packagesDir)
        {
            def packages = context.findFiles(glob: '*.zip')

            if (packages.size() > 0) {
                for (int i = 0; i < packages.size(); i++)
                {
                    Packaging.installModule(context, packages[i].path)
                }
            }
        }
    }
}