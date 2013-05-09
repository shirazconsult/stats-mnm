package com.nordija.statistic.monitoring.aggregatorImpl;

import java.io.File;
import java.io.IOException;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nordija.statistic.admin.AggregatorJmxConnector;

@Component("aggregatorMonitorHelper")
public class AggregatorMonitorHelper {
	private final static Logger logger = LoggerFactory.getLogger(AggregatorMonitorHelper.class);
			
	@Autowired private AggregatorJmxConnector aggregatorJmxConnector;
	
	@Value("${aggregator.data.dir}")
	protected String dataDir;
	@Value("${aggregator.data.file.prefix}")
	protected String filePrefix;

	@Value("${aggregator.routes}")
	private String[] aggregatorRoutes;

	private static List<String> lastDataCache = Collections.synchronizedList(new ArrayList<String>());

	// The order in this array should not be changed, since both data collection and gnuplotters 
	// are based on that
	private final static String[] dynamicDataAttributes = {
		"ExchangesCompleted", "ExchangesFailed", "ExchangesTotal", "TotalProcessingTime",
		"LastProcessingTime", "MaxProcessingTime", "MinProcessingTime", "MeanProcessingTime", 
		"Load01", "Load05", "Load15", "VMHeapMemoryUsage", "VMNonHeapMemoryUsage", "VMThreadCount", "VMSystemLoadAverage"
//		"FirstExchangeCompletedTimestamp", "FirstExchangeFailureTimestamp", "LastExchangeCompletedTimestamp", "LastExchangeFailureTimestamp", 
	};
	private final static String[] staticDataAttributes = {
		"RouteId", "State", "Description"
	};
	
	public String getData(@Header(Exchange.TIMER_PERIOD) long period, 
			@Header(Exchange.TIMER_COUNTER) long counter, @Header("CamelLoopIndex") Integer idx) throws Exception {
		if(idx == null){
			return getContextData(period, counter);
		}
		long time = period*counter;
		long now = new Date().getTime();
		StringBuilder sb = new StringBuilder();
		for (String st : dynamicDataAttributes) {
			Object obj = aggregatorJmxConnector.getMBeanAttribute(aggregatorRoutes[idx], st);
			sb.append(obj).append(" ");
		}
		sb.append(time).append(" ").append(now);
		sb.append(System.getProperty("line.separator"));
		if(dataFileExist(aggregatorRoutes[idx])){
			return sb.toString();
		}
		return getDataFileHeader(aggregatorRoutes[idx]).concat(sb.toString());
	}

	private String getContextData(long period, long counter) throws Exception {
		long time = period*counter;
		long now = new Date().getTime();
		StringBuilder sb = new StringBuilder();
		for (String st : dynamicDataAttributes) {
			if(!st.startsWith("VM")){
				Object obj = aggregatorJmxConnector.getMBeanAttribute("statisticCtx", st);
				sb.append(obj).append(" ");
			}
		}
		// vm data
		MemoryMXBean memMXBean = (MemoryMXBean) aggregatorJmxConnector.getMXBeanProxyCache().get("Memory");
		ThreadMXBean threadingMXBean = (ThreadMXBean) aggregatorJmxConnector.getMXBeanProxyCache().get("Threading");
		OperatingSystemMXBean opSysMXBean = (OperatingSystemMXBean) aggregatorJmxConnector.getMXBeanProxyCache().get("OperatingSystem");

		sb.append(memMXBean.getHeapMemoryUsage().getUsed()).append(" ").
		append(memMXBean.getNonHeapMemoryUsage().getUsed()).append(" ").
		append(threadingMXBean.getThreadCount()).append(" ").
		append(opSysMXBean.getSystemLoadAverage()).append(" ").		
		append(time).append(" ").append(now);
		
		synchronized (lastDataCache) {
			lastDataCache.clear();
			String[] values = sb.toString().split(" ");
			lastDataCache.addAll(Arrays.asList(sb.toString().split(" ")));
		}
		
		sb.append(System.getProperty("line.separator"));
		if(dataFileExist("statisticCtx")){
			return sb.toString();
		}
		return getHeaderDescription().concat(sb.toString());
	}

	private String getDataFileHeader(String objName) throws Exception{
		// get routeId, state and description for this route
		StringBuilder sb = new StringBuilder("# ");
		ObjectName obj = aggregatorJmxConnector.getObjectNameCache().get(objName);
		for (String st : staticDataAttributes) {
			Object attr = aggregatorJmxConnector.getAttribute(obj, st);
			sb.append(attr).append("::");
		}
		sb.append(System.getProperty("line.separator")).
		append("# ###################################").
		append(System.getProperty("line.separator"));			


		return sb.append(getHeaderDescription()).toString();
	}
	
	private String getHeaderDescription(){
		// create header fields for dynamic attributes
		StringBuilder sb = new StringBuilder("# ");
		int idx=1;
		for (String col : getDataColumns()) {
			sb.append(idx++).append("-").append(col).append(" ");
		}
		sb.append(System.getProperty("line.separator"));
		return sb.toString();
	}
	
	public boolean dataFileExist(String objName) {
		File f = new File(dataDir, getFilenameForMBean(objName));
		return f.exists();
	}
	
	public String getFilename(@Header("CamelLoopIndex") Integer routeIdx){
		return (routeIdx == null ? getFilenameForMBean("statisticCtx") : getFilenameForMBean(aggregatorRoutes[routeIdx]));
	}

	Map<String, String> filenameCache = new HashMap<String, String>();
	public String getFilenameForMBean(String objName){
		if(!filenameCache.containsKey(objName)){
			StringBuilder filename = new StringBuilder(filePrefix).
				append("-").
				append(objName).
				append("-").
				append(DateTime.now().toString("yyyMMdd")).
				append(".data");
			filenameCache.put(objName, filename.toString());
		}
		return filenameCache.get(objName);
	}

	public void resetCounters() throws Exception{
		for (String routeId : aggregatorRoutes) {
			ObjectName objName = aggregatorJmxConnector.getObjectNameCache().get(routeId);
			aggregatorJmxConnector.invokeOperation(objName, "reset", null, null);
		}
	}	
	
	public List<String> getLastDataRow(){
		List<String> result = new ArrayList<String>();
		synchronized (lastDataCache) {
			result.addAll(lastDataCache);
		}
		return result;
	}	
	
	public List<String> getDataColumns(){
		String[] dc = Arrays.copyOf(dynamicDataAttributes, dynamicDataAttributes.length+2);
		dc[dc.length-2] = "time";
		dc[dc.length-1] = "utime";
		return Arrays.asList(dc);
	}	
	
	public List<List<String>> getDataRows(long from, long to) throws IOException{
		return getDataRows(new File(dataDir, getFilenameForMBean("statisticCtx")), from, to);
	}

	private List<List<String>> getDataRows(File file, long from, long to) throws IOException{
		List<List<String>> result = new ArrayList<List<String>>();
		if((! file.exists()) || (! file.canRead())){
			return result;
		}
		LineIterator li = FileUtils.lineIterator(file);
		String next = null;
		while(li.hasNext()){
			next = li.nextLine();
			if(next.startsWith("#")){
				continue;
			}
			String[] split = next.split(" ");
			long time = Long.parseLong(split[split.length-1]);
			if(time >= from){
				if(time > to){
					return result;
				}
				result.add(Arrays.asList(next.split(" ")));
			}
		}

		return result;
	}
	
}
