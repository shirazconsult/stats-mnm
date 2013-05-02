#!/bin/bash
#
# Init file for the stats-mnm-agent module of the Nordija's statistics
#
# chkconfig: 2345 55 35
# description: Nordija's statistic Monitoring and Management module
# Farhad Dehghani (farhad@shirazconsult.com) - 04-April-2013
#
# Source function library.
#. /etc/init.d/functions

ARGS=()
DARGS=()
for arg in $@
do
  a=${arg//+/ }
  if [[ $a == "-D"* ]]; then
    DARGS+=("$a")
  else
    ARGS+=("$a")
  fi
done

# ################## 
# Setting basic variables
# ################## 
JAVA=/usr/bin/java
if [ -z "$STATISTICS_HOME" ]; then
	absolute_path=$(cd `dirname "$0"` && pwd)
	export STATISTICS_HOME=`dirname $absolute_path`
	echo "STATISTICS_HOME is not set. Setting it to $STATISTICS_HOME"
fi


# ################## 
# Setting classpath
# ################## 
CLASSPATH="$STATISTICS_HOME/lib/*"
CLASSPATH="$CLASSPATH:`ls $STATISTICS_HOME/modules/stats-mnm-agent-*.jar`:`ls $STATISTICS_HOME/modules/stats-monitor-*.jar`:`ls $STATISTICS_HOME/modules/nordija-amq-admin-*.jar`";

# ################## 
# Setting java options
# ################## 
#JAVA_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=y"
JAVA_OPTS="$JAVA_OPTS -XX:MaxPermSize=256m -Xms512M -Xmx2048M"
JAVA_OPTS="$JAVA_OPTS -Ddaemon-pidfile=${pidfile} -Dconf.dir=file://${STATISTICS_HOME}/conf -Djava.util.logging.config.file=file://${STATISTICS_HOME}/conf/jul-logging.properties -Dlogback.configurationFile=file://${STATISTICS_HOME}/conf/logback-mnm-agent.xml -DSTATISTICS_HOME=${STATISTICS_HOME} "${DARGS[@]}""

myjar=`ls ${STATISTICS_HOME}/modules/stats-mnm-agent-*.jar`
$JAVA $JAVA_OPTS -classpath $CLASSPATH com.nordija.statistic.mnm.stats.livedatasimulation.LiveStatsDataSimulator "${ARGS[@]}" -jar $myjar 
