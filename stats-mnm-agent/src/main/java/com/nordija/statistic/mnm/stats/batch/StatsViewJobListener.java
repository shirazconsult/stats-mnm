package com.nordija.statistic.mnm.stats.batch;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("statsViewJobListener")
public class StatsViewJobListener implements JobExecutionListener {
	private static final Logger logger = LoggerFactory.getLogger(StatsViewJobListener.class);
	
	@Autowired private DataSource dataSource;
	
	@Override
	public void beforeJob(JobExecution jobExecution) {
		ExecutionContext executionContext = jobExecution.getExecutionContext();
		JobInstance jobInstance = jobExecution.getJobInstance();
		JobParameters jobParams = jobInstance.getJobParameters();

		long from = jobParams.getLong("from");
		long to = jobParams.getLong("to");

		String jobName = jobInstance.getJobName();
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		
		if(jobName.equals("hourlyJob") || jobName.equals("dailyJob")){
			String table = jobName.equals("hourlyJob") ? "stats_view_hourly" : "stats_view_daily";
			int deleted = jdbcTemplate.update("delete from "+table+" where fromTS >= "+from+" and toTS < "+to);
			if(deleted >= 1){
				logger.info("Deleted {} records from {}, because the previous job was not finished completely.", deleted, table);
			}
		}
		
		executionContext.putLong("from", from);
		executionContext.putLong("to", to);
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		// nothing for now
	}
}
