package com.nordija.statistic.mnm.stats.batch;

import java.util.Date;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class CleanupTask implements Tasklet {
	private static final Logger logger = LoggerFactory.getLogger(CleanupTask.class);
	
	public final static String DELETE_BEFORE_DATE_PARAM = "deleteBeforeDate";
	public final static String TABLE_PARAM = "table";
	
	@Autowired private DataSource dataSource;
	
	@Override
	public RepeatStatus execute(StepContribution contribution,
			ChunkContext chunkContext) throws Exception {
		String jobName = chunkContext.getStepContext().getJobName();
		logger.info("Starting cleanup job {}.", jobName);

		Map<String, Object> jobParameters = chunkContext.getStepContext().getJobParameters();
		Date date = (Date)jobParameters.get(DELETE_BEFORE_DATE_PARAM);
		String table = (String)jobParameters.get(TABLE_PARAM);
		
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		
		int deleted = jdbcTemplate.update("delete from "+table+" where toTS < "+date.getTime());
		if(deleted >= 1){			
			logger.info("{} deleted {} records with timestamps before {} from {} table.", new Object[]{jobName, deleted, date, table});
		}
		logger.info("Completed cleanup job {}.", jobName);
		return RepeatStatus.FINISHED;
	}
}
