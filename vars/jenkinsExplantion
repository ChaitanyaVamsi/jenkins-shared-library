/*
 * Shared Library entry point
 * --------------------------
 * This function is called from a Jenkinsfile like:
 *
 * catalogueCI(
 *   project: 'roboshop',
 *   component: 'catalogue'
 * )
 */
def call(Map config) {

  pipeline {

    /*
     * Agent
     * -----
     * `agent any` allows Jenkins to run this pipeline on any available worker.
     * A specific slave node can be enforced if needed (commented below).
     */
    agent any
    // agent {
    //   node {
    //     label 'slave'
    //   }
    // }

    /*
     * Environment variables
     * ---------------------
     * These are available across all stages.
     * Values for PROJECT and COMPONENT are injected via the shared library config.
     */
    environment {
      COURSE    = "Jenkins"                   // Reference / training variable
      PROJECT   = config.get("project")       // Project name (passed from Jenkinsfile)
      COMPONENT = config.get("component")     // Service name (passed from Jenkinsfile)
      appVersion = ""                         // Will be populated from package.json
      ACC_ID    = "471112667143"               // AWS Account ID
    }

    /*
     * Pipeline options
     */
    options {
      timeout(time: 10, unit: 'MINUTES')      // Stop pipeline if it runs too long
      disableConcurrentBuilds()               // Prevent parallel runs of same job
    }

    stages {

      /*
       * Stage: Git Checkout
       * -------------------
       * Pulls application source code from GitHub.
       * Currently hardcoded to `main` branch.
       *
       * NOTE:
       * This stage does NOT depend on environment (dev/qa/prod).
       */
      stage('Git Checkout') {
        steps {
          git branch: 'main',
              url: 'https://github.com/ChaitanyaVamsi/jenkins-catalogue.git'
        }
      }

      /*
       * Stage: Display Version
       * ----------------------
       * Reads application version from package.json
       * and stores it in `appVersion` environment variable.
       *
       * This version is later used:
       * - As Docker image tag
       * - For traceability
       *
       * Requires: Pipeline Utility Steps plugin (readJSON)
       */
      stage('Display Version') {
        steps {
          script {
            def packageJSON = readJSON file: 'package.json'
            appVersion = packageJSON.version
            echo "Application version: ${appVersion}"
          }
        }
      }

      /*
       * Stage: Install Dependencies
       * ---------------------------
       * Installs Node.js dependencies required to build and test the app.
       */
      stage('Install Dependencies') {
        steps {
          sh """
            npm install
          """
        }
      }

      /*
       * Stage: Unit Test
       * ----------------
       * Runs application unit tests.
       * Pipeline will FAIL if tests fail.
       */
      stage('Unit Test') {
        steps {
          sh """
            npm test
          """
        }
      }

      /*
       * (Optional) SonarQube Scan
       * -------------------------
       * Commented for now.
       * Used for static code analysis and quality gates.
       */
      // stage('Sonar Scan') { ... }

      /*
       * Stage: Build Image
       * ------------------
       * - Logs into AWS ECR
       * - Builds Docker image
       * - Tags image using appVersion
       * - Pushes image to ECR
       *
       * Requires:
       * - aws-creds configured in Jenkins credentials
       * - ECR repository already created
       */
      stage('Build Image') {
        steps {
          script {
            withAWS(region: 'us-east-1', credentials: 'aws-creds') {
              sh """
                aws ecr get-login-password --region us-east-1 \
                | docker login --username AWS --password-stdin \
                  ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com

                docker build -t ${PROJECT}/${COMPONENT}:${appVersion} .

                docker tag ${PROJECT}/${COMPONENT}:${appVersion} \
                  ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}

                docker images

                docker push \
                  ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
              """
            }
          }
        }
      }

      /*
       * Stage: Trivy Scan
       * -----------------
       * Scans Docker image for vulnerabilities.
       *
       * Pipeline FAILS if:
       * - HIGH or CRITICAL vulnerabilities are found
       *
       * This acts as a security gate.
       */
      stage('Trivy Scan') {
        steps {
          sh """
            trivy image \
              --scanners vuln \
              --severity HIGH,CRITICAL,MEDIUM \
              --pkg-types os \
              --exit-code 1 \
              --format table \
              --no-progress \
              ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
          """
        }
      }

      /*
       * Stage: Trigger Dev Deployment
       * -----------------------------
       * Triggers downstream deployment job asynchronously.
       *
       * - Does NOT wait for deployment to finish
       * - Does NOT fail this pipeline if deploy fails
       *
       * This separates CI from CD.
       */
      stage('Trigger Dev catalogue-deploy') {
        steps {
          script {
            build job: 'catalogue-deploy',
                  wait: false,
                  propagate: false
          }
        }
      }
    }
  }
}
