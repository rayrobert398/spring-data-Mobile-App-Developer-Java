pipeline {
    agent none

    triggers {
        pollSCM 'H/10 * * * *'
        upstream(upstreamProjects: "spring-data-commons/master", threshold: hudson.model.Result.SUCCESS)
    }

    options {
        disableConcurrentBuilds()
    }
    
    stages {
        stage("Test") {
            parallel {
                stage("test: baseline") {
                    agent {
                        docker {
                            image 'adoptopenjdk/openjdk8:latest'
                            args '-u root -v /var/run/docker.sock:/var/run/docker.sock' // root but with no maven caching
                        }
                    }
                    options { timeout(time: 30, unit: 'MINUTES') }
                    steps {
                        sh 'rm -rf ?'
                        sh 'MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/spring-data-maven-repository" ./mvnw -Pci,all-dbs clean dependency:list test -Dsort -Dbundlor.enabled=false -B'
                        sh "chown -R 1001:1001 target"
                    }
                }
            }
        }

        stage('Release to artifactory') {
            when {
                branch 'issue/*'
            }
            agent {
                docker {
                    image 'adoptopenjdk/openjdk8:latest'
                    args '-v $HOME/.m2:/tmp/spring-data-maven-repository'
                }
            }
            options { timeout(time: 20, unit: 'MINUTES') }

            environment {
                ARTIFACTORY = credentials('02bd1690-b54f-4c9f-819d-a77cb7a9822c')
            }

            steps {
                sh 'rm -rf ?'
                sh 'MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/spring-data-maven-repository" ./mvnw -Pci,snapshot deploy -Dmaven.test.skip=true -B'
            }
        }
        
        stage('Release to artifactory with docs') {
            when {
                branch 'master'
            }
            agent {
                docker {
                    image 'adoptopenjdk/openjdk8:latest'
                    args '-v $HOME/.m2:/tmp/spring-data-maven-repository'
                }
            }
            options { timeout(time: 20, unit: 'MINUTES') }

            environment {
                ARTIFACTORY = credentials('02bd1690-b54f-4c9f-819d-a77cb7a9822c')
            }

            steps {
                sh 'rm -rf ?'
                sh 'MAVEN_OPTS="-Duser.name=jenkins -Duser.home=/tmp/spring-data-maven-repository" ./mvnw -Pci,snapshot deploy -Dmaven.test.skip=true -B'
            }
        }
    }

    post {
        changed {
            script {
                slackSend(
                        color: (currentBuild.currentResult == 'SUCCESS') ? 'good' : 'danger',
                        channel: '#spring-data-dev',
                        message: "${currentBuild.fullDisplayName} - `${currentBuild.currentResult}`\n${env.BUILD_URL}")
                emailext(
                        subject: "[${currentBuild.fullDisplayName}] ${currentBuild.currentResult}",
                        mimeType: 'text/html',
                        recipientProviders: [[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']],
                        body: "<a href=\"${env.BUILD_URL}\">${currentBuild.fullDisplayName} is reported as ${currentBuild.currentResult}</a>")
            }
        }
    }
}
