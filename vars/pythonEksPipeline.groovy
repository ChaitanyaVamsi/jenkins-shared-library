def call(Map config){
      pipeline{

          agent any

          // agent{
          //   node{
          //     label 'slave' // roboshop-java app server
          // }

          environment{
            appVersion = ""
            ACC_ID = "471112667143"
            PROJECT = config.get("project")
            COMPONENT = config.get("component")
          }

          options{
            buildDiscarder(logRotator(numToKeepStr: '1', daysToKeepStr: '1'))
            timeout(time:30, unit:'MINUTES')
            disableConcurrentBuilds()
          }

          stages{

            stage('Read Version'){
                steps{
                  script{
                        appVersion = readFile(file: 'version')
                        echo "app version: ${appVersion}"
                  }
                }
            }

            stage('Install Dependencies'){
                steps{
                  script{
                      sh """
                          pip3 install -r requirements.txt
                      """
                  }
                }
            }

            stage('unit Test'){
                steps{
                  script{
                      sh """
                            echo test
                      """
                  }
                }

            }

            stage('Build Image'){
                steps{
                    script{
                        withAWS(region:'us-east-1',credentials:'aws-creds'){
                          sh """
                            aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                            docker build -t ${PROJECT}/${COMPONENT}:${appVersion} .
                            docker tag ${PROJECT}/${COMPONENT}:${appVersion} ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                            docker images
                            docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                          """
                        }
                    }
                }
            }

            stage('Trivy Scan'){
                steps{
                    script{
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
            }

            stage('Trigger Dev Deploy'){
              steps{
                script{
                  build job:"../${COMPONENT}-deploy",
                  wait: false,
                  propogate: false,
                  parameters: [
                    string(name: 'appVersion', value:"${appVersion}")
                    string(name: 'deployTo', value:"dev")
                  ]
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