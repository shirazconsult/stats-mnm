package com.nordija.statistic.mnm.stats.batch;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.chrono.GregorianChronology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("scheduledJobLauncher")
public class ScheduledJobLauncher {
	final static private Logger logger = LoggerFactory.getLogger(ScheduledJobLauncher.class);
	
	@Autowired private Job hourlyJob;
	@Autowired private JobLauncher jobLauncher;
	@Autowired private JobExplorer jobExplorer;
	
	@Scheduled(fixedRate=DateTimeConstants.MILLIS_PER_HOUR)
	public void launch(){
		JobParameters jobParams = getNextJobParameters();
		logger.info("Launching {} with parameters: {}", hourlyJob.getName(), jobParams.toString());
		if(jobParams.getLong("to") - jobParams.getLong("from") < DateTimeConstants.MILLIS_PER_HOUR){
			logger.info("Skipping launch of {}, since at least one hour must be elapsed since last time the job was launched. from: {}, to: {}", 
					new Object[]{hourlyJob.getName(), jobParams.getLong("from"), jobParams.getLong("to")});
			return;
		}
    	try {
			jobLauncher.run(hourlyJob, jobParams);
		} catch (Exception e) {
			logger.error("Could not launch the batch job,", e);
		}
	}
	
	private JobParameters getNextJobParameters(){
		List<JobInstance> jobInstances = jobExplorer.getJobInstances(hourlyJob.getName(), 0, 1);
		
		if(CollectionUtils.isEmpty(jobInstances)){
	    	long epoch = GregorianChronology.getInstance().getDateTimeMillis(
	    			1970, 
	    			1, 
	    			1, 
	    			1, 0, 0, 0);
	    	return new JobParametersBuilder().
	    			addLong("from", epoch).
	    			addLong("to", getNextToInMillis()).
	    			addString("name", hourlyJob.getName()).
	    			toJobParameters();
		}
		
		JobInstance lastJobInst = jobInstances.get(0);
		List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(lastJobInst);
		JobExecution lastJobEx = jobExecutions.get(jobExecutions.size()-1);
		
		if(lastJobEx.getStatus() == BatchStatus.FAILED){
			return lastJobInst.getJobParameters();
		}
		
		JobParameters lastJobParams = lastJobInst.getJobParameters();
		long from = lastJobParams.getLong("to");
    	return new JobParametersBuilder().
    			addLong("from", from).
    			addLong("to", getNextToInMillis()).
    			addString("name", hourlyJob.getName()).
    			toJobParameters();		
	}
	
	private static long getNextToInMillis(){
		DateTime now = DateTime.now();
		return GregorianChronology.getInstance().getDateTimeMillis(
    			now.year().get(), 
    			now.monthOfYear().get(), 
    			now.dayOfMonth().get(), 
    			now.hourOfDay().get(), 
    			0, 0, 0);
	}
}
