package com.nordija.statistic.admin;

import java.util.List;
import java.util.Map;

public interface AggregatorAdmin {
	public void resetStatistics() throws Exception;
	public Map<String, Object> getAggregatorInfo() throws Exception;
	List<Map<String, Object>> getSettings() throws Exception;

}
