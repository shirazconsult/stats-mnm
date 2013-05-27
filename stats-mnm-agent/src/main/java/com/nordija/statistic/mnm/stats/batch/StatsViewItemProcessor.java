package com.nordija.statistic.mnm.stats.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterRead;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.annotation.OnProcessError;
import org.springframework.batch.item.ItemProcessor;

import com.nordija.statistic.mnm.stats.StatsView;
import com.nordija.statistic.mnm.stats.StatsViewKey;

public class StatsViewItemProcessor implements ItemProcessor<StatsView, List<StatsView>> {
	private static Logger logger = LoggerFactory.getLogger(StatsViewItemProcessor.class);
	
	static ConcurrentMap<StatsViewKey, StatsView> cache;
	long from, to;

	@OnProcessError
    public void onProcessError(StatsView item, Exception e){
    	logger.warn("Failed to process stats-view record "+(item == null ? "null" : item.toString()));
    }
    
	@Override
	public List<StatsView> process(StatsView sv) throws Exception {
		StatsViewKey svk = new StatsViewKey(sv.getType(), sv.getName(), sv.getTitle());
		StatsView rec = cache.get(svk);
		if(rec == null){
			rec = new StatsView(svk.type, svk.name, svk.title);
			cache.put(svk, rec);
		}
		rec.accumulateDuration(sv.getDuraion());
		rec.accumulateViewers(sv.getViewers());
		rec.setFromTS(sv.getFromTS());
		rec.setToTS(sv.getToTS());

		if(sv.isCompleted()){
			logger.debug("Time to flush cache. cache size="+cache.size());
			return new ArrayList<StatsView>(cache.values());
		}
		return null;
	}
	 
	private StatsView lastRecord;
	@AfterRead
	public void afterRead(StatsView item){
		if(item == null){
			lastRecord.setCompleted(true);
		}else{
			lastRecord = item;
		}
	}
	
	@BeforeStep
	public void beforeStep(StepExecution stepExecution){
		JobParameters jobParams = stepExecution.getJobParameters();
		this.from = jobParams.getLong("from");
		this.to = jobParams.getLong("to");
	}

	@PostConstruct
	public void init(){
		cache = new ConcurrentHashMap<StatsViewKey, StatsView>();
	}
}
