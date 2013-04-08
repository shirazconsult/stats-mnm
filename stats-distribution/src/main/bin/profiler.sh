#!/bin/bash
#
# Init file for the profiler module of the Nordija's statistics
#
# description: Nordija's statistic profiler module
# Farhad Dehghani (farhad@shirazconsult.com) - 24-August-2012
#

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
CLASSPATH="$CLASSPATH:`ls $STATISTICS_HOME/modules/stats-profiler-*.jar`:`ls $STATISTICS_HOME/modules/stats-monitor-*.jar`:`ls $STATISTICS_HOME/modules/nordija-amq-admin-*.jar`";

# ################## 
# Setting java options
# ################## 
JAVA_OPTS="-XX:MaxPermSize=256m -Xms512M -Xmx2048M"
JAVA_OPTS="$JAVA_OPTS -Dconf.dir=file://${STATISTICS_HOME}/conf -Dlogback.configurationFile=file://${STATISTICS_HOME}/conf/logback.xml -DSTATISTICS_HOME=${STATISTICS_HOME} "${DARGS[@]}""

myjar=`ls ${STATISTICS_HOME}/modules/stats-profiler-*.jar`
$JAVA $JAVA_OPTS -classpath $CLASSPATH com.nordija.statistic.profiler.StatisticProfiler "${ARGS[@]}" -jar ${STATISTICS_HOME}/modules/$myjar
