package com.nordija.statistic.mnm.stats.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.joda.time.DateTimeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterRead;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.annotation.OnProcessError;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.nordija.statistic.mnm.stats.StatsView;
import com.nordija.statistic.mnm.stats.StatsViewKey;

@Component("statsViewProcessor")
public class StatsViewItemProcessor implements ItemProcessor<StatsView, List<StatsView>> {
	private static Logger logger = LoggerFactory.getLogger(StatsViewItemProcessor.class);
	
	private long statsViewDurationLimit = DateTimeConstants.MILLIS_PER_HOUR;
	static ConcurrentMap<StatsViewKey, StatsView> cache;
	long from, to;

	@OnProcessError
    public void onProcessError(StatsView item, Exception e){
    	logger.error("Failed to process stats-view record {}.", (item == null ? "null" : item.toString()));
    }
    
	@Override
	public List<StatsView> process(StatsView sv) throws Exception {
		StatsViewKey svk = new StatsViewKey(sv.getType(), sv.getName(), sv.getTitle());
		StatsView rec = cache.get(svk);
		// completedRec will evt. point to a record in the cache with the same key, which might already be
		// completed (has elapsed the accumulationUnit). In that case it has to be sent to the item-writer.
		StatsView completedRec = null;
		if(isAlreadyCompleted(rec, sv)){
			completedRec = cache.remove(svk);
		}
		if(rec == null || completedRec != null){
			rec = new StatsView(svk.type, svk.name, svk.title);
			rec.setFromTS(sv.getFromTS());
			cache.put(svk, rec);
		}
		rec.accumulateDuration(sv.getDuraion());
		rec.accumulateViewers(sv.getViewers());
		rec.setToTS(sv.getToTS());

		if(sv.isCompleted()){
			logger.debug("Time to flush cache. cache size={}.", cache.size());
			return addRecordToList(new ArrayList<StatsView>(cache.values()), completedRec);
		}
		if(rec.getToTS() - rec.getFromTS() >= statsViewDurationLimit){
			return addRecordToList(getRecordAsList(cache.remove(svk)), completedRec);
		}
		return completedRec == null ? null : getRecordAsList(completedRec);
	}
	
	private boolean isAlreadyCompleted(StatsView cacheRec, StatsView newRec){
		return cacheRec != null && 
				newRec.getToTS() - cacheRec.getFromTS() >= statsViewDurationLimit &&
				newRec.getToTS() - cacheRec.getToTS() > 5*DateTimeConstants.MILLIS_PER_MINUTE;
	}
	
	private StatsView lastRecord;
	@AfterRead
	public void afterRead(StatsView item){
		if(item == null && lastRecord != null){
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

	public void setAccumulationUnit(TimeUnit accumulationUnit) {
		switch(accumulationUnit){
		case HOURS:
			statsViewDurationLimit = DateTimeConstants.MILLIS_PER_DAY;
		case DAYS:
			statsViewDurationLimit = DateTimeConstants.MILLIS_PER_HOUR;
			break;
		default:
			throw new IllegalArgumentException("Invalid accumulationUnit. Valid values are " + 
					TimeUnit.DAYS.name() + " and " + TimeUnit.DAYS.name());			
		}
	}
	
	public void setAccumulationUnit(String accumulationUnit) {
		setAccumulationUnit(TimeUnit.valueOf(accumulationUnit));
	}
	
	private List<StatsView> getRecordAsList(StatsView rec){
		return addRecordToList(null, rec);
	}

	private List<StatsView> addRecordToList(List<StatsView> list, StatsView rec){
		if(list == null){
			list = new ArrayList<StatsView>();
		}
		if(rec != null){
			list.add(rec);
		}
		return list;
	}

}
