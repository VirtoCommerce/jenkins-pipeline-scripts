package jobs.scripts;

class Modules {

    private static String DefaultBranchOrCommitPR = '${sha1}'
    private static String DefaultBranchOrCommitPush = '*/master'
    private static String DefaultRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
    private static String DefaultMSBuild = 'MSBuild 15.0'
    private static Integer DefaultPlatformPort = 8100
    private static Integer DefaultStorefrontPort = 8080
    private static Integer DefaultSqlPort = 1433    

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
        def packages;
        context.dir(packagesDir)
        {
            packages = context.findFiles(glob: '*.zip')
        }

        if (packages.size() > 0) {
            for (int i = 0; i < packages.size(); i++)
            {
                Packaging.installModule(context, "${packagesDir}\\${packages[i].path}")
            }
        }
    }

    def static runUnitTests(context)
    {
        Modules.runTests(context, "-trait \"category=ci\" -trait \"category=Unit\"", "xUnit.UnitTests.xml")
    }

    def static runIntegrationTests(context, Integer buildOrder)
    {
        def platformPort = DefaultPlatformPort + buildOrder
        def storefrontPort = DefaultStorefrontPort + buildOrder
        def sqlPort = DefaultSqlPort + buildOrder        

        // create context
        context.withEnv(["VC_PLATFORM=http://ci.virtocommerce.com:${platformPort}", "VC_STOREFRONT=http://ci.virtocommerce.com:${storefrontPort}", "VC_DATABASE=Data Source=http://ci.virtocommerce.com,${sqlPort};Initial Catalog=VirtoCommerce2;Persist Security Info=True;User ID=sa;Password=v!rto_Labs!;MultipleActiveResultSets=True;Connect Timeout=30" ]) {
            Modules.runTests(context, "-trait \"category=Integration\"", "xUnit.IntegrationTests.xml")
        }
    }

    def static runTests(context, traits, resultsFileName)
    {
        def xUnit = context.env.XUnit
        def xUnitExecutable = "${xUnit}\\xunit.console.exe"

        def paths = Modules.prepareTestEnvironment(context)
        context.bat "${xUnitExecutable} ${paths} -xml \"${resultsFileName}\" ${traits} -parallel none -verbose -diagnostics"
        context.step([$class: 'XUnitPublisher', testTimeMargin: '3000', thresholdMode: 1, thresholds: [[$class: 'FailedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: ''], [$class: 'SkippedThreshold', failureNewThreshold: '', failureThreshold: '', unstableNewThreshold: '', unstableThreshold: '']], tools: [[$class: 'XUnitDotNetTestType', deleteOutputFiles: true, failIfNotNew: false, pattern: resultsFileName, skipNoTestFiles: true, stopProcessingIfError: false]]])
    }    

    def static prepareTestEnvironment(context)
    {
        def testDlls = context.findFiles(glob: '**\\bin\\Debug\\*Test.dll')
        String paths = ""
        if (testDlls.size() > 0) {
            for (int i = 0; i < testDlls.size(); i++)
            {
                def testDll = testDlls[i]
                paths += "\"$testDll.path\" "
            }
        }

            // add platform dll to test installs
        def packagesDir = Utilities.getArtifactFolder(context)
        def allModulesDir = "c:\\Builds\\Jenkins\\VCF\\modules"

        context.env.xunit_virto_modules_folder = packagesDir
        context.env.xunit_virto_dependency_modules_folder = allModulesDir

        def testFolderName = "dev"

        // copy artifacts to global location that can be used by other modules, but don't do that for master branch as we need to test it with real manifest
        if (context.env.BRANCH_NAME != 'master') {
            context.dir(packagesDir)
            {		
                // copy all files to modules
                context.bat "xcopy *.zip \"${allModulesDir}\" /y" 
            }	
        }

        paths += "\"..\\..\\..\\vc-platform\\${testFolderName}\\workspace\\virtocommerce.platform.tests\\bin\\debug\\VirtoCommerce.Platform.Test.dll\""

        return paths;
    }
}