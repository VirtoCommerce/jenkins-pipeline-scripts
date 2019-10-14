import jobs.scripts.*

node {
    stage('Checkout'){
        deleteDir()
        checkout scm
    }

    stage('Build'){
        bat "vc-build Compress"
        bat "vc-build Pack"
    }

    stage('Unit Tests'){
        bat "vc-build Test"
    }   
}