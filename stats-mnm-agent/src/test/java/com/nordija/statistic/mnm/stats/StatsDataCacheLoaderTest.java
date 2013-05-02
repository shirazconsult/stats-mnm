package com.nordija.statistic.mnm.stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableSet;

import javax.sql.DataSource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.nordija.statistic.mnm.stats.livedatasimulation.StatsLiveDataProcessor;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={
		"classpath*:META-INF/spring/applicationContext.xml",
		"classpath:META-INF/spring/db-readwrite-batch.xml"
	})
public class StatsDataCacheLoaderTest {
	private static Logger logger = LoggerFactory.getLogger(StatsDataCacheLoaderTest.class);
	
	@Autowired private JobLauncher jobLauncher;
	@Autowired private Job migrateLiveStatsJob;
	@Autowired @Qualifier("streamingStatsDataCacheLoader") 
	private StreamingStatsDataCacheLoader streamingStatsDataCacheLoader;
	@Autowired private StatsLiveDataProcessor statsLiveDataProcessor;
	@Autowired @Qualifier("dataSource")
	private DataSource dataSource;
	
	private JobExecution liveStatsDataJob;
	
	@Before
	public void setup() throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, JobParametersInvalidException, InterruptedException{
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.execute("delete from statistic");
		
		jobLauncherThread.start();
		
		// wait for some data to come in to the database
		Thread.sleep(1000);
		
		streamingStatsDataCacheLoader.setMaxCacheSizeInMillis(100000);
		streamingStatsDataCacheLoader.setPageSizeInMillis(5000);
		logger.info("Starting the StatsDataCacheLoader thread.");
		streamingStatsDataCacheLoader.start();
	}

	public void tearDown(){
		try {			
			streamingStatsDataCacheLoader.stop();
		} catch (Exception e) {
			logger.error("Could not stop the StatsDataCacheLoader thread.", e);
		}

		if(liveStatsDataJob != null && liveStatsDataJob.isRunning()){
			try {				
				liveStatsDataJob.stop();
			} catch (Exception e) {
				logger.error("Could not stop the LiveStatsDataJob.", e);
			}
		}
	}
	
	@Test
	public void testStatsDataCache() throws InterruptedException{
		Thread.sleep(30000);
		for(int i=0; i<6; i++){			
			Thread.sleep(2000);
			if(streamingStatsDataCacheLoader.cache.isEmpty()){
				logger.warn("Cache is empty.");
			}else{
				logger.info("Cache size is {}.", streamingStatsDataCacheLoader.cache.size());
			}
			Collection<List<Object>> nextPage = streamingStatsDataCacheLoader.getNextPage();
			List<List<Object>> page = new ArrayList<List<Object>>(nextPage);
			logger.info("Fetching next page from cache. Size = {}", page.size());
			if(!page.isEmpty()){				
				logger.info("     page[first] = {}", page.get(0));
				logger.info("     page[last] = {}", page.get(page.size()-1));
				// assert if all the rows are sorted
				long previousTS = 0;
				for (List<Object> rec : page) {
					long deliveredTS = (Long)rec.get(StatsDataLoader.deliveredTSIdx);
					Assert.assertTrue(deliveredTS >= previousTS);
					previousTS = deliveredTS;
				}
			}			
		}

		long numOfViewRecs = 0;
		long previousTS = 0;
		for (Entry<CacheKey, List<Object>> entry : streamingStatsDataCacheLoader.cache.entrySet()) {
			CacheKey key = entry.getKey();
			List<Object> rec = entry.getValue();
			if(key.cacheViewKey != null){
				// assert keys
				numOfViewRecs++;
				Assert.assertNotNull(key.cacheViewKey.cacheKey);
				Assert.assertEquals(key, key.cacheViewKey.cacheKey);

				// assert deliveredTS
				long deliveredTS = (Long)rec.get(StatsDataLoader.deliveredTSIdx);
				Assert.assertTrue(deliveredTS >= previousTS);
				previousTS = deliveredTS;
			}
		}
		logger.info("Number of view records: {}", numOfViewRecs);
		Assert.assertTrue(numOfViewRecs > 0);
		
	}
	
	Thread jobLauncherThread = new Thread(new Runnable() {			
		@Override
		public void run() {
			statsLiveDataProcessor.setDelayMillis(20);
			statsLiveDataProcessor.setNumOfRecordsBeforDealy(1000);
			statsLiveDataProcessor.setTimeOffsetFromNow(-1000);
			logger.info("Starting to migrate live-stats data to the statistic base ...");
			try {
				liveStatsDataJob = jobLauncher.run(migrateLiveStatsJob, new JobParametersBuilder().toJobParameters());
			} catch (Exception e) {
				logger.error("Failed to start the live stats data migrator.", e);					
			}				
		}
	});
}
