package com.nordija.statistic.monitoring;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;

import junit.framework.Assert;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.junit4.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import com.nordija.statistic.monitoring.aggregatorImpl.AggregatorMonitorHelper;
import com.nordija.statistic.monitoring.aggregatorImpl.AggregatorMonitorImpl;

/**
 * This test requires that an aggregator-daemon is running and can be reached by the jmx-url which is defined in the application-test.properties.
 * For this reason this test is disabled per default. If you want to run it anyway, remove the "@Ignore" annotation. 
 * 
 * @author farhad
 * @since Aug 28, 2012
 * @version
 */
@RunWith(CamelSpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(locations = { "classpath:applicationContext-test.xml" })
@UseAdviceWith(true)
@Ignore
public class AggregatorMonitorTest {
	@Value("${aggregator.data.dir}")
	private String dataDir;
	@Value("${aggregator.data.file.prefix}")
	private String filePrefix;
	@Value("${aggregator.routes}")
	private String[] aggregatorRoutes;

	@Autowired
	private ModelCamelContext context;
	@Autowired
	private AggregatorMonitorImpl aggregatorMonitor;
	@Autowired
	private AggregatorMonitorHelper aggregatorMonitorHelper;
	
	@Produce(uri = "activemq:queue:stat.inbound")
	private ProducerTemplate template;

	@Test
	public void testDynamicData() throws Exception{	
		context.start();
		aggregatorMonitor.start();
		
		for (int i=0; i<=100; i++) {			
			template.sendBody("Test message");
			Thread.sleep(50);
		}
		

		// assert if all the routes have got a datafile
		for (String routeId : aggregatorRoutes) {
			String filename = aggregatorMonitorHelper.getFilenameForMBean(routeId);
			File f = new File(dataDir, filename);
			Assert.assertTrue(f.exists());
		}
		
		// assert if all the datafiles have some content
		String[] brokerDataFiles = getAggregatorDataFiles();
		for (String f : brokerDataFiles) {			
			File file = new File(dataDir, f);
			Assert.assertTrue(file.exists());
			BufferedReader br = new BufferedReader(new FileReader(file));
			String line = br.readLine();
			Assert.assertFalse(StringUtils.isBlank(line));
		}
	}
	
	@Before
	public void setUp(){
		String[] listOfFiles = getAggregatorDataFiles();
		for (String f : listOfFiles) {
			new File(dataDir, f).delete();
		}
	}
	
	private String[] getAggregatorDataFiles(){
		File staticdataDir = new File(dataDir);
		if(staticdataDir.exists()){
			return staticdataDir.list(new FilenameFilter() {				
				@Override
				public boolean accept(File dir, String filename) {
					return (filename.startsWith(filePrefix));
				}
			});
		}else{
			throw new IllegalArgumentException(dataDir+" does not exist.");
		}
	}
}
