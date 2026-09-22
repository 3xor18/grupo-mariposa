def services = ['config-server', 'products-api', 'clients-api', 'order-processor', 'order-tracker']
def goImage = 'golang:1.26'
def golangciLintImage = 'golangci/golangci-lint:v2.13.2'
def nodeImage = 'node:24-alpine'
def flutterImage = 'ghcr.io/cirruslabs/flutter:3.44.0'

pipeline {
  agent any

  options {
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
    disableConcurrentBuilds(abortPrevious: true)
  }

  parameters {
    booleanParam(name: 'DEPLOY', defaultValue: false,
      description: 'Deploy to EKS after a green build')
    choice(name: 'ENVIRONMENT', choices: ['staging', 'production'],
      description: 'Target environment')
    string(name: 'AWS_REGION', defaultValue: 'us-east-1', description: 'AWS region of the cluster')
    string(name: 'SERVICES',
      defaultValue: services.join(','),
      description: 'Services to deploy (comma separated, validated against an allowlist)')
  }

  environment {
    AWS_REGION = "${params.AWS_REGION}"
    ENVIRONMENT = "${params.ENVIRONMENT}"
    SERVICES = "${params.SERVICES}"
    NAMESPACE = 'grupo-mariposa'
    IMAGE_TAG = "${env.GIT_COMMIT}"
    HELM_IMAGE = 'alpine/helm:3.16.2'
    KUBECONFORM_IMAGE = 'ghcr.io/yannh/kubeconform:v0.7.0'
    GO_COVERAGE_MIN = '95'
    TRACKER_COVERAGE_MIN = '100'
    TRACKER_COVERAGE_EXCLUDE = '(^|/)lib/main\\.dart$|/core/platform/web_browser\\.dart$'
  }

  stages {
    stage('Quality gates') {
      parallel {
        stage('products-api lint') {
          agent { docker { image golangciLintImage; reuseNode true } }
          steps {
            dir('products-api') {
              sh 'golangci-lint run ./...'
            }
          }
        }
        stage('products-api') {
          agent { docker { image goImage; reuseNode true } }
          steps {
            dir('products-api') {
              sh 'go build ./...'
              sh 'go test ./... -race -coverprofile=coverage.out -covermode=atomic'
              sh '../scripts/ci/check-go-coverage.sh coverage.out "${GO_COVERAGE_MIN}"'
            }
          }
        }
        stage('clients-api') {
          agent { docker { image nodeImage; reuseNode true } }
          steps {
            dir('clients-api') {
              sh 'npm ci'
              sh 'npm run lint'
              sh 'npm run build'
              sh 'npm run test:cov'
            }
          }
        }
        stage('order-processor') {
          steps {
            dir('order-processor') {
              sh './mvnw -B verify'
            }
          }
          post {
            always {
              junit allowEmptyResults: true,
                testResults: 'order-processor/target/*-reports/*.xml'
            }
          }
        }
        stage('config-server') {
          steps {
            dir('config-server') {
              sh './mvnw -B verify'
            }
          }
          post {
            always {
              junit allowEmptyResults: true,
                testResults: 'config-server/target/surefire-reports/*.xml'
            }
          }
        }
        stage('order-tracker') {
          agent { docker { image flutterImage; reuseNode true } }
          steps {
            dir('order-tracker') {
              sh 'flutter pub get --enforce-lockfile'
              sh 'dart format --line-length 100 --output=none --set-exit-if-changed lib test'
              sh 'flutter analyze'
              sh 'flutter test --coverage'
              sh '''../scripts/ci/check-lcov-coverage.sh coverage/lcov.info \
                "${TRACKER_COVERAGE_MIN}" "${TRACKER_COVERAGE_EXCLUDE}"'''
            }
          }
        }
        stage('helm') {
          steps {
            sh '''HELM_CMD="docker run --rm -v ${WORKSPACE}:/w -w /w ${HELM_IMAGE}" \
              KUBECONFORM_CMD="docker run --rm -i ${KUBECONFORM_IMAGE}" \
              ./scripts/ci/validate-helm.sh'''
          }
        }
      }
    }

    stage('Build images') {
      steps {
        script {
          services.each { service ->
            sh "docker build -t grupo-mariposa/${service}:${IMAGE_TAG} ${service}"
          }
        }
      }
    }

    stage('Scan images') {
      steps {
        script {
          services.each { service ->
            sh "./scripts/ci/scan-image.sh grupo-mariposa/${service}:${IMAGE_TAG}"
          }
        }
      }
    }

    stage('Deploy') {
      when {
        allOf {
          expression { params.DEPLOY }
          branch 'main'
        }
      }
      steps {
        input message: "Deploy ${IMAGE_TAG} to ${params.ENVIRONMENT}?", ok: 'Deploy'
        withCredentials([
          string(credentialsId: 'aws-deploy-role-arn', variable: 'AWS_ROLE_ARN'),
          string(credentialsId: 'ecr-registry', variable: 'ECR_REGISTRY'),
          string(credentialsId: 'eks-cluster-name', variable: 'EKS_CLUSTER')
        ]) {
          sh './scripts/ci/deploy-eks.sh'
        }
      }
    }
  }

  post {
    always {
      cleanWs()
    }
  }
}
