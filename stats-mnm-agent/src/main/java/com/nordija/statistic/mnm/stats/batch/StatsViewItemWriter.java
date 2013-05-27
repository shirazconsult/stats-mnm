package com.nordija.statistic.mnm.stats.batch;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;

import com.nordija.statistic.mnm.stats.StatsView;

public class StatsViewItemWriter implements ItemWriter<List<StatsView>> {

	private JdbcBatchItemWriter<StatsView> batchItemWriter;
	
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
}
