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

public class ScheduledJobLauncher {
	final static private Logger logger = LoggerFactory.getLogger(ScheduledJobLauncher.class);
	
	@Autowired private Job hourlyJob;
	@Autowired private JobLauncher jobLauncher;
	@Autowired private JobExplorer jobExplorer;
	
	public void launch(){
		JobParameters jobParams = getNextJobParameters();
		long from = jobParams.getLong("from");
		long to = jobParams.getLong("to");
		
		logger.info("Launching {} with parameters: {}", hourlyJob.getName(), jobParams.toString());
		if(to - from < DateTimeConstants.MILLIS_PER_HOUR){
			logger.info("Skipping launch of {}, since at least one hour must be elapsed since the last launched time: {} - {}", 
					new Object[]{hourlyJob.getName(), new DateTime(from), new DateTime(to)});
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
	    	return new JobParametersBuilder().
	    			addLong("from", GregorianChronology.getInstance().getDateTimeMillis(1970, 1, 1, 1, 0, 0, 0)).
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
    	return new JobParametersBuilder().
    			addLong("from", lastJobParams.getLong("to")).
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
