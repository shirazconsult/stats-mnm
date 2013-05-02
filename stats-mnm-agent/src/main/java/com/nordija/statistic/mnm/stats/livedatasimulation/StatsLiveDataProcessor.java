package com.nordija.statistic.mnm.stats.livedatasimulation;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

public class StatsLiveDataProcessor implements ItemProcessor<List<Object>, List<Object>> {
	private static Logger logger = LoggerFactory.getLogger(StatsLiveDataProcessor.class);
	
	private static long count;
	
	private long delayMillis = 200;
	private long numOfRecordsBeforDealy = 1000;
	private long timeOffsetFromNow = -1000;
	
	@Override
	public List<Object> process(List<Object> item) throws Exception {
		count++;
		item.set(13, System.currentTimeMillis()+(timeOffsetFromNow));
		if(count % numOfRecordsBeforDealy == 0){
			logger.info("Migrated total of {} live statistics events.", count);
			Thread.sleep(delayMillis);
		}
		return item;
	}

	public void setDelayMillis(long delayMillis) {
		this.delayMillis = delayMillis;
	}

	public void setNumOfRecordsBeforDealy(long numOfRecordsBeforDealy) {
		this.numOfRecordsBeforDealy = numOfRecordsBeforDealy;
	}

	public void setTimeOffsetFromNow(long timeOffsetFromNow) {
		this.timeOffsetFromNow = timeOffsetFromNow;
	}
	
}
