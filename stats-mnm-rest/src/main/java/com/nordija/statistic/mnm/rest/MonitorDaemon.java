package com.nordija.statistic.mnm.rest;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nordija.activemq.monitor.BrokerMonitor;
import com.nordija.activemq.monitor.Monitor;
import com.nordija.statistic.monitoring.aggregatorImpl.AggregatorMonitorImpl;

public class MonitorDaemon {
	private static final Logger logger = LoggerFactory.getLogger(MonitorDaemon.class);
	
	public static void main(String[] args){
		try {
            ClassPathXmlApplicationContext context = loadContext();
            
            boolean success = startServices(context);
            if(!success){
            	logger.error("!!! One or more services did not start successfully !!!");
            }
            
            logger.info("All services started successfully.");
            
            daemonize(context);
        } catch (Exception e1) {
            logger.info("Failed to start as daemon", e1);
            System.exit(0);
        }
        while (!shutdownRequested) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                logger.info("Interrupted. Exiting.");
                System.exit(0);
            }
        }
	}
	
	// Methods to controll the daemon process
    private static final String DAEMON_PID_FILE = "daemon-pidfile";
    static protected boolean shutdownRequested = false;
    static private Thread mainThread;

    private static void daemonize(final ApplicationContext context) throws IOException {
        mainThread = Thread.currentThread();

        File pidFile = getPidFile();
        pidFile.deleteOnExit();

        // Detach from standard out and err.
        logger.info("Detaching from stdout and stderr.");
        System.out.close();
        System.err.close();

        // Add shutdown hook
        logger.info("Adding shutdown hook.");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                MonitorDaemon.shutdown(context);
            }
        });

        logger.info("pid file is " + pidFile.getCanonicalPath());
    }
    
    private static File getPidFile() {
        String pid = System.getProperty(DAEMON_PID_FILE);
        if (pid == null) {
            throw new IllegalArgumentException("No " + DAEMON_PID_FILE
                + " specified. Please invoke "
                + MonitorDaemon.class.getName()
                + " with -D"
                + DAEMON_PID_FILE
                + "=<pid-file>");
        }
        return new File(pid);
    }

    static public boolean startServices(ApplicationContext context){
        Monitor brokerMonitor = context.getBean(BrokerMonitor.class);
		logger.info("Testing connection to the aggregator monitor service.");
		if(!brokerMonitor.testConnection()){
			logger.error("ActiveMQ monitor cannot connect to the ActiveMQ broker service. Please check if the broker is running.");
		}
        brokerMonitor.start();
        logger.info("Started all ActiveMQ monitor successfully.");
        
        com.nordija.statistic.monitoring.Monitor aggregatorMonitor = context.getBean(AggregatorMonitorImpl.class);
		logger.info("Testing connection to the aggregator service.");		
		if(!aggregatorMonitor.testConnection()){
			logger.error("Aggregator monitor cannot connect to the aggregator service. Please check if the aggregator is running.");
		}else{
			aggregatorMonitor.start();
			logger.info("Started Aggregator monitor successfully.");
		}
        
		MonitorRestService restService = context.getBean(MonitorRestService.class); 
        try{
        	restService.start();
        }catch(Exception e){
        	logger.error("Failed to start Rest Service. Exiting...", e.getCause());
        }
        logger.info("Started Rest Service successfully.");
        
        return restService.isRunning() && aggregatorMonitor.isRunning() && brokerMonitor.isRunning();
    }
    
    static public void shutdown(ApplicationContext context) {
        logger.info("Shutting down Monitor Daemon services.");
        shutdownRequested = true;
        try {
        	
        	Monitor brokerMonitor = context.getBean(BrokerMonitor.class);
            try {
            	logger.info("Shutting down ActiveMQ monitor.");
            	brokerMonitor.stop();
			} catch (Exception e) {
				logger.error("Failed to shutdown ActiveMQ monitor gracefully", e);
			}

        	com.nordija.statistic.monitoring.Monitor aggregatorMonitor = context.getBean(AggregatorMonitorImpl.class);
            try {
            	logger.info("Shutting down Aggregator monitor.");
            	if(aggregatorMonitor.isRunning()){
            		aggregatorMonitor.stop();
            	}
			} catch (Exception e) {
				logger.error("Failed to shutdown Aggregator monitor gracefully", e);
			}

            try{
            	logger.info("Stopping the Rest Service.");
            	MonitorRestService restService = context.getBean(MonitorRestService.class);
            	restService.stop();
            } catch (Exception e){
            	logger.error("Failed to close down the Rest Service.", e);
            }

            logger.info("Shutting down the daemon process.");
            mainThread.join();
        } catch (InterruptedException e) {
            logger.error("Interrupted which waiting on main daemon thread to complete.");
        }
        logger.info("Monitor services shut down successfully.");
    }
    
    
	private static ClassPathXmlApplicationContext loadContext() throws Exception {
        logger.info("Loading context ....");
        ClassPathXmlApplicationContext context =
            new ClassPathXmlApplicationContext(new String[] { "classpath*:META-INF/spring/applicationContext.xml" });

        return context;
    }
}
