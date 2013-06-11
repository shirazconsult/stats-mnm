package com.nordija.statistic.mnm.stats.batch;

import java.util.Map;

public interface ScheduledJobLauncher {
	void launch();
	// The purpose of this method is just to expose job parameters via jmx
	Map<String, Object> getNextJobParams();
}
