// module script
def call(body) {
    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    
    // you can call any valid step functions from your code, just like you can from Pipeline scripts
    echo "Building plugin ${config.name}"
}
