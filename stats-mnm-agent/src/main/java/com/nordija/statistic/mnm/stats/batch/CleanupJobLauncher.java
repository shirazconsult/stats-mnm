package com.nordija.statistic.mnm.stats.batch;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;

public class CleanupJobLauncher implements ScheduledJobLauncher {
	final static private Logger logger = LoggerFactory.getLogger(CleanupJobLauncher.class);
	
	private Job job;
	
	@Autowired private JobLauncher jobLauncher;
	@Autowired private JobExplorer jobExplorer;

	private long daysToLive;
	private String table;

	@Override
	public void launch() {
		try{
			launch(getNextJobParameters());
		} catch (Exception e) {
			logger.error("Failed to execute {}. Reason: {}", job.getName(), e.getMessage());			
		}
	}

	@Override
	public void launch(String dateStr) {
		DateTime dt = DateTime.parse(dateStr);

		JobParameters jobParameters = new JobParametersBuilder().
			addDate(CleanupTask.DELETE_BEFORE_DATE_PARAM, dt.toDate()).
			addString(CleanupTask.TABLE_PARAM, table).
			toJobParameters();
		try{
			launch(new JobParametersBuilder(jobParameters).addLong("run.id", RandomUtils.nextLong()).toJobParameters());
		}catch(JobInstanceAlreadyCompleteException jiac){
			logger.warn("Non-unique 'run.id' parameter ({}). Retrying with new 'run.id'");
			try {
				launch(new JobParametersBuilder(jobParameters).addLong("run.id", RandomUtils.nextLong()).toJobParameters());
			} catch (Exception e) {
				logger.error("Could not launch the batch job,", e);
			}
		} catch (Exception e) {
			logger.error("Failed to execute {}. Reason: {}", job.getName(), e.getMessage());			
		}
	}

	private void launch(JobParameters jobParams) throws Exception{
			jobLauncher.run(job, jobParams);
	}
	
	private JobParameters getNextJobParameters(){
		DateTime deleteBeforeDate = DateTime.now().minusDays((int)daysToLive);
    	return new JobParametersBuilder().
    			addDate(CleanupTask.DELETE_BEFORE_DATE_PARAM, deleteBeforeDate.toDate()).
    			addString(CleanupTask.TABLE_PARAM, table).
    			toJobParameters();
	}
	
	private final static DateTimeFormatter dtf = ISODateTimeFormat.date();
	@Override
	public Map<String, Object> getNextJobParams(){
		DateTime deleteBeforeDate = DateTime.now().minusDays((int)daysToLive);
		Map<String, Object> res = new HashMap<String, Object>();
		res.put(CleanupTask.DELETE_BEFORE_DATE_PARAM, dtf.print(deleteBeforeDate));
		res.put(CleanupTask.TABLE_PARAM, table);
		
		return res;
	}

	@PostConstruct
	public void init(){
		if(daysToLive == 0){
			throw new IllegalStateException("The property "+daysToLive+" is not set.");
		}
		if(StringUtils.isEmpty(table)){
			throw new IllegalStateException("The property "+table+" is not set.");
		}
	}

	public void setDaysToLive(long daysToLive) {
		this.daysToLive = daysToLive;
	}

	public void setTable(String table) {
		this.table = table;
	}

	public void setJob(Job job) {
		this.job = job;
	}
}
