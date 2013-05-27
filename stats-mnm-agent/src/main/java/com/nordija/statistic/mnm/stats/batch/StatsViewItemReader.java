package com.nordija.statistic.mnm.stats.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.AfterStep;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.context.annotation.Scope;

import com.nordija.statistic.mnm.stats.StatsView;

@Scope("step")
public class StatsViewItemReader extends JdbcCursorItemReader<StatsView> {
	private static final Logger logger = LoggerFactory.getLogger(StatsViewItemReader.class);
	
	long from, to;
	
	@BeforeStep
	public void beforeStep(StepExecution stepExecution){
		JobParameters jobParams = stepExecution.getJobParameters();
		this.from = jobParams.getLong("from");
		this.to = jobParams.getLong("to");

		String sql = new StringBuilder("select * from stats_view where toTS >= ").
				append(from).append(" and toTS < "+to).
				append(" order by toTS").
				toString();
		setSql(sql);

		logger.info("Using sql: "+sql);
	}

	@AfterStep
    public ExitStatus afterStep(StepExecution stepExecution){
    	return ExitStatus.COMPLETED;
    }
	
}
