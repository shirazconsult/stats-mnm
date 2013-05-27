package com.nordija.statistic.mnm.stats.batch;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.annotation.OnWriteError;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;

import com.nordija.statistic.mnm.stats.StatsView;

public class StatsViewItemWriter implements ItemWriter<List<StatsView>> {
	private static final Logger logger = LoggerFactory.getLogger(StatsViewItemWriter.class);
	
	private JdbcBatchItemWriter<StatsView> batchItemWriter;
	private ExecutionContext executionContext;
	
	@Override
	public void write(List<? extends List<StatsView>> items) throws Exception {
		List<StatsView> mergedItems = new ArrayList<StatsView>();
		for (List<StatsView> itemList : items) {			
			mergedItems.addAll(itemList);
		}
		
		batchItemWriter.write(mergedItems);
	}

	public void setBatchItemWriter(JdbcBatchItemWriter<StatsView> batchItemWriter) {
		this.batchItemWriter = batchItemWriter;
	}
	
	@BeforeStep
	public void beforeStep(StepExecution stepExecution){
		executionContext = stepExecution.getExecutionContext();
	}
	
	@OnWriteError
    public void onWriteError(Exception exception, List<? extends StatsView> items){
		logger.error("Failed to write {} records.", items.size());
	}
}
