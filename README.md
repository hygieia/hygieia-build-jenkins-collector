Configure the Jenkins Collector to display and monitor information (related to build status) on the Hygieia Dashboard, from Jenkins. Hygieia uses Spring Boot to package the collector as an executable JAR file with dependencies.

# Table of Contents
* [Setup Instructions](#setup-instructions)
* [Sample Application Properties](#sample-application-properties)
* [Run collector with Docker](#run-collector-with-docker)

### Setup Instructions

To configure the Jenkins Collector, execute the following steps:

*	**Step 1 - Artifact Preparation:**

	Please review the two options in Step 1 to find the best fit for you. 

	***Option 1 - Download the artifact:***

	You can download the SNAPSHOTs from the SNAPSHOT directory [here](https://oss.sonatype.org/content/repositories/snapshots/com/capitalone/dashboard/jenkins-build-collector/) or from the maven central repository [here](https://search.maven.org/artifact/com.capitalone.dashboard/jenkins-build-collector).  

	***Option 2 - Build locally:***

	To configure the collector, git clone the [collector repo](https://github.com/Hygieia/hygieia-build-jenkins-collector).  Then, execute the following steps:

	To package the collector source code into an executable JAR file, run the maven build from the `\hygieia-build-jenkins-collector` directory of your source code installation:

	```bash
	mvn install
	```

	The output file `[collector name].jar` is generated in the `hygieia-build-jenkins-collector\target` folder.

	Once you have chosen an option in Step 1, please proceed: 

*   **Step 2: Set Parameters in Application Properties File**

Set the configurable parameters in the `application.properties` file to connect to the Dashboard MongoDB database instance, including properties required by the Jenkins Collector.

To configure parameters for the Jenkins Collector, refer to the sample [application.properties](#sample-application-properties) section.

For information about sourcing the application properties file, refer to the [Spring Boot Documentation](http://docs.spring.io/spring-boot/docs/current-SNAPSHOT/reference/htmlsingle/#boot-features-external-config-application-property-files).

*   **Step 3: Deploy the Executable File**

To deploy the `[collector name].jar` file, change directory to `hygieia-build-jenkins-collector\target`, and then execute the following from the command prompt:

```
java -jar [collector name].jar --spring.config.name=jenkins --spring.config.location=[path to application.properties file]
```

### Sample Application Properties

```properties
# Database Name
dbname=dashboarddb

# Database HostName - default is localhost
dbhost=localhost

# Database Port - default is 27017
dbport=9999

# MongoDB replicaset
dbreplicaset=[false if you are not using MongoDB replicaset]
dbhostport=[host1:port1,host2:port2,host3:port3]

# Database Username - default is blank
dbusername=dashboarduser

# Database Password - default is blank
dbpassword=dbpassword

# Collector schedule (required)
jenkins.cron=0 0/5 * * * *

# The page size
jenkins.pageSize=1000

# The folder depth - default is 10
jenkins.folderDepth=10

# Jenkins server (required) - Can provide multiple
jenkins.servers[0]=http://jenkins.company.com

# If using username/token for API authentication
# (required for Cloudbees Jenkins Ops Center) For example,
jenkins.servers[1]=http://username:token@jenkins.company.com

# Another option: If using same username/password Jenkins auth,
# set username/apiKey to use HTTP Basic Auth (blank=no auth)
jenkins.usernames[0]=
jenkins.apiKeys[0]=

# Determines if build console log is collected - defaults to false
jenkins.saveLog=true
		
# Search criteria enabled via properties (max search criteria = 2) 
jenkins.searchFields[0]= options.jobName
jenkins.searchFields[1]= niceName 

# Timeout values
jenkins.connectTimeout=20000
jenkins.readTimeout=20000
```
## Run collector with Docker

You can install Hygieia by using a docker image from docker hub. This section gives detailed instructions on how to download and run with Docker. 

*	**Step 1: Download**

	Navigate to the docker hub location of your collector [here](https://hub.docker.com/u/hygieiadoc) and download the latest image (most recent version is preferred).  Tags can also be used, if needed.

*	**Step 2: Run with Docker**

	```Docker run -e SKIP_PROPERTIES_BUILDER=true -v properties_location:/hygieia/config image_name```
	
	- <code>-e SKIP_PROPERTIES_BUILDER=true</code>  <br />
	indicates whether you want to supply a properties file for the java application. If false/omitted, the script will build a properties file with default values
	- <code>-v properties_location:/hygieia/config</code> <br />
	if you want to use your own properties file that located outside of docker container, supply the path here. 
		- Example: <code>-v /Home/User/Document/application.properties:/hygieia/config</code>
