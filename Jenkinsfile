import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.api.*;
import org.eclipse.jgit.lib.StoredConfig;

node
{
 git "git@github.com:VirtoCommerce/jenkins-pipeline-scripts.git"
}

def upstreamRepoUrl = "https://github.com/VirtoCommerce/jenkins-pipeline-scripts"
Jenkins j = Jenkins.getInstance();
File workflowLibDir = new File(j.getRootPath().toString(), "workflow-libs")

println "cmd /c \"c:\\Program Files (x86)\\Git\\cmd\\git.exe\" pull upstream master".execute(null, workflowLibDir).text
println "cmd /c ls -la".execute(null, workflowLibDir).text
