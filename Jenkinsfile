// Jenkinsfile — multibranch pipeline for arcana-cloud-springboot
// Adapted from legacy springboot-app-pipeline (single-branch job polling SCM).
//
// Key differences from the legacy XML-embedded script:
//   * `checkout scm` (no hardcoded branch=main)        — supports every branch + every PR
//   * `pollSCM` trigger removed                        — Jenkins multibranch + GitHub webhook drive triggers
//   * "Push to Registry" + "Arch Qube Metrics" gated   — only main pushes to registry; PR builds stay local
//   * SonarQube gets pullrequest.* params on PRs       — PR-decoration in Sonar UI
//   * `dir("${env.PROJECTS_DIR}/...")` blocks removed  — multibranch uses workspace root

pipeline {
    agent any

    options {
        timeout(time: 90, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '1'))
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        APP_NAME  = "springboot-app"
        REGISTRY  = "localhost:5000"
        IMAGE_TAG = "${REGISTRY}/arcana/${APP_NAME}"
        VERSION   = "1.0.0"
    }

    stages {
        stage("Checkout") {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
                script {
                    echo "Branch: ${env.BRANCH_NAME ?: 'unknown'}"
                    echo "PR: ${env.CHANGE_ID ?: 'no'} (target: ${env.CHANGE_TARGET ?: 'n/a'})"
                }
            }
        }

        stage("Cleanup Old Images") {
            steps {
                sh '''
                    docker image prune -f || true
                    docker images --format '{{.Repository}}:{{.Tag}}' \
                        | grep "${APP_NAME}.*build-" \
                        | sort -t- -k2 -rn \
                        | tail -n +4 \
                        | xargs -r docker rmi 2>/dev/null || true
                    docker compose -f docker-compose.test.yml down \
                        --remove-orphans 2>/dev/null || true
                '''
            }
        }

        stage("Docker Compose Build") {
            steps {
                sh "VERSION=${VERSION} docker compose -f docker-compose.ci.yml build"
                sh "docker tag localhost:5000/arcana/${APP_NAME}:${VERSION} ${IMAGE_TAG}:build-${BUILD_NUMBER}"
            }
        }

        stage("Unit Tests") {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh "docker compose -f docker-compose.test.yml run --rm --build test"
                }
            }
        }

        stage("Integration: Layered HTTP") {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh '''
                        docker compose -p arcana-ci-http -f deployment/layered/docker-compose-ci-http.yml down -v --remove-orphans 2>/dev/null || true
                        docker compose -p arcana-ci-grpc -f deployment/layered/docker-compose-ci-grpc.yml down -v --remove-orphans 2>/dev/null || true
                        docker rm -f arcana-ci-mysql-http arcana-ci-redis-http arcana-ci-service-http arcana-ci-controller-http arcana-ci-mysql-grpc arcana-ci-redis-grpc arcana-ci-repository-grpc arcana-ci-service-grpc arcana-ci-controller-grpc 2>/dev/null || true
                        SPRINGBOOT_IMAGE="${IMAGE_TAG}:build-${BUILD_NUMBER}" \
                            docker compose -p arcana-ci-http -f deployment/layered/docker-compose-ci-http.yml up -d
                        JENKINS_ID=$(hostname)
                        docker network connect arcana-ci-http-net $JENKINS_ID 2>/dev/null || true
                        bash scripts/integration-smoke-test.sh http://arcana-ci-controller-http:8090 http 180
                        docker network disconnect arcana-ci-http-net $JENKINS_ID 2>/dev/null || true
                    '''
                }
            }
            post {
                always {
                    dir('deployment/layered') {
                        sh 'docker compose -p arcana-ci-http -f docker-compose-ci-http.yml down -v --remove-orphans 2>/dev/null || true'
                    }
                }
            }
        }

        stage("Integration: Layered gRPC") {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh '''
                        docker compose -p arcana-ci-http -f deployment/layered/docker-compose-ci-http.yml down -v --remove-orphans 2>/dev/null || true
                        docker compose -p arcana-ci-grpc -f deployment/layered/docker-compose-ci-grpc.yml down -v --remove-orphans 2>/dev/null || true
                        docker rm -f arcana-ci-mysql-http arcana-ci-redis-http arcana-ci-service-http arcana-ci-controller-http arcana-ci-mysql-grpc arcana-ci-redis-grpc arcana-ci-repository-grpc arcana-ci-service-grpc arcana-ci-controller-grpc 2>/dev/null || true
                        SPRINGBOOT_IMAGE="${IMAGE_TAG}:build-${BUILD_NUMBER}" \
                            docker compose -p arcana-ci-grpc -f deployment/layered/docker-compose-ci-grpc.yml up -d
                        JENKINS_ID=$(hostname)
                        docker network connect arcana-ci-grpc-net $JENKINS_ID 2>/dev/null || true
                        bash scripts/integration-smoke-test.sh http://arcana-ci-controller-grpc:8090 grpc 270
                        docker network disconnect arcana-ci-grpc-net $JENKINS_ID 2>/dev/null || true
                    '''
                }
            }
            post {
                always {
                    dir('deployment/layered') {
                        sh 'docker compose -p arcana-ci-grpc -f docker-compose-ci-grpc.yml down -v --remove-orphans 2>/dev/null || true'
                    }
                }
            }
        }

        stage("Integration: K8s HTTP") {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh '''#!/bin/bash
                        export PATH="/var/jenkins_home/bin:${PATH}"
                        kind version || { echo "kind not found"; exit 1; }
                        bash scripts/kind-smoke-test.sh "${IMAGE_TAG}:build-${BUILD_NUMBER}" http 480
                    '''
                }
            }
            post {
                always {
                    sh '''#!/bin/bash
                        export PATH="/var/jenkins_home/bin:${PATH}"
                        kind get clusters 2>/dev/null | grep arcana-ci | while read cl; do
                          kind delete cluster --name "$cl" 2>/dev/null || true
                        done
                    '''
                }
            }
        }

        stage("Integration: K8s gRPC") {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh '''#!/bin/bash
                        export PATH="/var/jenkins_home/bin:${PATH}"
                        kind version || { echo "kind not found"; exit 1; }
                        kubectl version --client || true
                        bash scripts/kind-smoke-test.sh "${IMAGE_TAG}:build-${BUILD_NUMBER}" grpc 600
                    '''
                }
            }
            post {
                always {
                    sh '''#!/bin/bash
                        export PATH="/var/jenkins_home/bin:${PATH}"
                        kind get clusters 2>/dev/null | grep arcana-ci | while read cl; do
                          kind delete cluster --name "$cl" 2>/dev/null || true
                        done
                    '''
                }
            }
        }

        stage("SonarQube Analysis") {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    withSonarQubeEnv('SonarQube') {
                        script {
                            def prArgs = env.CHANGE_ID ? """ \
                                -Dsonar.pullrequest.key=${env.CHANGE_ID} \
                                -Dsonar.pullrequest.branch=${env.BRANCH_NAME} \
                                -Dsonar.pullrequest.base=${env.CHANGE_TARGET}""" : ''
                            sh """sonar-scanner -Dsonar.projectKey=springboot-app -Dsonar.scm.disabled=true${prArgs}"""
                        }
                    }
                }
            }
        }

        stage("Architecture Qube") {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                    sh '''
                        mkdir -p arch-qube-reports
                        docker run --rm \
                            --network devops_default \
                            -v $(pwd):/project \
                            -v $(pwd)/arch-qube-reports:/output \
                            arcana.boo/arcana/arch-qube:latest scan /project \
                            --framework springboot --no-ai \
                            --ci --format json,markdown \
                            -o /output --threshold 90 || true
                    '''
                }
            }
        }

        stage("Image Info") {
            steps {
                sh "docker images --format 'table {{.Repository}}:{{.Tag}}\\t{{.Size}}' | grep ${APP_NAME} || true"
            }
        }

        stage("Push to Registry") {
            when { branch 'main' }
            steps {
                sh "docker push ${IMAGE_TAG}:${VERSION}"
                sh "docker push ${IMAGE_TAG}:build-${BUILD_NUMBER}"
            }
        }

        stage("Arch Qube Metrics") {
            when { branch 'main' }
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    sh "bash /data/projects/_scripts/arch-qube-metrics.sh \$(pwd) arcana-cloud-springboot || true"
                }
            }
        }
    }

    post {
        success { echo "Pipeline SUCCESS - ${APP_NAME}:${VERSION} branch=${env.BRANCH_NAME ?: '?'} pr=${env.CHANGE_ID ?: 'no'}" }
        failure { echo "Pipeline FAILED - branch=${env.BRANCH_NAME ?: '?'} pr=${env.CHANGE_ID ?: 'no'}" }
        always  { echo "Build number ${BUILD_NUMBER} done" }
    }
}
