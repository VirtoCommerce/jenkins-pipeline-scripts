package jobs.scripts;

class Packaging implements Serializable {

    private static String DefaultBranchOrCommitPR = '${sha1}'
    private static String DefaultBranchOrCommitPush = '*/master'
    private static String DefaultRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
    private static String DefaultMSBuild = 'MSBuild 15.0'

    private def Context;

    Packaging(script) {
        Context = script;
    }

    /**
     * Creates new docker image
     *
     * @param jobName Base name of the job
     * @param isPR True if PR job, false otherwise
     * @param folder (optional) If folder is specified, project is not used as the folder name
     * @return Full job name.  If folder prefix is specified,
     */
    def static createDockerImage(String jobName, boolean isPR, String folder = '') {

		bat "docker build -t virtocommerce/storefront:latest storefront"
        return "";//getFullJobName('', jobName, isPR, folder);
    }

    def createReleaseArtifact(version, webProject, zipArtifact, websiteDir)
    {
        Context.echo "Preparing release for ${version}"
        def tempFolder = Context.pwd(tmp: true)
        def wsFolder = Context.pwd()
        def websitePath = "$tempFolder\\_PublishedWebsites\\$websiteDir"
        def packagesDir = "$wsFolder\\artifacts"

        Context.dir(packagesDir)
        {
            Context.deleteDir()
        }

        // create artifacts
        Context.bat "\"${script.tool 'MSBuild 15.0'}\" \"${webProject}\" /nologo /verbosity:m /p:Configuration=Release /p:Platform=\"Any CPU\" /p:DebugType=none \"/p:OutputPath=$tempFolder\""
        (new AntBuilder()).zip(destfile: "${packagesDir}\\${zipArtifact}.${version}.zip", basedir: "${websitePath}")
    }
}