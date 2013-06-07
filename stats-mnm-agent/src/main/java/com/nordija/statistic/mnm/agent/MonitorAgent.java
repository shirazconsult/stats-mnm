package com.nordija.statistic.mnm.agent;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.nordija.activemq.monitor.BrokerMonitor;
import com.nordija.activemq.monitor.Monitor;
import com.nordija.statistic.mnm.stats.StreamingStatsDataProcessor;
import com.nordija.statistic.monitoring.aggregatorImpl.AggregatorMonitorImpl;

public class MonitorAgent {
	private static final Logger logger = LoggerFactory.getLogger(MonitorAgent.class);
	
	public static void main(String[] args){
		try {
            ClassPathXmlApplicationContext context = loadContext();
            
            boolean noDaemon = false;
            List<String> services = null;
            for (String arg : args) {
            	String[] ar = arg.split(" ");
            	for (int i = 0; i < ar.length; i++) {					
            		if(ar[i].equalsIgnoreCase("--no-daemon")){
            			noDaemon = true;					
            		}else if(ar[i].startsWith("--services=")){
            			String s = arg.substring(arg.indexOf("=")+1);
            			String[] split = s.split(",");
            			services = Arrays.asList(split);
            		}
				}
			}
            if(CollectionUtils.isEmpty(services)){
            	logger.error("No Services are being started. Please define services as a comma separated list. " +
            			"Ex. --services=[REST,AMQ_MONITOR,AGG_MONITOR,STATS_DATA_PROCESSOR]");
            	System.exit(0);
            }
            startServices(context, noDaemon, services);
            
            if(!noDaemon){
            	daemonize(context);
            }
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
                MonitorAgent.shutdown(context);
            }
        });

        logger.info("pid file is " + pidFile.getCanonicalPath());
    }
    
    private static File getPidFile() {
        String pid = System.getProperty(DAEMON_PID_FILE);
        if (pid == null) {
            throw new IllegalArgumentException("No " + DAEMON_PID_FILE
                + " specified. Please invoke "
                + MonitorAgent.class.getName()
                + " with -D"
                + DAEMON_PID_FILE
                + "=<pid-file>");
        }
        return new File(pid);
    }

    private static void startAmqMonitor(final ApplicationContext context) throws Exception{
    	Thread t = new Thread(){    		
    		@Override
    		public void run() {
    			Monitor brokerMonitor = context.getBean(BrokerMonitor.class);
    			while(true){
    				logger.info("Testing connection to the ActiveMQ broker service.");
    				if(!brokerMonitor.testConnection()){
    					logger.error("ActiveMQ monitor cannot connect to the ActiveMQ broker service. Please check if the broker is running.");
    				}else{
    					brokerMonitor.start();
    					logger.info("Started all ActiveMQ monitor successfully.");
    					return;
    				}
    				try {
    					currentThread().sleep(10000);
    				} catch (InterruptedException e) {
    					logger.warn("Monitor Agent Interrupted. Giving up to start the ActiveMQ monitor.");
    					return;
    				}
    			}
    		}
    	};    	
    	t.start();
    }
    
    private static void startAggMonitor(final ApplicationContext context) throws Exception{
    	Thread t = new Thread(){    		
    		@Override
    		public void run() {
    			while(true){
    				com.nordija.statistic.monitoring.Monitor aggregatorMonitor = context.getBean(AggregatorMonitorImpl.class);
    				logger.info("Testing connection to the aggregator service.");		
    				if(!aggregatorMonitor.testConnection()){
    					logger.error("Aggregator monitor cannot connect to the aggregator service. Please check if the aggregator is running.");
    				}else{
        				try {
        					// Wait a bit before starting the monitor agent. The rational behind the delay is to give time to the Aggregator to 
        					// initialize it's jmx-agent and mbeans, in case the Aggregator has just been started. 
        					Thread.sleep(2000);
        				} catch (InterruptedException e) {
        					logger.warn("Monitor Agent Interrupted. Giving up to start the Aggregator monitor.");
        					return;
        				}					
    					aggregatorMonitor.start();
    					logger.info("Started Aggregator monitor successfully.");
    					return;
    				}
    				try {
    					Thread.sleep(10000);
    				} catch (InterruptedException e) {
    					logger.warn("Monitor Agent Interrupted. Giving up to start the Aggregator monitor.");
    					return;
    				}					
    			}
    		}
    	};
    	t.start();
    }
    
    private static void startStatsDataProcessor(final ApplicationContext context) throws Exception{    	
    	StreamingStatsDataProcessor statsDataProcessor = context.getBean(StreamingStatsDataProcessor.class);
    	try{
    		statsDataProcessor.start();
    	}catch(Exception ex){
    		logger.error("Failed to start Stats Data Processor service.", ex);
    	}
    	logger.info("Started Stats Data Processor service successfully.");
    }
    
    private static void startRestServices(final ApplicationContext context, boolean noDaemon) throws Exception{
    	MonitorRestService restService = context.getBean(MonitorRestService.class); 
    	try{
    		restService.start();
    		restService.setNoDaemon(noDaemon);
    	}catch(Exception e){
    		logger.error("Failed to start Rest Service. Exiting...", e.getCause());
    		throw e;
    	}
    	logger.info("Successfully started Rest Services.");    	
    }
    
    public static void startServices(final ApplicationContext context, boolean noDaemon, List<String> services) throws Exception{
    	for (String ser : services) {
			if(ser.equalsIgnoreCase(Service.AMQ_MONITOR.name())){
				startAmqMonitor(context);
			}else if(ser.equalsIgnoreCase(Service.AGG_MONITOR.name())){
				startAggMonitor(context);
			}else if(ser.equalsIgnoreCase(Service.STATS_DATA_PROCESSOR.name())){
				startStatsDataProcessor(context);
			}else if(ser.equalsIgnoreCase(Service.REST.name())){
				startRestServices(context, noDaemon);
			}
		}
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

            StreamingStatsDataProcessor statsDataProcessor = context.getBean(StreamingStatsDataProcessor.class);
            try{
            	logger.info("Shutting down Stats Data processor.");
            	if(statsDataProcessor.isRunning()){
            		statsDataProcessor.stop();
            	}
            }catch (Exception e) {
				logger.error("Failed to shutdown Stats Data processor gracefully", e);
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
            new ClassPathXmlApplicationContext(new String[] { 
            		"classpath*:META-INF/spring/applicationContext.xml",
            		"classpath*:META-INF/spring/applicationContext-batch.xml",
            		"classpath*:META-INF/spring/applicationContext-jmx.xml"});

        return context;
    }	
}
