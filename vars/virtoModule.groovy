// module script
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // you can call any valid step functions from your code, just like you can from Pipeline scripts
    echo "Building plugin ${config.name}"
    
    echo "updating version"
    updateVersion(pwd())
    step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: []]])

    /*
    if(env.BRANCH_NAME=="master"){
        stage name: 'Deploy to Prod', concurrency: 1
            updateVersion(pwd())
    }
    */
}

def updateVersion(workspace)
{
    def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent
    bat "powershell.exe -File \"${scriptDir}}\\version.ps1\" -solutiondir \"${workspace}\""
    bat "\"${tool 'Git'}\" config user.email \"ci@virtocommerce.com\""
    bat "\"${tool 'Git'}\" config user.name \"Virto Jenkins\""
    bat "\"${tool 'Git'}\" commit -am \"Updated version number\""
    bat "\"${tool 'Git'}\" push origin HEAD:master -f"
}


