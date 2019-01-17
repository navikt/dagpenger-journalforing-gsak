pipeline {
  agent any
  environment {
    APPLICATION_NAME = 'dagpenger-journalforing-gsak'
    ZONE = 'fss'
    NAMESPACE = 'default'
    VERSION = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    DOCKER_REPO = 'repo.adeo.no:5443/'
    DOCKER_IMAGE_VERSION = '${DOCKER_REPO}${APPLICATION_NAME}:${VERSION}'
  }

  stages {
    stage('Install dependencies') {
      steps {
        sh "./gradlew assemble"
      }
    }

    stage('Build') {
      steps {
        sh "./gradlew build"
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

    stage('Publish') {
      when { branch 'master' }

      steps {
        withCredentials([usernamePassword(
          credentialsId: 'repo.adeo.no',
          usernameVariable: 'REPO_USERNAME',
          passwordVariable: 'REPO_PASSWORD'
        )]) {
            sh "docker login -u ${REPO_USERNAME} -p ${REPO_PASSWORD} repo.adeo.no:5443"
        }

        script {
          sh "docker build . --pull -t ${DOCKER_IMAGE_VERSION}"
          sh "docker push ${DOCKER_IMAGE_VERSION}"
        }
      }
    }

    stage("Publish service contract") {
      when { branch 'master' }

      steps {
        withCredentials([usernamePassword(
          credentialsId: 'repo.adeo.no',
          usernameVariable: 'REPO_USERNAME',
          passwordVariable: 'REPO_PASSWORD'
        )]) {
          sh "curl -vvv --user ${REPO_USERNAME}:${REPO_PASSWORD} --upload-file nais.yaml https://repo.adeo.no/repository/raw/nais/${APPLICATION_NAME}/${VERSION}/nais.yaml"
        }
      }
    }

    stage('Deploy to non-production') {
      when { branch 'master' }

      steps {
        script {
          sh "kubectl config use-context preprod-${env.ZONE}"
          sh "kubectl apply -n ${env.NAMESPACE} -f nais.yaml"
          sh "kubectl rollout status -w deployment/${APPLICATION_NAME}"
        }
      }
    }
  }
}
