package com.nordija.statistic.mnm.stats.livedatasimulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class LiveStatsDataSimulator {
	private static Logger logger = LoggerFactory.getLogger(LiveStatsDataSimulator.class);

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = null;
		try {
			context = loadContext();
		} catch (Exception e) {
			System.out.println("Unable ot load application context " + e.getMessage());
			System.exit(0);
		}

		LiveStatsDataSimulator main = new LiveStatsDataSimulator();

		AutowireCapableBeanFactory acbf = context.getAutowireCapableBeanFactory();
		acbf.autowireBeanProperties(main, AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, false);
		acbf.initializeBean(main, "main");

		try {
			main.launchBatch();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}

	@Autowired private JobLauncher jobLauncher;
	@Autowired private Job migrateLiveStatsJob;

	private void launchBatch() throws JobExecutionAlreadyRunningException,
			JobRestartException, JobInstanceAlreadyCompleteException,
			JobParametersInvalidException {
		logger.info("Starting to migrate live-stats data to the statistic base ...");
		jobLauncher.run(migrateLiveStatsJob, new JobParametersBuilder().toJobParameters());
	}

	private static ClassPathXmlApplicationContext loadContext()
			throws Exception {
		logger.info("Loading context ....");
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(
				new String[] {
						"classpath*:META-INF/spring/applicationContext.xml",
						"classpath:META-INF/spring/db-readwrite-batch.xml" });

		return context;
	}
}
