import java.text.SimpleDateFormat

def target_cluster_flags = ""
def applicationString = "${LIST}"
def applicationStringConf = "${LIST_CONF}"
def applications = applicationString.tokenize(";")
def applicationsConf = applicationStringConf.tokenize(";")
def openShiftCLI = "/usr/bin/oc"

pipeline
{
    agent { label 'master' }

    options
    {
        buildDiscarder(logRotator(numToKeepStr: '5'))
    }
    stages
    {
        stage('CleanWS')
        {
            steps
            {
                script
                { deleteDir() }
            }
        }
        stage('prepare')
        {
            steps
            {
                script
                {
                    if (!"${BUILD_BRANCH}"?.trim())
                    {
                        currentBuild.result = 'ABORTED'
                        error('Tag to build is empty')
                    }
                    echo "Releasing branch ${BUILD_BRANCH}"
                    echo "${GIT_CREDENTIAL_ID}"
                    echo "${APP_GIT_URL}"
                    target_cluster_flags = "--server=$ocp_cluster_url --insecure-skip-tls-verify"
                }
            }
        }       
        stage('Source checkout')
        {
            steps
            {
                checkout(
                        [$class                              : 'GitSCM', branches: [[name: "${BUILD_BRANCH}"]],
                            doGenerateSubmoduleConfigurations: false,
                            extensions                       : [],
                            submoduleCfg                     : [],
                            userRemoteConfigs                : [[credentialsId: "${GIT_CREDENTIAL_ID}", url: "${APP_GIT_URL}"]]]
                        )
            }
        }
        stage('Read Pom')
        {
            steps
            {
                script
                {
                    pom = readMavenPom file: 'pom.xml'
                    echo "Artificat ID: ${pom.artifactId}"
                    echo "Version: ${pom.version}"
                    echo "Group ID: ${pom.groupId}"                    
                    echo "NAMESPACE ${NAMESPACE} ..."
                }
            }
        }
        stage('Maven setup')
        {
            steps
            {
                echo "starting build"
                withMaven(maven: 'mvn', mavenSettingsConfig: 'aec5edb6-092f-44a8-af49-f8a7b413024c')
                { sh "mvn -X -f ${POM_FILE} versions:set -DnewVersion=${pom.version}" }
            }
        }
        stage('Build')
        {
            tools
            {
                jdk "JDK"
                maven "mvn"
            }
            steps
            {
                withMaven(maven: 'mvn', mavenSettingsConfig: 'aec5edb6-092f-44a8-af49-f8a7b413024c')
                { sh "mvn -f ${POM_FILE} clean install -Dmaven.javadoc.skip=true -DskipTests " }
            }
        }
        stage('Parallel Stage: Unit Test and SonarQube')
        {
            parallel
            {
                stage('SonarQube analysis')
                {
                    steps
                    {
                        script
                        {
                            if(Boolean.parseBoolean(env.SONARQUBE))
                            {
                                withSonarQubeEnv('Sonarqube')
                                {
                                    withMaven(maven: 'mvn',mavenSettingsFilePath: "${MVN_SETTINGS}")
                                    { sh "mvn -f ${POM_FILE} sonar:sonar" }
                                }
                            }
                            else
                            {
                                echo "SonarQube analysis skipped"
                            }
                        }
                    }
                }
                stage('Run Maven Tests')
                {
                    tools
                    {
                        jdk "JDK"
                        maven "mvn"
                    }
                    steps
                    {
                        script
                        {
                            if(Boolean.parseBoolean(env.MVN_TESTS))
                            {
                                withMaven(maven: 'mvn',mavenSettingsFilePath: "${MVN_SETTINGS}")
                                { sh "mvn -f ${POM_FILE} test" }
                            }
                            else
                            {
                                echo "Maven tests skipped"
                            }
                        }
                    }
                }
            }
        }
		stage('Bake')
        {
			stages
            {
				stage('Prepare')
                {
					steps
                    {
                        script
                        {
                            def folder = "";
                            try
                            {
                                folder = "${POM_FILE}".split("/pom.xml")[0];
                                echo "Folder is $folder"
								if(folder == 'pom.xml')
                                {
                                    folder = "";								
                                }
                            }
                            catch(e)
                            {
                            }
                            sh """
                            rm -rf ${WORKSPACE}/s2i-binary
                            mkdir -p ${WORKSPACE}/s2i-binary/configuration
                            mv ${WORKSPACE}/${folder}/target/${pom.artifactId}-${pom.version}.jar ${WORKSPACE}/s2i-binary                         
                           """
                            withCredentials([string(credentialsId: "$OPENSHIFT_SERVICE_TOKEN", variable: 'SECRET')])
                            {

                                def buildconfigUpdateResult = sh(script: "${openShiftCLI} patch bc ${APPLICATION_NAME_BUILD}  -p '{\"spec\":{\"output\":{\"to\":{\"kind\":\"ImageStreamTag\",\"name\":\"${APPLICATION_NAME_BUILD}:${pom.version}\"}}}}' --namespace=${NAMESPACE} -o json --token=$SECRET $target_cluster_flags | ${openShiftCLI} replace ${APPLICATION_NAME_BUILD} --namespace=${NAMESPACE} $target_cluster_flags --token=$SECRET -f -",returnStdout: true)
                                if (!buildconfigUpdateResult?.trim())
                                {
                                    currentBuild.result = 'ERROR'
                                    error('BuildConfig update finished with errors')
                                }
                                echo "Patch BuildConfig result: $buildconfigUpdateResult"
                            }
                        }
                    }             
				}
				stage('s2i binary deploy')
                {
                    steps
                    {
                        script
                        {
                            withCredentials([string(credentialsId: "$OPENSHIFT_SERVICE_TOKEN", variable: 'SECRET')])
                            {
                                def binaryDeployResult = sh(script: "${openShiftCLI} start-build ${APPLICATION_NAME_BUILD}  --from-dir=${WORKSPACE}/s2i-binary/ --namespace=${NAMESPACE} --token=$SECRET $target_cluster_flags --follow",
                                returnStdout: true)
                                if (!binaryDeployResult?.trim())
                                {
                                    currentBuild.result = 'ERROR'
                                    error('Binary deploy finished with errors')
                                }
                                echo "s2i binary deploy result: $binaryDeployResult"
                            }
                        }
                    }
                }
			}
		}
		stage('Deploy')
        {
			stages
            {
				stage('Deploy ConfigMap')
                {
                    steps
                    {
                        script
                        {
                            withCredentials([string(credentialsId: "$OPENSHIFT_SERVICE_TOKEN", variable: 'SECRET')])
                            {
                                applicationsConf.each
                                { app ->
                                    echo "Executing ${openShiftCLI} create ConfigMap ${app}-v${pom.version} ..."
                                    sh(script: "${openShiftCLI} create -f ${WORKSPACE}/${PATH_CONF}/Config_map/${app}.yml -o yaml  --namespace=${NAMESPACE} $target_cluster_flags --token=$SECRET || ${openShiftCLI} replace --namespace=${NAMESPACE} $target_cluster_flags --token=$SECRET -f ${WORKSPACE}/${PATH_CONF}/Config_map/${app}.yml")
                                }
                            }
                        }
                    }
                }
				stage('Deploy DeploymentConfig')
				{
					steps
					{
						script
						{
							withCredentials([string(credentialsId: "$OPENSHIFT_SERVICE_TOKEN", variable: 'SECRET')])
							{
								if(Boolean.parseBoolean(env.OVERWRITE_DEPLOYMENTCONFIG))
								{
									applications.each
									{ app ->
										echo "Executing ${openShiftCLI} create e replace DeploymentConfig ${app}-v${pom.version}- ${PATH_CONF} ..."
										sh(script: "${openShiftCLI} create -f ${WORKSPACE}/${PATH_CONF}/Deployment_config/${app}.yml -o yaml  --namespace=${NAMESPACE} $target_cluster_flags --token=$SECRET || ${openShiftCLI} replace --namespace=${NAMESPACE} $target_cluster_flags --token=$SECRET -f ${WORKSPACE}/${PATH_CONF}/Deployment_config/${app}.yml")
									}
								}
								else
								{
									applications.each
									{ app -> echo "DeploymentConfig is up to date ..."  }
								}
							}
						}
					}
				}
				stage('Rollout')
                {
                    steps
                    {
                        script
                        {
                            withCredentials([string(credentialsId: "$OPENSHIFT_SERVICE_TOKEN", variable: 'SECRET')])
                            {
                                applications.each
                                { app ->
                                    def patchImageStream = sh(script: "${openShiftCLI} set image dc/${app} ${app}=${APPLICATION_NAME_BUILD}:${pom.version} --source=imagestreamtag --namespace=${NAMESPACE} --token=$SECRET $target_cluster_flags",
                                    returnStdout:true)
                                    if (!patchImageStream?.trim())
                                    {
                                        def currentImageStreamVersion = sh(script: "${openShiftCLI} get dc ${app} -o jsonpath='{.spec.template.spec.containers[0].image}' --namespace=${NAMESPACE} --token=$SECRET $target_cluster_flags",
                                        returnStdout:true)
                                    }
                                }
                                applications.each
                                { app ->
                                    def rollout = sh(script: "${openShiftCLI} rollout latest ${app} --namespace=${NAMESPACE} --token=$SECRET $target_cluster_flags ; sleep 5",
                                    returnStdout: true)
                                    if (!rollout?.trim())
                                    {
                                        currentBuild.result = 'ERROR'
                                        error('Rollout finished with errors')
                                    }
                                }
                            }
                        }
                    }
                }
				
			}
		}			
    }
}
