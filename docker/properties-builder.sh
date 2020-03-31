#!/bin/bash

if [ "$SKIP_PROPERTIES_BUILDER" = true ]; then
  echo "Skipping properties builder"
  exit 0
fi

# mongo container provides the HOST/PORT
# api container provided DB Name, ID & PWD

if [ "$TEST_SCRIPT" != "" ]
then
        #for testing locally
        PROP_FILE=application.properties
else 
	PROP_FILE=config/hygieia-teamcity-build-collector.properties
fi
  
if [ "$MONGO_PORT" != "" ]; then
	# Sample: MONGO_PORT=tcp://172.17.0.20:27017
	MONGODB_HOST=`echo $MONGO_PORT|sed 's;.*://\([^:]*\):\(.*\);\1;'`
	MONGODB_PORT=`echo $MONGO_PORT|sed 's;.*://\([^:]*\):\(.*\);\2;'`
else
	env
	echo "ERROR: MONGO_PORT not defined"
	exit 1
fi

echo "MONGODB_HOST: $MONGODB_HOST"
echo "MONGODB_PORT: $MONGODB_PORT"


#update local host to bridge ip if used for a URL
DOCKER_LOCALHOST=
echo $TEAMCITY_MASTER|egrep localhost >>/dev/null
if [ $? -ne 1 ]
then
	#this seems to give a access to the VM of the docker-machine
	#LOCALHOST=`ip route|egrep '^default via'|cut -f3 -d' '`
	#see http://superuser.com/questions/144453/virtualbox-guest-os-accessing-local-server-on-host-os
	DOCKER_LOCALHOST=10.0.2.2
	MAPPED_URL=`echo "$TEAMCITY_MASTER"|sed "s|localhost|$DOCKER_LOCALHOST|"`
	echo "Mapping localhost -> $MAPPED_URL"
	TEAMCITY_MASTER=$MAPPED_URL
fi

echo $TEAMCITY_OP_CENTER|egrep localhost >>/dev/null
if [ $? -ne 1 ]
then
	#this seems to give a access to the VM of the docker-machine
	#LOCALHOST=`ip route|egrep '^default via'|cut -f3 -d' '`
	#see http://superuser.com/questions/144453/virtualbox-guest-os-accessing-local-server-on-host-os
	LOCALHOST=10.0.2.2
	MAPPED_URL=`echo "$TEAMCITY_OP_CENTER"|sed "s|localhost|$LOCALHOST|"`
	echo "Mapping localhost -> $MAPPED_URL"
	TEAMCITY_OP_CENTER=$MAPPED_URL
fi

cat > $PROP_FILE <<EOF
#Database Name
dbname=${HYGIEIA_API_ENV_SPRING_DATA_MONGODB_DATABASE:-dashboarddb}

#Database HostName - default is localhost
dbhost=${MONGODB_HOST:-10.0.1.1}

#Database Port - default is 27017
dbport=${MONGODB_PORT:-27017}

#Database Username - default is blank
dbusername=${HYGIEIA_API_ENV_SPRING_DATA_MONGODB_USERNAME:-dashboarduser}

#Database Password - default is blank
dbpassword=${HYGIEIA_API_ENV_SPRING_DATA_MONGODB_PASSWORD:-dbpassword}

#Collector schedule (required)
teamcity.cron=${TEAMCITY_CRON:-0 0/5 * * * *}

#A comma seperated list of teamcity projectids
teamcity.projectIds=${TEAMCITY_PROJECT_IDS}

# The folder depth - default is 10
teamcity.folderDepth=${TEAMCITY_FOLDER_DEPTH:-10}

#Teamcity server (required) - Can provide multiple
#teamcity.servers[0]=http://teamcity.company.com
#teamcity.niceNames[0]=[YourTeamcity]
#teamcity.environments[0]=[DEV,QA,INT,PERF,PROD]
#Another option: If using same username/password Teamcity auth - set username/apiKey to use HTTP Basic Auth (blank=no auth)
#teamcity.usernames[0]=user
#teamcity.apiKeys[0]=12345

EOF

# find how many teamcity urls are configured
max=$(wc -w <<< "${!TEAMCITY_MASTER*}")

# loop over and output the url, username, apiKey and niceName
i=0
while [ $i -lt $max ]
do
	if [ $i -eq 0 ]
	then
		server="TEAMCITY_MASTER"
		username="TEAMCITY_USERNAME"
		apiKey="TEAMCITY_API_KEY"
		niceName="TEAMCITY_NAME"
	else
		server="TEAMCITY_MASTER$i"
		username="TEAMCITY_USERNAME$i"
		apiKey="TEAMCITY_API_KEY$i"
		niceName="TEAMCITY_NAME$i"
	fi
	
cat >> $PROP_FILE <<EOF
teamcity.servers[${i}]=${!server}
teamcity.usernames[${i}]=${!username}
teamcity.apiKeys[${i}]=${!apiKey}
teamcity.niceNames[${i}]=${!niceName}

EOF
	
	i=$(($i+1))
done

cat >> $PROP_FILE <<EOF
#Determines if build console log is collected - defaults to false
teamcity.saveLog=${TEAMCITY_SAVE_LOG:-true}

#map the entry localhost so URLS in teamcity resolve properly
# Docker NATs the real host localhost to 10.0.2.2 when running in docker
# as localhost is stored in the JSON payload from teamcity we need
# this hack to fix the addresses
teamcity.dockerLocalHostIP=${DOCKER_LOCALHOST}

EOF

if [ "$TEAMCITY_OP_CENTER" != "" ]
then

	cat >> $PROP_FILE <<EOF
#If using username/token for api authentication (required for Cloudbees Jenkins Ops Center) see sample
#teamcity.servers[${max}]=${TEAMCITY_OP_CENTER:-http://username:token@teamcity.company.com}
teamcity.servers[${max}]=${TEAMCITY_OP_CENTER}
EOF

fi


echo "

===========================================
Properties file created `date`:  $PROP_FILE
Note: passwords & apiKey hidden
===========================================
`cat $PROP_FILE |egrep -vi 'password|apiKey'`
"

exit 0
