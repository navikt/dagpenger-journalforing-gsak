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
        withDockerRegistry(
          credentialsId: 'repo.adeo.no',
          url: "https://${DOCKER_REPO}"
        ) {
          sh label: 'Build and push Docker image', script: """
            docker build . --pull -t ${DOCKER_IMAGE_VERSION}
            docker push ${DOCKER_IMAGE_VERSION}
          """
        }

        withCredentials([usernamePassword(
          credentialsId: 'repo.adeo.no',
          usernameVariable: 'REPO_USERNAME',
          passwordVariable: 'REPO_PASSWORD'
        )]) {
          sh label: 'Publish service contract', script: """
            sed 's/latest/${VERSION}/' nais.yaml | tee nais.yaml
            curl --user ${REPO_USERNAME}:${REPO_PASSWORD} --upload-file nais.yaml https://repo.adeo.no/repository/raw/nais/${APPLICATION_NAME}/${VERSION}/nais.yaml
          """
        }

        sh label: 'Deploy to non-production', script: """
          kubectl config use-context dev-${env.ZONE}
          kubectl apply -n ${env.NAMESPACE} -f nais.yaml --wait
          kubectl rollout status -w deployment/${APPLICATION_NAME}
        """
      }

      post {
        success {
          archiveArtifacts artifacts: 'nais.yaml', fingerprint: true
        }
      }
    }
  }
}
