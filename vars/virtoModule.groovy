// module script
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // you can call any valid step functions from your code, just like you can from Pipeline scripts
    echo "Building plugin ${config.name}"
    
    if(env.BRANCH_NAME=="master"){
        stage name: 'Deploy to Prod', concurrency: 1
            updateVersion(pwd())
    }
}

def updateVersion(workspace)
{
    bat "powershell.exe -File \"${env.VC_RES}\\script\\version3.ps1\" -solutiondir \"${workspace}\""
    bat "\"${tool 'Git'}\" config user.email \"ci@virtocommerce.com\""
    bat "\"${tool 'Git'}\" config user.name \"Virto Jenkins\""
    bat "\"${tool 'Git'}\" commit -am \"Updated version number\""
    bat "\"${tool 'Git'}\" push origin HEAD:master -f"
}


