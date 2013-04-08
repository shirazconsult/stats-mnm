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
# pid file
# ##################
pidfile=${STATISTICS_HOME}/data/mnm-agent-daemon.pid

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
#$JAVA $JAVA_OPTS -classpath $CLASSPATH com.nordija.statistic.mnm.agent.MonitorAgent "${ARGS[@]}" -jar ${STATISTICS_HOME}/modules/$myjar

launch_daemon() {
  /bin/bash <<EOF
        $JAVA $JAVA_OPTS -classpath $CLASSPATH com.nordija.statistic.mnm.agent.MonitorAgent "${ARGS[@]}" -jar $myjar 2>&1 >> /dev/null &
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
                        echo "Monitor Agent is already running. Exiting."
                                exit 0
                        fi
         fi

        echo "Starting Monitor Agent for Nordija Statistics ..."
        daemon_pid=$(launch_daemon)
        echo "Monitor Agent process id is $daemon_pid"
        echo $daemon_pid > $pidfile
        #if ps -p "${daemon_pid}" > /dev/null 2>&1
        if pgrep -F $pidfile > /dev/null ; then
                # daemon is running.                
                echo "Please check the $STATISTICS_HOME/log/mnm-agent.log file to make sure that the MonitorAgent has started successfully."
        else
                echo "Monitor Agent did not start. Please check the log file ${STATISTICS_HOME}/log/mnm-agent.log"
                rm -f $pidfile
        fi
}

stop() {
        echo "Shutting down Monitor Agent (${pidfile}) ..."
        kill `cat ${pidfile}`
        rm -f $pidfile
        echo "Please wait for the Monitor Agent to finish it's job..."
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
