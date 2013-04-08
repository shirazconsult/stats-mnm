#!/bin/bash
#
# Init file for the aggregator module of the Nordija's statistics
#
# chkconfig: 2345 55 25
# description: Nordija's statistic module
# Farhad Dehghani (farhad@shirazconsult.com) - 24-August-2012
#
# Source function library.
#. /etc/init.d/functions

# ##################
# If this script is invoked outside the distribution (f.ex. from /etc/init.d)
# then make sure that the STATISTICS_HOME is set correctly.
# ##################
STATISTICS_HOME=/usr/local/fokuson/statistics/

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
# pid file
# ##################
pidfile=${STATISTICS_HOME}/data/aggregator-daemon.pid

# ##################
# JMX_PORT
# If not defined in aggregator.conf, then default to 1089
# if running multiple aggregators on the same server then make sure
# that the  jmx port is set differently for each aggregator.
# ##################
jmxportdef=`cat ${STATISTICS_HOME}/conf/aggregator.conf | grep JMX_PORT=`
JMX_PORT=${jmxportdef:9}
if [ -z $JMX_PORT ]; then
  JMX_PORT=1089
fi
echo "JMX_PORT is $JMX_PORT"

# ################## 
# Setting classpath
# ################## 
CLASSPATH="$CLASSPATH:$STATISTICS_HOME/lib/*"
CLASSPATH="$CLASSPATH:`ls $STATISTICS_HOME/modules/stats-aggregator-*.jar`:`ls $STATISTICS_HOME/modules/stats-common-*.jar`:`find $STATISTICS_HOME/modules/ -name stats-\*persister\*.jar -exec echo -n {}':' \;`";

# ################## 
# Setting java options
# ################## 
JAVA_JMX_OPTS="$JAVA_OPTS -Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.port=$JMX_PORT -Dorg.apache.camel.jmx.rmiConnector.registryPort=$JMX_PORT"
JAVA_MEM_OPTS="-XX:MaxPermSize=256m -Xms5124K -Xmx2048M"
JAVA_OPTS="$JAVA_MEM_OPTS $JAVA_JMX_OPTS -Ddaemon-pidfile=${pidfile}"
JAVA_OPTS="$JAVA_OPTS -Dconf.dir=file://${STATISTICS_HOME}/conf -Dlogback.configurationFile=file://${STATISTICS_HOME}/conf/logback.xml -DSTATISTICS_HOME=${STATISTICS_HOME}"

myjar=`ls ${STATISTICS_HOME}/modules/stats-aggregator-*.jar`
launch_daemon() {
  /bin/bash <<EOF
        $JAVA $JAVA_OPTS -classpath $CLASSPATH com.nordija.statistic.AggregatorDaemon -jar $myjar 2>&1 >> /dev/null &
    pid=\$!
    echo \${pid}
EOF
}

# Returns 0 if the process with PID $1 is running.
function checkProcessIsRunning {
   if [ -z "$1" -o "$1" == " " ]; then return 1; fi
   ps $1 >$-
   if [ $? -ne 0 ]; then return 1; fi
   return 0
}

start() {
        if [ -f $pidfile ]
                then
                        checkProcessIsRunning `cat $pidfile`
                        if [ $? -ne 0 ]; then
                                rm -f $pidfile
                        else
                        echo "AggregatorDaemon is already running. Exiting."
                                exit 0
                        fi
         fi

        echo "Starting Statistics Aggregator Daemon ..."
        daemon_pid=$(launch_daemon)
        echo "Aggregator process id is $daemon_pid"
        echo $daemon_pid > $pidfile
        #if ps -p "${daemon_pid}" > /dev/null 2>&1
        if pgrep -F $pidfile > /dev/null ; then
                # daemon is running.                
                echo "Please check the $STATISTICS_HOME/log/aggregator.log file to make sure that the Aggregator Daemon has started successfully."
        else
                echo "Aggregator Daemon did not start. Please check the log file ${STATISTICS_HOME}/log/aggregator.log"
                rm -f $pidfile
        fi
}

stop() {
        echo "Shutting down Statistics Aggregator Daemon (${pidfile}) ..."
        kill `cat ${pidfile}`
        rm -f $pidfile
        echo "Please wait for the Aggregator to finish it's job..."
}

case "$1" in
  start)
        start
        ;;
  stop)
        stop
        ;;
  restart)
        stop
        sleep 10
        start
        ;;
  *)
        echo "Usage: $0 {start|stop|restart}"
esac

exit 0
