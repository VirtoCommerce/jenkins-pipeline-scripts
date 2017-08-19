package jobs.scripts;

class Utilities {

    private static String DefaultBranchOrCommitPR = '${sha1}'
    private static String DefaultBranchOrCommitPush = '*/master'
    private static String DefaultRefSpec = '+refs/pull/*:refs/remotes/origin/pr/*'
    private static String DefaultMSBuild = 'MSBuild 15.0'

    /**
     * Get the folder name for a job.
     *
     * @param project Project name (e.g. dotnet/coreclr)
     * @return Folder name for the project. Typically project name with / turned to _
     */
    def static getFolderName(String project) {
        return project.replace('/', '_')
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

    def static createReleaseArtifact(def version, def webProject, def zipArtifact, def websiteDir)
    {
        //echo "Preparing release for ${version}"
        def tempFolder = "..\\workspace@tmp"
        def websitePath = "$tempFolder\\_PublishedWebsites\\$websiteDir"
        def packagesDir = "artifacts"

        dir(packagesDir)
        {
            deleteDir()
        }

        // create artifacts
        bat "\"${tool 'MSBuild 15.0'}\" \"${webProject}\" /nologo /verbosity:m /p:Configuration=Release /p:Platform=\"Any CPU\" /p:DebugType=none \"/p:OutputPath=$tempFolder\""
        (new AntBuilder()).zip(destfile: "${packagesDir}\\${zipArtifact}.${version}.zip", basedir: "${websitePath}")
    }
}