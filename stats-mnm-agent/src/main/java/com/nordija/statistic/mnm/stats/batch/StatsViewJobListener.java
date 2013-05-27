package com.nordija.statistic.mnm.stats.batch;

import javax.sql.DataSource;

import org.springframework.batch.core.ExitStatus;
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
	private ExecutionContext executionContext;
	@Autowired private DataSource dataSource;
	
	@Override
	public void beforeJob(JobExecution jobExecution) {
		executionContext = jobExecution.getExecutionContext();
		JobInstance jobInstance = jobExecution.getJobInstance();
		JobParameters jobParams = jobInstance.getJobParameters();

		long from = jobParams.getLong("from");
		long to = jobParams.getLong("to");

		ExitStatus exitStatus = jobExecution.getExitStatus();
		if(exitStatus == ExitStatus.FAILED){
			// clean up. This job is going to be restarted.
			JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
			jdbcTemplate.execute("delete from stats_view_hourly where fromTS >= "+from+" and toTS < "+to);
		}
		
		executionContext.putLong("from", from);
		executionContext.putLong("to", to);
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		// nothing for now
	}
}
