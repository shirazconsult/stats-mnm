package com.nordija.statistic.admin;

import java.io.IOException;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("aggregatorJmxConnector")
public class AggregatorJmxConnectorImpl implements AggregatorJmxConnector {
	private final static Logger logger = LoggerFactory.getLogger(AggregatorJmxConnectorImpl.class);
	
	@Value("${aggregator.jmx.url}")
	protected String aggregatorJmxUrl;
	
	private static Map<String, ObjectName> objectNameCache = null;
	private static Map<String, Object> mxBeanProxyCache = null;

//	private MBeanServerConnection jmxConnection = null;
	private JMXConnector jmxConnector = null;
			
	@Override
	public MBeanServerConnection getJmxConnection() throws IOException{
		if(jmxConnector == null){
			JMXServiceURL url = new JMXServiceURL(aggregatorJmxUrl);
			jmxConnector = JMXConnectorFactory.connect(url);
		}
		return jmxConnector.getMBeanServerConnection();
	}
	
	@Override
	public Map<String, ObjectName> getObjectNameCache() throws Exception{
		if(objectNameCache == null){
			objectNameCache = new ConcurrentHashMap<String, ObjectName>();
			
			// cache relevant mbean names under statistics context 
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
			
			// cache all mbean name under statistics.aggregator context
			objName = new ObjectName("statistics.aggregator:*");
			objectNameList = new LinkedList<ObjectName>(getJmxConnection().queryNames(objName, null));			
			for (ObjectName obj : objectNameList) {
				String key = obj.getKeyProperty("name").replace("\"", "");
				objectNameCache.put(key, obj);
				logger.info("Caching JMX object name {}.", key);
			}			
		}
		return objectNameCache;
	}
	
	@Override
	public Map<String, Object> getMXBeanProxyCache() throws Exception{
		if(mxBeanProxyCache == null){
			mxBeanProxyCache = new ConcurrentHashMap<String, Object>();
			
			ObjectName memObjName = getJmxConnection().queryNames(new ObjectName("java.lang:type=Memory"), null).iterator().next();
			mxBeanProxyCache.put("Memory", JMX.newMXBeanProxy(getJmxConnection(), memObjName, MemoryMXBean.class));
			
			ObjectName threadingObjName = getJmxConnection().queryNames(new ObjectName("java.lang:type=Threading"), null).iterator().next();
			mxBeanProxyCache.put("Threading", JMX.newMXBeanProxy(getJmxConnection(), threadingObjName, ThreadMXBean.class));
			
			ObjectName opSysObjName = getJmxConnection().queryNames(new ObjectName("java.lang:type=OperatingSystem"), null).iterator().next();
			mxBeanProxyCache.put("OperatingSystem", JMX.newMXBeanProxy(getJmxConnection(), opSysObjName, OperatingSystemMXBean.class));			
		}
		return mxBeanProxyCache;
	}
		
	@Override
	public Object getMBeanAttribute(String objName, String attr) throws Exception{
		return getJmxConnection().getAttribute(getObjectNameCache().get(objName), attr);
	}

	
	@Override
	public Object getAttribute(ObjectName objectName, String attr) throws Exception {
		return getJmxConnection().getAttribute(objectName, attr);
	}

	@Override
	public Object invokeOperation(ObjectName objectName, String operation,
			Object[] params, String[] signature) throws Exception {
		return getJmxConnection().invoke(objectName, "reset", params, signature);
	}

	public void cleanup(){
		try {
			jmxConnector.close();
		} catch (IOException e) {
			logger.warn("Failed to close the jmx connector.", e);
		}
	}		
}
