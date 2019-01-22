pipeline {
  agent any
  environment {
    DEPLOYMENT = readYaml(file: './nais.yaml')
    APPLICATION_NAME = "${DEPLOYMENT.metadata.name}"
    ZONE = "${DEPLOYMENT.metadata.annotations.zone}"
    NAMESPACE = "${DEPLOYMENT.metadata.namespace}"
    VERSION = sh(label: 'Get git sha1 as version', script: 'git rev-parse --short HEAD', returnStdout: true).trim()
  }

  stages {
    stage('Install dependencies') {
      steps {
        sh './gradlew assemble'
      }
    }

    stage('Build') {
      steps {
        sh './gradlew build'
      }

      post {
        always {
          publishHTML target: [
            allowMissing: true,
            alwaysLinkToLastBuild: false,
            keepAll: true,
            reportDir: 'build/reports/tests/test',
            reportFiles: 'index.html',
            reportName: 'Test coverage'
          ]

          junit 'build/test-results/test/*.xml'
        }
      }
    }

    stage('Deploy') {
      when { branch 'master' }

      environment {
        DOCKER_REPO = 'repo.adeo.no:5443'
        DOCKER_IMAGE_VERSION = '${DOCKER_REPO}/${APPLICATION_NAME}:${VERSION}'
      }

      steps {
        // Build and push Docker image
        withDockerRegistry(
          credentialsId: 'repo.adeo.no',
          url: "https://${DOCKER_REPO}"
        ) {
          sh label: 'Build Docker Image', script: "docker build . --pull -t ${DOCKER_IMAGE_VERSION}"
          sh label: 'Push Docker Image', script: "docker push ${DOCKER_IMAGE_VERSION}; :"
        }

        // Publish service contract
        withCredentials([usernamePassword(
          credentialsId: 'repo.adeo.no',
          usernameVariable: 'REPO_USERNAME',
          passwordVariable: 'REPO_PASSWORD'
        )]) {
          sh label: 'Replace latest with git sha1',
            script: "sed 's/latest/${VERSION}/' nais.yaml | tee nais.yaml"
          sh label: 'Upload nais.yaml to repository',
            script: "curl --user ${REPO_USERNAME}:${REPO_PASSWORD} --upload-file nais.yaml https://repo.adeo.no/repository/raw/nais/${APPLICATION_NAME}/${VERSION}/nais.yaml"
        }

        // Deploy to non-production
        script {
          sh label: 'Change context for kubectl', script: "kubectl config use-context dev-${env.ZONE}"
          sh label: 'Apply new deployment', script: "kubectl apply -n ${env.NAMESPACE} -f nais.yaml --wait"
          sh label: 'Wait for deployment to complete', script: "kubectl rollout status -w deployment/${APPLICATION_NAME}"
        }
      }

      post {
        success {
          archiveArtifacts artifacts: 'nais.yaml', fingerprint: true
        }
      }
    }
  }
}
