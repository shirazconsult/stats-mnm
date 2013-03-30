package com.nordija.statistic.monitoring.aggregator;

import java.io.IOException;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public abstract class AbstractAggregatorMonitorHelper {
	private final static Logger logger = LoggerFactory.getLogger(AbstractAggregatorMonitorHelper.class);
	
	@Value("${aggregator.data.dir}")
	protected String dataDir;
	@Value("${aggregator.data.file.prefix}")
	protected String filePrefix;
	@Value("${aggregator.jmx.url}")
	protected String aggregatorJmxUrl;
	
	protected Map<String, ObjectName> objectNameCache = null;
	protected Map<String, Object> mxBeanProxyCache = null;

	private MBeanServerConnection jmxConnection = null;
	private JMXConnector jmxConnector = null;
			
	public MBeanServerConnection getJmxConnection() throws IOException{
		if(jmxConnection == null){
			JMXServiceURL url = new JMXServiceURL(aggregatorJmxUrl);
			jmxConnector = JMXConnectorFactory.connect(url);
			jmxConnection = jmxConnector.getMBeanServerConnection();
		}
		return jmxConnection;
	}
	
	protected Map<String, ObjectName> getObjectNameCache() throws Exception{
		if(objectNameCache == null){
			objectNameCache = new HashMap<String, ObjectName>();
			
			ObjectName objName = new ObjectName("statistics:context=*statistic*,type=routes,*");
			List<ObjectName> objectNameList = new LinkedList<ObjectName>(getJmxConnection().queryNames(objName, null));			
			for (ObjectName obj : objectNameList) {
				String key = obj.getKeyProperty("name").replace("\"", "");
				objectNameCache.put(key, obj);
				logger.info("Caching JMX object name {}.", key);
			}

			ObjectName ctxObjName = new ObjectName("statistics:context=*statistic*,type=context,name=\"statisticCtx\"");
			objectNameCache.put("statisticCtx", getJmxConnection().queryNames(ctxObjName, null).iterator().next());			
			logger.info("Caching JMX object name {}.", ctxObjName);
		}
		return objectNameCache;
	}
	
	protected Map<String, Object> getMXBeanProxyCache() throws Exception{
		if(mxBeanProxyCache == null){
			mxBeanProxyCache = new HashMap<String, Object>();
			
			ObjectName memObjName = getJmxConnection().queryNames(new ObjectName("java.lang:type=Memory"), null).iterator().next();
			mxBeanProxyCache.put("Memory", JMX.newMXBeanProxy(getJmxConnection(), memObjName, MemoryMXBean.class));
			
			ObjectName threadingObjName = getJmxConnection().queryNames(new ObjectName("java.lang:type=Threading"), null).iterator().next();
			mxBeanProxyCache.put("Threading", JMX.newMXBeanProxy(getJmxConnection(), threadingObjName, ThreadMXBean.class));
			
			ObjectName opSysObjName = getJmxConnection().queryNames(new ObjectName("java.lang:type=OperatingSystem"), null).iterator().next();
			mxBeanProxyCache.put("OperatingSystem", JMX.newMXBeanProxy(getJmxConnection(), opSysObjName, OperatingSystemMXBean.class));			
		}
		return mxBeanProxyCache;
	}
		
	public Object getMBeanAttribute(String objName, String attr) throws Exception{
		return getJmxConnection().getAttribute(getObjectNameCache().get(objName), attr);
	}

	public void cleanup(){
		try {
			jmxConnector.close();
		} catch (IOException e) {
			logger.warn("Failed to close the jmx connector.", e);
		}
	}		
}
