package com.nordija.statistic.monitoring;

import java.util.List;

import org.springframework.context.Lifecycle;

public interface Monitor extends Lifecycle {
	public boolean testConnection();
	public void resetCounters() throws Exception;
	public List<String> getDataColumns();
	public List<String> getLastDataRow();
	public List<List<String>> getDataRows(long from, long to);	
}
