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
        // timeout moved into the "Pipeline" wrapper stage below (after the
        // ci-springboot-build lock is acquired) so lock-wait does NOT count
        // toward it; raised 90 -> 150min for slow contended runs (2026-07-02)
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '1'))
        disableConcurrentBuilds()
        lock('ci-springboot-build')  // serialize whole pipeline across ALL branches: host RAM can't run concurrent springboot builds (testcontainers MySQL OOM -> exit124 rebuild loop, 2026-06-29)
        timestamps()
    }

    environment {
        APP_NAME  = "springboot-app"
        REGISTRY  = "localhost:5000"
        IMAGE_TAG = "${REGISTRY}/arcana/${APP_NAME}"
        VERSION   = "1.0.0"
    }

    stages {
      stage("Pipeline") {
        // timeout wraps only execution; the pipeline-level lock('ci-springboot-build')
        // is acquired before this stage, so time spent waiting for the lock is excluded.
        options { timeout(time: 150, unit: 'MINUTES') }
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
                    # keep only the previous build image (layer cache); the
                    # registry holds every build-N tag durably
                    docker images --format '{{.Repository}}:{{.Tag}}' \
                        | grep -E "^${IMAGE_TAG}:build-[0-9]+$" \
                        | sed 's/.*:build-//' | sort -rn | tail -n +2 \
                        | sed "s|^|${IMAGE_TAG}:build-|" \
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
                // Push build tag to the registry now, before the long unit-test window,
                // so the integration stages can pull it even if host GC reclaims the
                // local image mid-run (mirrors arcana-cloud-nodejs "Push Build Tag").
                sh "docker push ${IMAGE_TAG}:build-${BUILD_NUMBER}"
            }
        }

        stage("Unit Tests") {
            steps {
                sh '''
                    docker rm -f springboot-app-test-${BUILD_NUMBER} 2>/dev/null || true
                    docker compose -f docker-compose.test.yml run --build --name springboot-app-test-${BUILD_NUMBER} test
                    RC=$?
                    mkdir -p build/reports
                    docker cp springboot-app-test-${BUILD_NUMBER}:/app/build/reports/. build/reports/ 2>/dev/null || true
                    docker rm -f springboot-app-test-${BUILD_NUMBER} 2>/dev/null || true
                    exit $RC
                '''
            }
        }

        stage("Integration: Layered HTTP") {
            // Serialize this repo's layered-compose stage: main + PR builds share
            // static compose project/network/container names and collide when concurrent.
            options { lock('ci-springboot-layered') }
            steps {
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
            post {
                always {
                    dir('deployment/layered') {
                        sh 'docker compose -p arcana-ci-http -f docker-compose-ci-http.yml down -v --remove-orphans 2>/dev/null || true'
                    }
                }
            }
        }

        stage("Integration: Layered gRPC") {
            // Serialize this repo's layered-compose stage: main + PR builds share
            // static compose project/network/container names and collide when concurrent.
            options { lock('ci-springboot-layered') }
            steps {
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
            post {
                always {
                    dir('deployment/layered') {
                        sh 'docker compose -p arcana-ci-grpc -f docker-compose-ci-grpc.yml down -v --remove-orphans 2>/dev/null || true'
                    }
                }
            }
        }

        stage("Integration: K8s HTTP") {
            // Serialize ALL kind/k8s stages host-wide: concurrent kind clusters
            // OOM-killed image imports on the 24G shared host (exit 137).
            options { lock('ci-kind-global') }
            steps {
                sh '''#!/bin/bash
                    export PATH="/var/jenkins_home/bin:${PATH}"
                    kind version || { echo "kind not found"; exit 1; }
                    bash scripts/kind-smoke-test.sh "${IMAGE_TAG}:build-${BUILD_NUMBER}" http 480
                '''
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
            // Serialize ALL kind/k8s stages host-wide: concurrent kind clusters
            // OOM-killed image imports on the 24G shared host (exit 137).
            options { lock('ci-kind-global') }
            steps {
                sh '''#!/bin/bash
                    export PATH="/var/jenkins_home/bin:${PATH}"
                    kind version || { echo "kind not found"; exit 1; }
                    kubectl version --client || true
                    bash scripts/kind-smoke-test.sh "${IMAGE_TAG}:build-${BUILD_NUMBER}" grpc 600
                '''
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
                withSonarQubeEnv('SonarQube') {
                    sh """sonar-scanner -Dsonar.projectKey=springboot-app -Dsonar.scm.disabled=true"""
                    sh '''
                        set -e
                        TOKEN="${SONAR_AUTH_TOKEN:-$SONAR_TOKEN}"
                        RT=.scannerwork/report-task.txt
                        [ -f "$RT" ] || { echo "report-task.txt missing"; exit 1; }
                        CE_TASK_ID=$(grep '^ceTaskId=' "$RT" | cut -d= -f2-)
                        ANALYSIS_ID=""
                        for i in $(seq 1 60); do
                            RESP=$(curl -s -u "$TOKEN:" "$SONAR_HOST_URL/api/ce/task?id=$CE_TASK_ID")
                            ST=$(echo "$RESP" | grep -o '"status":"[A-Z_]*"' | head -1 | cut -d'"' -f4)
                            echo "  CE status: ${ST:-?} (try $i)"
                            if [ "$ST" = "SUCCESS" ]; then ANALYSIS_ID=$(echo "$RESP" | grep -o '"analysisId":"[^"]*"' | head -1 | cut -d'"' -f4); break;
                            elif [ "$ST" = "FAILED" ] || [ "$ST" = "CANCELED" ]; then echo "CE $ST"; exit 1; fi
                            sleep 5
                        done
                        [ -n "$ANALYSIS_ID" ] || { echo "CE timeout"; exit 1; }
                        GATE=$(curl -s -u "$TOKEN:" "$SONAR_HOST_URL/api/qualitygates/project_status?analysisId=$ANALYSIS_ID")
                        GST=$(echo "$GATE" | grep -o '"status":"[A-Z]*"' | head -1 | cut -d'"' -f4)
                        echo "Quality gate: ${GST:-UNKNOWN}"
                        if [ "$GST" != "OK" ]; then echo "$GATE"; exit 1; fi
                    '''
                }
            }
        }

        stage("Architecture Qube") {
            steps {
                sh '''
                    docker rm -f arcana-arch-qube-springboot-${BUILD_NUMBER} 2>/dev/null || true
                    docker create --name arcana-arch-qube-springboot-${BUILD_NUMBER} --network devops_default \
                        -v /src -v /output \
                        arcana.boo/arcana/arch-qube:latest \
                        scan /src --framework springboot --no-ai --ci \
                        --format json,markdown -o /output --threshold 90 || exit 1
                    tar --exclude=./.git --exclude=./build --exclude=./arch-qube-reports -C . -cf - . \
                        | docker cp - arcana-arch-qube-springboot-${BUILD_NUMBER}:/src || exit 1
                    docker start -a arcana-arch-qube-springboot-${BUILD_NUMBER}
                    AQ_RC=$?
                    mkdir -p arch-qube-reports
                    docker cp arcana-arch-qube-springboot-${BUILD_NUMBER}:/output/. arch-qube-reports/ 2>/dev/null || true
                    docker rm -f arcana-arch-qube-springboot-${BUILD_NUMBER} 2>/dev/null || true
                    exit $AQ_RC
                '''
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
                // Derive the release tag from the registry-durable build-N image.
                // The local :${VERSION} tag may have been reclaimed by host GC during
                // the long build window (build-N is already pushed in Docker Compose
                // Build), so pull build-N back, re-tag, and push the release tag.
                sh "docker pull ${IMAGE_TAG}:build-${BUILD_NUMBER}"
                sh "docker tag ${IMAGE_TAG}:build-${BUILD_NUMBER} ${IMAGE_TAG}:${VERSION}"
                sh "docker push ${IMAGE_TAG}:${VERSION}"
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
      }
    }

    post {
        success {
            echo "Pipeline SUCCESS - ${APP_NAME}:${VERSION} branch=${env.BRANCH_NAME ?: '?'} pr=${env.CHANGE_ID ?: 'no'}"
            sh '''
                # self-clean: keep only THIS build's image locally; previous
                # build-N tags stay pullable from the registry
                docker images --format '{{.Repository}}:{{.Tag}}' \
                    | grep -E "^${IMAGE_TAG}:build-[0-9]+$" \
                    | grep -v ":build-${BUILD_NUMBER}$" \
                    | xargs -r docker rmi 2>/dev/null || true
            '''
        }
        failure { echo "Pipeline FAILED - branch=${env.BRANCH_NAME ?: '?'} pr=${env.CHANGE_ID ?: 'no'}" }
        always  { echo "Build number ${BUILD_NUMBER} done" }
    }
}
