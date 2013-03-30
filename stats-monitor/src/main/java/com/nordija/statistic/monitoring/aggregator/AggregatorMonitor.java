package com.nordija.statistic.monitoring.aggregator;

import com.nordija.statistic.monitoring.Monitor;

public interface AggregatorMonitor extends Monitor{
	/**
	 * Retruns true if the aggregator has been processing one or more exchanges for the last 10 seconds.
	 * 
	 * @return
	 */
	boolean isActive();
}
