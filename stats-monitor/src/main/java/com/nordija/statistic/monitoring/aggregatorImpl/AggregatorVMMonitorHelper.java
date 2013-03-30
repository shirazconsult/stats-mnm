package com.nordija.statistic.monitoring.aggregatorImpl;

import java.io.File;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;

import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nordija.statistic.monitoring.aggregator.AbstractAggregatorMonitorHelper;

@Deprecated
@Component("aggregatorVMMonitorHelper")
public class AggregatorVMMonitorHelper extends AbstractAggregatorMonitorHelper{
	private final static Logger logger = LoggerFactory.getLogger(AggregatorVMMonitorHelper.class);
			
	// The order in this array should not be changed, since both data collection and gnuplotters 
	// are based on that
	private final static String[] vmDataAttributes = {
		"HeapMemoryUsage", "NonHeapMemoryUsage", "ThreadCount", "SystemLoadAverage"
	};
			
	public String getData(@Header(Exchange.TIMER_PERIOD) long period, 
			@Header(Exchange.TIMER_COUNTER) long counter) throws Exception {
		MemoryMXBean memMXBean = (MemoryMXBean) getMXBeanProxyCache().get("Memory");
		ThreadMXBean threadingMXBean = (ThreadMXBean) getMXBeanProxyCache().get("Threading");
		OperatingSystemMXBean opSysMXBean = (OperatingSystemMXBean) getMXBeanProxyCache().get("OperatingSystem");
		
		long time = period*counter;
		StringBuilder sb = new StringBuilder().
				append(memMXBean.getHeapMemoryUsage().getUsed()).append(" ").
				append(memMXBean.getNonHeapMemoryUsage().getUsed()).append(" ").
				append(threadingMXBean.getThreadCount()).append(" ").
				append(opSysMXBean.getSystemLoadAverage()).append(" ").
				append(time);
		sb.append(System.getProperty("line.separator"));
		if(vmDataFileExist()){
			return sb.toString();
		}
		return getDataFileHeader().concat(sb.toString());
	}
			
	private String getDataFileHeader(){
		StringBuilder sb = new StringBuilder("# ");
		for(int i=0; i<vmDataAttributes.length; i++){			
			sb.append(i+1).append("-").append(vmDataAttributes[i]).append(" ");
		}
		sb.append(vmDataAttributes.length+1).append("-time");
		sb.append(System.getProperty("line.separator"));
		return sb.toString();
	}
		
	public boolean vmDataFileExist() {
		File f = new File(dataDir, getFilename());
		return f.exists();
	}

	public String getFilename(){
		return new StringBuilder(filePrefix).
				append("-VM-").
				append(DateTime.now().toString("yyyMMdd")).
				append(".data").toString();		
	}	
}
