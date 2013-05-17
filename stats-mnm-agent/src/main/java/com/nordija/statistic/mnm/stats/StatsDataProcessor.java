package com.nordija.statistic.mnm.stats;

import org.springframework.context.Lifecycle;

public interface StatsDataProcessor extends Lifecycle {
	public static String[] viewColumns = {
		"type", "name", "title", "sum", "minDuration", "maxDuration", "totalDuration", "fromTS", "toTS"};
	public static String[] events = {
		"adAdtion", "DvrUsage", "LiveUsage", "movieRent", "SelfCareSUBSCRIBE", "shopLoaded", 
		"STARTOVERUsage", "TIMESHIFTUsage", "VodUsageMOVIE", "VodUsageTRAILER", 
		"WebTVLogin", "widgetShow" 
	};
	
	static final int viewTypeIdx = 0;
	static final int viewNameIdx = 1;
	static final int viewTitleIdx = 2;
	static final int viewSumIdx = 3;
	static final int viewMinDurationIdx = 4;
	static final int viewMaxDurationIdx = 5;
	static final int viewTotalDurationIdx = 6;
	static final int viewFromTSIdx = 7; 
	static final int viewToTSIdx = 8;
	static final int viewCompletedIdx = 9;
	
	static final int typeIdx = 8;
	static final int nameIdx = 9;
	static final int durationIdx = 11;
	static final int extraIdx = 12;
	static final int deliveredTSIdx = 13;
	

}
