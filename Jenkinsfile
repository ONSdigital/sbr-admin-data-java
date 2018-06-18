#!groovy
@Library('jenkins-pipeline-shared') _

pipeline {
    environment {
        RELEASE_TYPE = "PATCH"

        BRANCH_DEV = "develop"
        BRANCH_TEST = "release"
        BRANCH_PROD = "master"

        DEPLOY_DEV = "dev"
        DEPLOY_TEST = "test"
        DEPLOY_PROD = "prod"

        CF_CREDS = "sbr-api-dev-secret-key"

        GIT_TYPE = "Github"
        GIT_CREDS = "github-sbr-user"
        GITLAB_CREDS = "sbr-gitlab-id"

        ORGANIZATION = "ons"
        TEAM = "sbr"
        MODULE_NAME = "sbr-admin-data"

        // hbase config
        CH_TABLE = "ch"
        VAT_TABLE = "vat"
        PAYE_TABLE = "paye"
        LEU_TABLE = "leu"
	    
    	STAGE = "NONE"
    }
    options {
        skipDefaultCheckout()
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '30'))
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
    }
    agent any
    stages {
        stage('Checkout') {
            agent any
            steps {
                deleteDir()
                checkout scm
                stash name: 'app'
                sh "$SBT version"
                script {
                    version = '1.0.' + env.BUILD_NUMBER
                    currentBuild.displayName = version
                    STAGE = "Checkout"
                }
            }
        }

        stage('Build') {
            agent any
            steps{
                sh "$SBT clean compile"
            }
        }

        stage('Test'){
            agent any
            steps {
                colourText("info", "Building ${env.BUILD_ID} on ${env.JENKINS_URL} from branch ${env.BRANCH_NAME}")

                sh """
                    $SBT clean compile "project $MODULE_NAME" coverage test coverageReport coverageAggregate
                """
            }
            post {
                always {
                    script {
                        STAGE = "Test"
                    }
                }
                success {
                    colourText("info","Tests successful!")
                }
                failure {
                    colourText("warn","Failure during tests!")
                }
            }
        }

        stage('Static Analysis') {
            agent any
            steps {
                parallel (
                    "Scalastyle" : {
                        colourText("info","Running scalastyle analysis")
                        sh "$SBT scalastyle"
                    },
                    "Scapegoat" : {
                        colourText("info","Running scapegoat analysis")
                        sh "$SBT scapegoat"
                    }
                )
            }
            post {
                always {
                    script {
                        STAGE = "Static Analysis"
                    }
                }
                success {
                    colourText("info","Generating reports for tests")
                    junit '**/target/test-reports/*.xml'

                    // removed subfolder scala-2.11/ from target path
                    step([$class: 'CoberturaPublisher', coberturaReportFile: '**/target/coverage-report/*.xml'])
                    step([$class: 'CheckStylePublisher', pattern: '**/target/code-quality/style/*scalastyle*.xml'])
                }
                failure {
                    colourText("warn","Failed to retrieve reports.")
                }
            }
        }

        stage('Package'){
            agent any
            when {
                expression {
                    isBranch(BRANCH_DEV) || isBranch(BRANCH_TEST) || isBranch(BRANCH_PROD)
                }
            }
            steps {
               // colourText("info", "Building ${env.BUILD_ID} on ${env.JENKINS_URL} from branch ${env.BRANCH_NAME}")
                dir('gitlab') {
                    git(url: "$GITLAB_URL/StatBusReg/${MODULE_NAME}-api.git", credentialsId: GITLAB_CREDS, branch: "${BRANCH_DEV}")
                }
                // Replace fake VAT/PAYE data with real data
                sh '''
                rm -rf conf/sample/201706/vat_data.csv
                rm -rf conf/sample/201706/paye_data.csv
                cp gitlab/dev/data/sbr-2500-ent-vat-data.csv conf/sample/201706/vat_data.csv
                cp gitlab/dev/data/sbr-2500-ent-paye-data.csv conf/sample/201706/paye_data.csv
                cp gitlab/dev/conf/* conf
                '''

                sh """$SBT clean compile "project $MODULE_NAME" universal:packageBin"""

                script {
                    if (BRANCH_NAME == BRANCH_DEV) {
                        env.DEPLOY_NAME = DEPLOY_DEV
                        sh "cp target/universal/${ORGANIZATION}-${MODULE_NAME}-*.zip ${DEPLOY_DEV}-${ORGANIZATION}-${MODULE_NAME}.zip"
                    }
                    else if  (BRANCH_NAME == BRANCH_TEST) {
                        env.DEPLOY_NAME = DEPLOY_TEST
                        sh "cp target/universal/${ORGANIZATION}-${MODULE_NAME}-*.zip ${DEPLOY_TEST}-${ORGANIZATION}-${MODULE_NAME}.zip"
                    }
                    else if (BRANCH_NAME == BRANCH_PROD) {
                        env.DEPLOY_NAME = DEPLOY_PROD
                        sh "cp target/universal/${ORGANIZATION}-${MODULE_NAME}-*.zip ${DEPLOY_PROD}-${ORGANIZATION}-${MODULE_NAME}.zip"
                    }
                    else {
                        colourText("info","Not a valid branch to set env var DEPLOY_NAME")
                    }
                }
            }
            post {
                always {
                    script {
                        STAGE = "Package"
                    }
                }
                success {
                    colourText("info","Packaging Successful!")
                }
                failure {
                    colourText("warn","Something went wrong!")
                }
            }
        }

        stage('Deploy'){
            agent any
            when {
                 expression {
                     isBranch(BRANCH_DEV) || isBranch(BRANCH_TEST) || isBranch(BRANCH_PROD)
                 }
            }
            steps {
                script {
                    STAGE = "Deploy"
                }
                milestone(1)
                lock('CH Deployment Initiated') {
                    colourText("info", "${env.DEPLOY_NAME}-${CH_TABLE}-${MODULE_NAME} deployment in progress")
                    deploy(CH_TABLE, false)
                    colourText("success", "${env.DEPLOY_NAME}-${CH_TABLE}-${MODULE_NAME} Deployed.")
                }
                lock('VAT Deployment Initiated') {
                    colourText("info", "${env.DEPLOY_NAME}-${VAT_TABLE}-${MODULE_NAME} deployment in progress")
                    deploy(VAT_TABLE, false)
                    colourText("success", "${env.DEPLOY_NAME}-${VAT_TABLE}-${MODULE_NAME} Deployed.")
                }
                lock('PAYE Deployment Initiated') {
                    colourText("info", "${env.DEPLOY_NAME}-${PAYE_TABLE}-${MODULE_NAME} deployment in progress")
                    deploy(PAYE_TABLE, false)
                    colourText("success", "${env.DEPLOY_NAME}-${PAYE_TABLE}-${MODULE_NAME} Deployed.")
                }
		        lock('Legal Unit Deployment Initiated') {
                    colourText("info", "${env.DEPLOY_NAME}-${LEU_TABLE}-${MODULE_NAME} deployment in progress")
                    deploy(LEU_TABLE , true)
                    colourText("success", "${env.DEPLOY_NAME}-${LEU_TABLE}-${MODULE_NAME} Deployed.")
                }
            }
        }

        stage ('Package and Push Artifact') {
            agent any
            when {
                expression {
                    isBranch(BRANCH_DEV) || isBranch(BRANCH_TEST)
                }
            }
            steps {
                script {
                    env.NODE_STAGE = "Package and Push Artifact"
                }
                sh """
                    $SBT 'set test in assembly := {}' clean compile assembly
                """
                copyToHBaseNode()
                colourText("success", 'Package.')
            }
        }

        stage("Releases"){
            agent any
            when {
                expression {
                    isBranch(BRANCH_DEV) || isBranch(BRANCH_TEST) || isBranch(BRANCH_PROD)
                }
            }
            steps {
                script {
                    STAGE = "Releases"
                    currentTag = getLatestGitTag()
                    colourText("info", "Found latest tag: ${currentTag}")
                    newTag =  IncrementTag( currentTag, RELEASE_TYPE )
                    colourText("info", "Generated new tag: ${newTag}")
                    //push(newTag, currentTag)
                }
            }
        }

        stage('Integration Tests') {
            agent any
            when {
                expression {
                    isBranch(BRANCH_DEV) || isBranch(BRANCH_TEST)
                }
            }
            steps {
                script {
                    STAGE = "Integration Tests"
                }
                unstash 'compiled'
                sh "$SBT it:test"
                colourText("success", 'Integration Tests - For Release or Dev environment.')
            }
        }
    }
    post {
        always {
            script {
                colourText("info", 'Post steps initiated')
                deleteDir()
            }
        }
        success {
            colourText("success", "All stages complete. Build was successful.")
            sendNotifications currentBuild.result, "\$SBR_EMAIL_LIST"
        }
        unstable {
            colourText("warn", "Something went wrong, build finished with result ${currentResult}. This may be caused by failed tests, code violation or in some cases unexpected interrupt.")
            sendNotifications currentBuild.result, "\$SBR_EMAIL_LIST", "${env.NODE_STAGE}"
        }
        failure {
            colourText("warn","Process failed at: ${env.NODE_STAGE}")
            sendNotifications currentBuild.result, "\$SBR_EMAIL_LIST", "${env.NODE_STAGE}"
        }
    }
}

