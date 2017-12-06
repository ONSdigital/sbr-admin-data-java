#!groovy
@Library('jenkins-pipeline-shared@feature/hbase-connect') _

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
        TABLE_NAME = "enterprise"
        NAMESPACE = "sbr_dev_db"
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
                    env.NODE_STAGE = "Checkout"
                }
            }
        }
        stage('Build'){
            agent any
            steps {
                colourText("info", "Building ${env.BUILD_ID} on ${env.JENKINS_URL} from branch ${env.BRANCH_NAME}")
                dir('gitlab') {
                    git(url: "$GITLAB_URL/StatBusReg/$MODULE_NAME-api.git", credentialsId: GITLAB_CREDS, branch: 'feature/hbase-rest')
                }
                // Replace fake VAT/PAYE data with real data
                sh 'rm -rf conf/sample/201706/vat_data.csv'
                sh 'rm -rf conf/sample/201706/paye_data.csv'
                sh 'cp gitlab/dev/data/sbr-2500-ent-vat-data.csv conf/sample/201706/vat_data.csv'
                sh 'cp gitlab/dev/data/sbr-2500-ent-paye-data.csv conf/sample/201706/paye_data.csv'
                sh 'cp gitlab/dev/conf/* conf'

                sh """
                    $SBT clean compile "project $MODULE_NAME" universal:packageBin coverage test coverageReport coverageAggregate
                """
                script {
                    if (BRANCH_NAME == BRANCH_DEV) {
                        env.DEPLOY_NAME = DEPLOY_DEV
                    }
                    else if  (BRANCH_NAME == BRANCH_TEST) {
                        env.DEPLOY_NAME = DEPLOY_TEST
                    }
                    else if (BRANCH_NAME == BRANCH_PROD) {
                        env.DEPLOY_NAME = DEPLOY_PROD
                    }
                }
            }
            post {
                always {
                    script {
                        env.NODE_STAGE = "Build"
                    }
                }
                success {
                    colourText("info","Successful Build!")
                    sh "cp target/universal/${ORGANIZATION}-${MODULE_NAME}-*.zip ${env.DEPLOY_NAME}-${ORGANIZATION}-${MODULE_NAME}.zip"
                }
                failure {
                    colourText("warn","Something went wrong!")
                }
            }
        }

        stage('Static Analysis') {
            agent any
            steps {
                parallel (
                    "Unit" :  {
                        colourText("info","Running unit tests")
                        // sh "$SBT test"
                    },
                    "Style" : {
                        colourText("info","Running style tests")
                        sh """
                            $SBT scalastyleGenerateConfig
                            $SBT scalastyle
                        """
                    },
                    "Additional" : {
                        colourText("info","Running additional tests")
                        sh "$SBT scapegoat"
                    }
                )
            }
            post {
                always {
                    script {
                        env.NODE_STAGE = "Static Analysis"
                    }
                }
                success {
                    colourText("info","Generating reports for tests")
                    //   junit '**/target/test-reports/*.xml'

                    // removed subfolder scala-2.11/ from target path
                    step([$class: 'CoberturaPublisher', coberturaReportFile: '**/target/coverage-report/*.xml'])
                    // target/scalastyle-result.xml,
                    step([$class: 'CheckStylePublisher', pattern: 'target/scapegoat-report/scapegoat-scalastyle.xml'])
                }
                failure {
                    colourText("warn","Failed to retrieve reports.")
                }
            }
        }

        stage ('Bundle') {
            agent any
            when {
                anyOf {
                    branch DEPLOY_DEV
                    branch DEPLOY_TEST
                    branch DEPLOY_PROD
                }
            }
            steps {
                script {
                    env.NODE_STAGE = "Bundle"
                }
                colourText("info", "Bundling....")
//                stash name: "zip"
            }
        }

        stage("Releases"){
            agent any
            when {
                anyOf {
                    branch DEPLOY_DEV
                    branch DEPLOY_TEST
                    branch DEPLOY_PROD
                }
            }
            steps {
                script {
                    env.NODE_STAGE = "Releases"
                    currentTag = getLatestGitTag()
                    colourText("info", "Found latest tag: ${currentTag}")
                    newTag =  IncrementTag( currentTag, RELEASE_TYPE )
                    colourText("info", "Generated new tag: ${newTag}")
                    //push(newTag, currentTag)
                }
            }
        }

        stage ('Package and Push Artifact') {
            agent any
            when {
                branch DEPLOY_PROD
            }
            steps {
                script {
                    env.NODE_STAGE = "Package and Push Artifact"
                }
                sh """
                    $SBT clean compile package
                    $SBT clean compile assembly
                """
                colourText("success", 'Package.')
            }
        }

        stage('Deploy'){
            agent any
            when {
                anyOf {
                    branch DEPLOY_DEV
                    branch DEPLOY_TEST
                    branch DEPLOY_PROD
                }
            }
            steps {
                script {
                    env.NODE_STAGE = "Deploy"
                }
                milestone(1)
                lock('Deployment Initiated') {
                    colourText("info", 'deployment in progress')
                    deploy()
                    colourText("success", 'Deploy.')
                }
            }
        }

        stage('Integration Tests') {
            agent any
            when {
                anyOf {
                    branch DEPLOY_DEV
                    branch DEPLOY_TEST
                }
            }
            steps {
                script {
                    env.NODE_STAGE = "Integration Tests"
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

def push (String newTag, String currentTag) {
    echo "Pushing tag ${newTag} to Gitlab"
    GitRelease( GIT_CREDS, newTag, currentTag, "${env.BUILD_ID}", "${env.BRANCH_NAME}", GIT_TYPE)
}

def deploy () {
    echo "Deploying Api app to ${env.DEPLOY_NAME}"
    withCredentials([string(credentialsId: CF_CREDS, variable: 'APPLICATION_SECRET')]) {
        deployToCloudFoundry("cloud-foundry-$TEAM-${env.DEPLOY_NAME}-user", TEAM, "${env.DEPLOY_NAME}", "${env.DEPLOY_NAME}-$MODULE_NAME", "${env.DEPLOY_NAME}-$ORGANIZATION-$MODULE_NAME.zip", "conf/${env.DEPLOY_NAME}/manifest.yml", TABLE_NAME, NAMESPACE)
    }
}
