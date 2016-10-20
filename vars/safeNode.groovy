import java.util.regex.Pattern

def call(String label = null, Closure body) {
    node(label) {
        String path = pwd()
        String branchName = env.BRANCH_NAME
        if (branchName) {
            path = path.split(Pattern.quote(File.separator))
            def workspaceRoot = path[0..<-1].join(File.separator)
            def currentWs = path[-1]
            // Here is where we make branch names safe for directories -
            // the most common bad character is '/' in 'feature/add_widget'
            // which gets replaced with '%2f', so JOB_NAME will be
            // ${PR}}OJECT_NAME}%2f${BRANCH_NAME}
            String newWorkspace = env.JOB_NAME.replace('/', '-')
            newWorkspace = newWorkspace.replace(File.separator, '-')
            newWorkspace = newWorkspace.replace('%2f', '-')
            newWorkspace = newWorkspace.replace('%2F', '-')
            // Add on the '@n' suffix if it was there
            if (currentWs =~ '@') {
                newWorkspace = "${newWorkspace}@${currentWs.split('@')[-1]}"
            }
            path = "${workspaceRoot}${File.separator}${newWorkspace}"
        }
        ws(path) {
            body()
        }
    }
}