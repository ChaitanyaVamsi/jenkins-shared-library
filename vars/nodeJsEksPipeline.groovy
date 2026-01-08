def call(Map config){

      pipeline{

        agent any
          // agent{
          //   node{
          //     label 'slave'
          //   }
          // }

          environment{
            COURSE = "Jenkins"
            PROJECT = config.get("project")
            COMPONENT = config.get("component")
            appVersion = ""
            ACC_ID = "471112667143"
          }

          options{
            timeout(time:10, unit: 'MINUTES')
            disableConcurrentBuilds()
          }

          stages{
            // need a plugin for this - Pipeline Utility Steps

            stage('Git Checkout'){

              steps{
              git branch: 'main', url: ' https://github.com/ChaitanyaVamsi/jenkins-catalogue.git '
              }
            }
            stage ('Display Version'){
                steps{
                  script{
                    def packageJSON = readJSON file: 'package.json'
                    appVersion= packageJSON.version // this appVersion is defined in environment
                    echo "app version: ${appVersion}"
                  }
                }
            }

            stage('install dependencies'){
              steps{
                script{
                  sh """
                      npm install
                  """
                }
              }
            }

            stage('Unit Test'){
              steps{
                script{
                  sh """
                    npm test
                  """
                }
              }
            }

            // stage('Sonar Scan'){
            //   environment{
            //     def scannerHome = tool 'sonar-8.0'
            //   }
            //   steps{
            //     script{
            //        withSonarQubeEnv('sonar-server') {
            //         sh '''
            //         #  ${scannerHome}/bin/sonar-scanner -Dsonar.projectName=test -Dsonar.projectKey=test
            //           ${scannerHome}/bin/sonar-scanner
            //           '''
            //        }

            //     }
            //   }
            // }


              //  stage('Dependabot Security Gate') {
              //       environment {
              //           GITHUB_OWNER = 'daws-86s'
              //           GITHUB_REPO  = 'catalogue'
              //           GITHUB_API   = 'https://api.github.com'
              //           GITHUB_TOKEN = credentials('GITHUB_TOKEN')
              //       }

              //       steps {
              //           script{
              //               /* Use sh """ when you want to use Groovy variables inside the shell.
              //               Use sh ''' when you want the script to be treated as pure shell. */
              //               sh """
              //               echo "Fetching Dependabot alerts..."

              //               response=$(curl -s \
              //                   -H "Authorization: token ${GITHUB_TOKEN}" \
              //                   -H "Accept: application/vnd.github+json" \
              //                   "${GITHUB_API}/repos/${GITHUB_OWNER}/${GITHUB_REPO}/dependabot/alerts?per_page=100")

              //               echo "${response}" > dependabot_alerts.json

              //               high_critical_open_count=$(echo "${response}" | jq '[.[]
              //                   | select(
              //                       .state == "open"
              //                       and (.security_advisory.severity == "high"
              //                           or .security_advisory.severity == "critical")
              //                   )
              //               ] | length')

              //               echo "Open HIGH/CRITICAL Dependabot alerts: ${high_critical_open_count}"

              //               if [ "${high_critical_open_count}" -gt 0 ]; then
              //                   echo "❌ Blocking pipeline due to OPEN HIGH/CRITICAL Dependabot alerts"
              //                   echo "Affected dependencies:"
              //                   echo "$response" | jq '.[]
              //                   | select(.state=="open"
              //                   and (.security_advisory.severity=="high"
              //                   or .security_advisory.severity=="critical"))
              //                   | {dependency: .dependency.package.name, severity: .security_advisory.severity, advisory: .security_advisory.summary}'
              //                   exit 1
              //               else
              //                   echo "✅ No OPEN HIGH/CRITICAL Dependabot alerts found"
              //               fi
              //               """

              //           }


            // add aws user creds in credential manager jenkins , you can create the access keys and IDs from users IAM
            stage('Build Image'){
              steps{
                script{
                  withAWS(region:'us-east-1',credentials:'aws-creds') {
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

            stage('Trigger Dev catalogue-deploy'){
              steps{
                script{
                  build job:'catalogue-deploy'
                  wait: false,
                  propogate: false
                }
              }
            }
          }
    }
}