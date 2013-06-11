package com.nordija.statistic.monitoring.admin;

import java.io.IOException;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

public interface AggregatorJmxConnector {
	MBeanServerConnection getJmxConnection() throws IOException;
	Map<String, ObjectName> getObjectNameCache() throws Exception;
	Map<String, Object> getMXBeanProxyCache() throws Exception;
	Object getMBeanAttribute(String objName, String attr) throws Exception;
	Object getAttribute(ObjectName objectName, String attr) throws Exception;
	Object invokeOperation(ObjectName objectName, String operation, Object[] params, String[] signature) throws Exception;
	
	void cleanup();
}
