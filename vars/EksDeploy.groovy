def call(Map config){

    pipeline{
        agent any

        environment {
          COURSE    = "Jenkins"                      // Training / reference variable (not used in pipeline logic)
          appVersion = config.get("appVersion")
          ACC_ID    = "471112667143"               // AWS Account ID
          PROJECT   = config.get("project")       // Project name (used in EKS cluster naming)
          COMPONENT = config.get("component")    // Application / microservice name
          REGION    = "us-east-1"               // AWS region where EKS clusters exist
          DEPLOY_TO =  config.get("deploy_to")
        }

        options {
          timeout(time: 30, unit: 'MINUTES')    // Abort build if it runs longer than 30 minutes
          disableConcurrentBuilds()            // Prevent multiple builds of this job at the same time
        }

        /* parameters {

          // Application version to deploy (example: 1.0.3, latest, commit-id)
          string(
            name: 'appVersion',
            description: 'Which app version you want to deploy'
          )


          // * This selects the TARGET ENVIRONMENT / EKS CLUSTER.
          // *
          // * dev  -> roboshop-dev  EKS cluster
          // * qa   -> roboshop-qa   EKS cluster
          // * prod -> roboshop-prod EKS cluster
          // *
          // * NOTE:
          // * This parameter DOES NOT select a Git branch.
          // * It is only used to decide which Kubernetes cluster Jenkins connects to.

          choice(
            name: 'deploy_to',
            choices: ['dev', 'qa', 'prod'],
            description: 'Select which EKS cluster to connect to'
          )
        }
         */

        stages{
          stage('Deploy'){
                          steps{
                            script{
                              withAWS(region: 'us-east-1', credentials: 'aws-creds') {
                                    sh """
                                      set -e
                                      aws eks update-kubeconfig --region ${REGION} --name ${PROJECT}-${DEPLOY_TO}
                                      kubectl get nodes
                                      sed -i "s/IMAGE_VERSION/${appVersion}/g" values.yaml
                                      helm upgrade --install ${COMPONENT} -f values-${DEPLOY_TO}.yaml -n ${PROJECT} --atomic --timeout=5m .
                                    """
                              }
                            }
                          }
                      }

          stage('Functiona Testing'){
            when{
              expression { DEPLOY_TO == "dev" }
            }
                  steps{
                    script{
                      sh """
                            echo "functional Testing "
                      """
                    }
                  }
          }
        }

         post{
              always{
                      echo 'I will always say Hello again!'
                      cleanWs()
                  }
                  success {
                      echo 'I will run if success'
                  }
                  failure {
                      echo 'I will run if failure'
                  }
                  aborted {
                      echo 'pipeline is aborted'
                  }
             }

    }

}