def isBranch(String branchName){
    return env.BRANCH_NAME.toString().equals(branchName)
}

def push (String newTag, String currentTag) {
    echo "Pushing tag ${newTag} to Gitlab"
    GitRelease( GIT_CREDS, newTag, currentTag, "${env.BUILD_ID}", "${env.BRANCH_NAME}", GIT_TYPE)
}

def deploy (String DATA_SOURCE, Boolean REVERSE_FLAG) {
    CF_SPACE = "${env.DEPLOY_NAME}".capitalize()
    CF_ORG = "${TEAM}".toUpperCase()
    NAMESPACE = "sbr_${env.DEPLOY_NAME}_db"
    echo "Deploying Api app to ${env.DEPLOY_NAME}"
    withCredentials([string(credentialsId: CF_CREDS, variable: 'APPLICATION_SECRET')]) {
        deployToCloudFoundryHBaseWithReverseOption("${TEAM}-${env.DEPLOY_NAME}-cf", "${CF_ORG}", "${CF_SPACE}", "${env.DEPLOY_NAME}-${DATA_SOURCE}-${MODULE_NAME}", "${env.DEPLOY_NAME}-${ORGANIZATION}-${MODULE_NAME}.zip", "gitlab/${env.DEPLOY_NAME}/manifest.yml", "${DATA_SOURCE}", "${NAMESPACE}", REVERSE_FLAG)
    }
}

def copyToHBaseNode() {
    echo "Deploying to ${env.DEPLOY_NAME}"
    sshagent(credentials: ["sbr-${env.DEPLOY_NAME}-ci-ssh-key"]) {
        withCredentials([string(credentialsId: "sbr-hbase-node", variable: 'HBASE_NODE')]) {
            sh """
                ssh sbr-${env.DEPLOY_NAME}-ci@${HBASE_NODE} mkdir -p ${MODULE_NAME}/lib
                scp ${WORKSPACE}/target/ons-sbr-admin-data-*.jar sbr-${env.DEPLOY_NAME}-ci@${HBASE_NODE}:${MODULE_NAME}/lib/
                echo 'Successfully copied jar file to ${MODULE_NAME}/lib directory on ${HBASE_NODE}'
                ssh sbr-${env.DEPLOY_NAME}-ci@${HBASE_NODE} hdfs dfs -put -f ${MODULE_NAME}/lib/ons-sbr-admin-data-*.jar hdfs://prod1/user/sbr-${env.DEPLOY_NAME}-ci/lib/
                echo 'Successfully copied jar file to HDFS'
	    """
        }
    }
}
