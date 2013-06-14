package com.nordija.statistic.mnm.stats.batch;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.CollectionUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.chrono.GregorianChronology;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
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

public class ScheduledJobLauncherImpl implements ScheduledJobLauncher {
	final static private Logger logger = LoggerFactory.getLogger(ScheduledJobLauncherImpl.class);
	
	private Job job;
	private TimeUnit scheduleTimeUnit = TimeUnit.HOURS;
	private long timespanBetweenLaunches = DateTimeConstants.MILLIS_PER_HOUR;
	
	@Autowired private JobLauncher jobLauncher;
	@Autowired private JobExplorer jobExplorer;
	
	@Override
	public void launch(){
		launch(getNextJobParameters());
	}

	@Override
	public void launch(String fromDate){
		DateTime dt = DateTime.parse(fromDate);
		long from = dt.getMillis();

		launch(new JobParametersBuilder().
				addLong("from", from).
				addLong("to", getNextToInMillis()).
				addString("name", job.getName()).
				toJobParameters());		
	}

	private void launch(JobParameters jobParams){
		logger.info("Launching {} with parameters: {}", job.getName(), jobParams.toString());

		long from = jobParams.getLong("from");
		long to = jobParams.getLong("to");
		if(to - from < timespanBetweenLaunches){
			logger.info("Skipping launch of {}, since at least one hour must be elapsed since the last launched time: {} - {}", 
					new Object[]{job.getName(), new DateTime(from), new DateTime(to)});
			return;
		}
    	try {
			jobLauncher.run(job, jobParams);
		} catch (Exception e) {
			logger.error("Could not launch the batch job,", e);
		}		
	}
	
	private JobParameters getNextJobParameters(){
		List<JobInstance> jobInstances = jobExplorer.getJobInstances(job.getName(), 0, 1);
		
		if(CollectionUtils.isEmpty(jobInstances)){
	    	return new JobParametersBuilder().
	    			addLong("from", GregorianChronology.getInstance().getDateTimeMillis(1970, 1, 1, 1, 0, 0, 0)).
	    			addLong("to", getNextToInMillis()).
	    			addString("name", job.getName()).
	    			toJobParameters();
		}
		
		JobInstance lastJobInst = jobInstances.get(0);
		List<JobExecution> jobExecutions = jobExplorer.getJobExecutions(lastJobInst);
		JobExecution lastJobEx = jobExecutions.get(jobExecutions.size()-1);
		
		// this would effectively restart the failed job
		if(lastJobEx.getStatus() == BatchStatus.FAILED){
			return lastJobInst.getJobParameters();
		}
		
		JobParameters lastJobParams = lastJobInst.getJobParameters();
    	return new JobParametersBuilder().
    			addLong("from", lastJobParams.getLong("to")).
    			addLong("to", getNextToInMillis()).
    			addString("name", job.getName()).
    			toJobParameters();		
	}
	
	// The purpose of this method is just to expose job parameters via jmx
	private final static DateTimeFormatter dtf = ISODateTimeFormat.dateHourMinuteSecond();
	@Override
	public Map<String, Object> getNextJobParams(){
		JobParameters params = getNextJobParameters();
		Map<String, Object> res = new HashMap<String, Object>();
		res.put("from", dtf.print(params.getLong("from")));
		res.put("to", dtf.print(params.getLong("to")));
		res.put("name", params.getString("name"));
		
		return res;
	}
	
	private long getNextToInMillis(){
		DateTime now = DateTime.now();
		switch(scheduleTimeUnit){
		case HOURS:
			return GregorianChronology.getInstance().getDateTimeMillis(
					now.year().get(), 
					now.monthOfYear().get(), 
					now.dayOfMonth().get(), 
					now.hourOfDay().get(), 
					0, 0, 0);
		case DAYS:
			return GregorianChronology.getInstance().getDateTimeMillis(
					now.year().get(), 
					now.monthOfYear().get(), 
					now.dayOfMonth().get(), 
					0, 0, 0, 0);	
		default:
			throw new IllegalArgumentException("Invalid scheduleTimeUnit. Must be either "+TimeUnit.HOURS.name()+" or "+TimeUnit.DAYS.name());
		}
	}

	public void setJob(Job job) {
		this.job = job;
	}

	public void setScheduleTimeUnit(TimeUnit scheduleTimeUnit) {
		switch(scheduleTimeUnit){
		case HOURS:
			timespanBetweenLaunches = DateTimeConstants.MILLIS_PER_HOUR;
			break;
		case DAYS:
			timespanBetweenLaunches = DateTimeConstants.MILLIS_PER_DAY;
			break;
		default:
			throw new IllegalArgumentException("Invalid accumulationUnit. Valid values are " + 
					TimeUnit.DAYS.name() + " and " + TimeUnit.DAYS.name());			
		}
		this.scheduleTimeUnit = scheduleTimeUnit; 
	}
	
	public void setScheduleTimeUnit(String scheduleTimeUnit) {
		setScheduleTimeUnit(TimeUnit.valueOf(scheduleTimeUnit));
	}

}
