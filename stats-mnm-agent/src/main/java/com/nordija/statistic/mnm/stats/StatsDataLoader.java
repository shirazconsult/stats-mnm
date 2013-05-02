package com.nordija.statistic.mnm.stats;

import java.util.Collection;
import java.util.List;

import org.springframework.context.Lifecycle;

public interface StatsDataLoader extends Lifecycle {
	public final static String[] columns = {
		"id", "cusRef", "devRef", "devType", "devModel", "timeZone", "cusGrRefs",
		"ref", "type", "name", "time", "duration", "extra",  "deliveredTS", "insertedTS"};
	public final static String[] viewColumns = {
		"cusRef", "name", "type", "firsDeliveredTS", "lastDeliveredTS", "duration", 
		"devModel", "title"};
	public final static String[] events = {
		"LiveUsage", "widgetShow", "VodUsageMOVIE", "VodUsageTRAILER", "DvrUsage", 
		"WebTVLogin", "STARTOVERUsage", "TIMESHIFTUsage", "movieRent", "shopLoaded", 
		"adAdtion"};

	
	public final static int deliveredTSIdx = columns.length-2;
	public final static int viewTypeIdx = 2;  // the index of "type" column in viewColumns 
	
	void loadCache() throws Exception;
	
	// methods for querying the cache views
	Collection<List<Object>> getNextViewPages(int numOfPages);
	Collection<List<Object>> getNextViewPage(long from);
	
	// methods for querying the backing cache
	Collection<List<Object>> getNextPages(int numOfPages);
	Collection<List<Object>> getNextPage();
	Collection<List<Object>> getNextPage(long from);
	Collection<List<Object>> getNextPages(long from, int numOfPages);
}
