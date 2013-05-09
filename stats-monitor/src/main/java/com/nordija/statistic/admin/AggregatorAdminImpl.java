package com.nordija.statistic.admin;

import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nordija.statistic.monitoring.aggregatorImpl.AggregatorMonitorImpl;

@Service("aggregatorAdmin")
public class AggregatorAdminImpl implements AggregatorAdmin {

	@Autowired AggregatorJmxConnector aggregatorJmxConnector;
	@Autowired AggregatorMonitorImpl aggregatorMonitor;
	
	@Override
	public void resetStatistics() throws Exception {
		aggregatorMonitor.resetCounters();
	}

	@Override
	public Map<String, Object> getAggregatorInfo() throws Exception {
		Map<String, Object> info = new HashMap<String, Object>();
		info.put("LastExchangeCompletedTimestamp", (Date)aggregatorJmxConnector.getMBeanAttribute("statisticCtx", "LastExchangeCompletedTimestamp"));
		info.put("ExchangesTotal", (Long)aggregatorJmxConnector.getMBeanAttribute("statisticCtx", "ExchangesTotal"));
		info.put("ExchangesCompleted", (Long)aggregatorJmxConnector.getMBeanAttribute("statisticCtx", "ExchangesCompleted"));
		info.put("Uptime", (String)aggregatorJmxConnector.getMBeanAttribute("statisticCtx", "Uptime"));
		info.put("version", "2.0");
		info.put("protocolVersion", "v2");
		info.put("homeDir", "/tmp/statistics-1.0.0-SNAPSHOT");
		info.put("aggregatorName", InetAddress.getLocalHost().getHostName());
		
		return info;
	}

	@Override
	public List<Map<String, Object>> getSettings() throws Exception{
		return (List<Map<String, Object>>)aggregatorJmxConnector.getMBeanAttribute("Settings.Manager", "Settings");
	}
}
