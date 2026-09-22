pipeline {
  agent any

  options {
    timestamps()
    timeout(time: 60, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
    disableConcurrentBuilds(abortPrevious: true)
  }

  parameters {
    booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Deploy to EKS after a green build')
    choice(name: 'ENVIRONMENT', choices: ['staging', 'production'], description: 'Target environment')
  }

  environment {
    AWS_REGION = 'us-east-1'
    NAMESPACE = 'grupo-mariposa'
    IMAGE_TAG = "${env.GIT_COMMIT}"
  }

  stages {
    stage('Quality gates') {
      parallel {
        stage('products-api') {
          agent { docker { image 'golang:1.26'; reuseNode true } }
          steps {
            dir('products-api') {
              sh 'go vet ./...'
              sh 'go test ./... -race -coverprofile=coverage.out -covermode=atomic'
              sh 'go tool cover -func=coverage.out | tail -1'
            }
          }
        }
        stage('clients-api') {
          agent { docker { image 'node:24-alpine'; reuseNode true } }
          steps {
            dir('clients-api') {
              sh 'npm ci'
              sh 'npm run lint'
              sh 'npm run test:cov'
              sh 'npm run test:e2e'
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
        stage('order-tracker') {
          agent { docker { image 'ghcr.io/cirruslabs/flutter:stable'; reuseNode true } }
          steps {
            dir('order-tracker') {
              sh 'flutter pub get'
              sh 'flutter analyze'
              sh 'flutter test --coverage'
            }
          }
        }
      }
    }

    stage('Build images') {
      steps {
        script {
          ['products-api', 'clients-api', 'order-processor', 'order-tracker'].each { service ->
            sh "docker build -t grupo-mariposa/${service}:${IMAGE_TAG} ${service}"
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
