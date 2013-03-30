package com.nordija.statistic.monitoring.aggregatorImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServerConnection;

import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nordija.statistic.monitoring.aggregator.AggregatorMonitor;

@Component("aggregatorMonitor")
public class AggregatorMonitorImpl extends RouteBuilder implements AggregatorMonitor{
	private final static Logger logger = LoggerFactory.getLogger(AggregatorMonitorImpl.class);
	
	@Value("${aggregator.data.dir}")
	private String dataDir;
	@Value("${aggregator.data.collection.interval}")
	private long interval;
	@Value("${aggregator.data.collection.count}")
	private long repeatCount;
	@Value("${aggregator.routes}")
	private String[] aggregatorRoutes;

	private AtomicBoolean isRunning = new AtomicBoolean(false);
	@Autowired
	private AggregatorMonitorHelper aggregatorMonitorHelper;

	@Override
	public void configure() throws Exception {
		from("timer:aggregaotrMonitorTimer?fixedRate=true&period=" + interval + "&repeatCount=" + repeatCount).routeId("aggregator.routes.monitor").noAutoStartup()
		.loop(aggregatorRoutes.length).copy()
		.to("bean:aggregatorMonitorHelper?method=getData")
		.setHeader("dataFilename", method(AggregatorMonitorHelper.class, "getFilename"))
		.to("file:"+dataDir+"?fileName=${in.header.dataFilename}&fileExist=Append");

		from("timer:aggregaotrContextMonitorTimer?delay=1000&fixedRate=true&period=" + interval + "&repeatCount=" + repeatCount).routeId("aggregator.context.monitor").noAutoStartup()
		.to("bean:aggregatorMonitorHelper?method=getData")
		.setHeader("dataFilename", method(AggregatorMonitorHelper.class, "getFilename"))
		.to("file:"+dataDir+"?fileName=${in.header.dataFilename}&fileExist=Append");

//		from("timer:vmMonitorTimer?fixedRate=true&period=" + interval + "&repeatCount=" + repeatCount).routeId("aggregator.vm.monitor").noAutoStartup()
//		.to("bean:aggregatorVMMonitorHelper?method=getData")
//		.setHeader("dataFilename", method(AggregatorVMMonitorHelper.class, "getFilename"))
//		.to("file:"+dataDir+"?fileName=${in.header.dataFilename}&fileExist=Append");
	}
	
	@Override
	public void start() {
		try {
			getContext().start();
//			getContext().startRoute("aggregator.routes.monitor");
//			getContext().startRoute("aggregator.vm.monitor");
			getContext().startRoute("aggregator.context.monitor");
			isRunning.set(true);
		} catch (Exception e) {
			logger.error("Failed to start. ", e);
		}
	}

	@Override
	public void stop() {
		try {
//			getContext().stopRoute("aggregator.routes.monitor");
//			getContext().stopRoute("aggregator.vm.monitor");
			getContext().stopRoute("aggregator.context.monitor");
			aggregatorMonitorHelper.cleanup();
			isRunning.set(false);
		} catch (Exception e) {
			logger.error("Failed to stop. ", e);
		}
	}

	@Override
	public boolean isRunning() {
		return isRunning.get();
	}

	@Override
	public boolean testConnection() {
		MBeanServerConnection jmxConnection = null;
		try {
			jmxConnection = aggregatorMonitorHelper.getJmxConnection();
		} catch (IOException e) {
			logger.error("Failed to connect to the aggregator's jmx agent.", e);
			return false;
		}
		return jmxConnection != null;
	}

	@Override
	public void resetCounters() throws Exception {
		aggregatorMonitorHelper.resetCounters();
	}

	@Override
	public boolean isActive() {
		try {
			Integer inflightExchanges = (Integer)aggregatorMonitorHelper.getMBeanAttribute("aggregator", "InflightExchanges");
			if(inflightExchanges != 0){
				logger.info("Number of inflight exchagnes is {}.", inflightExchanges);
				return true;
			}
			inflightExchanges = (Integer)aggregatorMonitorHelper.getMBeanAttribute("db.persister", "InflightExchanges");
			if(inflightExchanges != 0){
				logger.info("Number of inflight exchagnes is {}.", inflightExchanges);
				return true;
			}
			Date lastCompleted = (Date)aggregatorMonitorHelper.getMBeanAttribute("aggregator", "LastExchangeCompletedTimestamp");
			if(lastCompleted != null && lastCompleted.getTime() <= System.currentTimeMillis()-60000){
				logger.info("Last completed exchange was at {}.", lastCompleted);
				return true;
			}
			lastCompleted = (Date)aggregatorMonitorHelper.getMBeanAttribute("db.persister", "LastExchangeCompletedTimestamp");
			if(lastCompleted != null && lastCompleted.getTime() <= System.currentTimeMillis()-60000){
				logger.info("Last completed exchange was at {}.", lastCompleted);
				return true;
			}
		} catch (Exception e) {
			logger.error("Unable to access the aggregator's JMX agent.", e);
			return true;
		}
		return false;
	}
	
	@Override
	public List<String> getLastDataRow() {
		ArrayList<String> result = new ArrayList<String>();
		result.addAll(aggregatorMonitorHelper.getLastDataRow());
//		result.addAll(aggregatorVMMonitorHelper.getLastDataRow());
		return result;
	}

	@Override
	public List<List<String>> getDataRows(long from, long to) {
		try {
			// for now we return only rows for the aggregator and not for the vm.
			return aggregatorMonitorHelper.getDataRows(from, to);
		} catch (IOException e) {
			logger.error("Could not read data.", e);
		}
		return null;
	}

	@Override
	public List<String> getDataColumns() {
		return aggregatorMonitorHelper.getDataColumns();
	}
	
}
