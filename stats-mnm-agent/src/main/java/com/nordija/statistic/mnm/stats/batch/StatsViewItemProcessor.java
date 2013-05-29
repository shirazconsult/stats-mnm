package com.nordija.statistic.mnm.stats.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.joda.time.DateTime;
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
	
	private String name = "statsViewItemProcessor";
	
	private TimeUnit accumulationUnit = TimeUnit.HOURS;
	private ConcurrentMap<StatsViewKey, StatsView> cache;
	long from, to;

	@OnProcessError
    public void onProcessError(StatsView item, Exception e){
    	logger.error("{} : Failed to process stats-view record {}.", name, (item == null ? "null" : item.toString()));
    }
    
	@Override
	public List<StatsView> process(StatsView sv) throws Exception {
		StatsViewKey svk = new StatsViewKey(sv.getType(), sv.getName(), sv.getTitle());
		StatsView rec = cache.get(svk);
		// completedRec will evt. point to a record in the cache with the same key, which might already be
		// completed (has elapsed the accumulationUnit). In that case it has to be sent to the item-writer.
		StatsView completedCacheRec = null;
		if(isCacheRecCompleted(rec, sv)){
			completedCacheRec = cache.remove(svk);
		}
		if(rec == null || completedCacheRec != null){
			rec = new StatsView(svk.type, svk.name, svk.title);
			rec.setFromTS(sv.getFromTS());
			cache.put(svk, rec);
		}
		rec.accumulateDuration(sv.getDuraion());
		rec.accumulateViewers(sv.getViewers());
		rec.setToTS(sv.getToTS());

		if(sv.isCompleted()){  // no more records in the chunk
			logger.debug("{} : Time to flush cache. cache size={}.", name, cache.size());
			return addRecordToList(new ArrayList<StatsView>(cache.values()), completedCacheRec);
		}
		if(isCurrentRecCompleted(rec)){
			return addRecordToList(getRecordAsList(cache.remove(svk)), completedCacheRec);
		}
		return completedCacheRec == null ? null : getRecordAsList(completedCacheRec);
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
		case DAYS:
			this.accumulationUnit = accumulationUnit;
			break;
		default:
			throw new IllegalArgumentException("Invalid accumulationUnit. Valid values are " + 
					TimeUnit.DAYS.name() + " and " + TimeUnit.DAYS.name());			
		}
	}

	private boolean isCurrentRecCompleted(StatsView rec){
		if(rec != null){
			DateTime recFromTime = new DateTime(rec.getFromTS());
			DateTime recToTime = new DateTime(rec.getToTS());
			int recFromDay = recFromTime.dayOfMonth().get();
			int recToDay = recToTime.dayOfMonth().get();
			switch(accumulationUnit){
			case HOURS:
				int recFromHour = recFromTime.hourOfDay().get();
				int recToHour = recToTime.hourOfDay().get();
				return recToHour > recFromHour || recToDay > recFromDay;
			case DAYS:
				int recFromMonth = recFromTime.monthOfYear().get();
				int recToMonth = recToTime.monthOfYear().get();
				return recToDay > recFromDay || recToMonth > recFromMonth;
			default:
				return false;
			}
		}
		return false;		
	}
	
	private boolean isCacheRecCompleted(StatsView cacheRec, StatsView newRec){
		if(cacheRec != null){
			DateTime newRecFromTime = new DateTime(newRec.getFromTS());
			DateTime cacheRecFromTime = new DateTime(cacheRec.getFromTS());
			int newRecFromDay = newRecFromTime.dayOfMonth().get();
			int cacheRecFromDay = cacheRecFromTime.dayOfMonth().get();
			switch(accumulationUnit){
			case HOURS:
				int newRecFromHour = newRecFromTime.hourOfDay().get();
				int cacheRecFromHour = cacheRecFromTime.hourOfDay().get();
				return newRecFromHour > cacheRecFromHour || newRecFromDay > cacheRecFromDay;
			case DAYS:
				int newRecFromMonth = newRecFromTime.monthOfYear().get();
				int cacheRecFromMonth = cacheRecFromTime.monthOfYear().get();
				return newRecFromDay > cacheRecFromDay || newRecFromMonth > cacheRecFromMonth;
			default:
				return false;
			}
		}
		return false;
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

	public void setName(String name) {
		this.name = name;
	}

}
