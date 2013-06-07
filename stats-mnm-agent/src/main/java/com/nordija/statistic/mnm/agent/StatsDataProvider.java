package com.nordija.statistic.mnm.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.nordija.statistic.mnm.rest.model.ListResult;
import com.nordija.statistic.mnm.rest.model.NestedList;
import com.nordija.statistic.mnm.stats.StatsDataProcessor;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiError;
import com.wordnik.swagger.annotations.ApiErrors;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

@Service("statsDataProvider")
@Path("/stats")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Api(value = "/stats", description = "Fetch Statistics Data for Visualization")
public class StatsDataProvider {
	private static final Logger logger = LoggerFactory.getLogger(StatsDataProvider.class);
	
	@Autowired @Qualifier("dataSource") private DataSource dataSource;
	
	private JdbcTemplate jdbcTemplate;
	
	@GET
	@Path("/viewcolumns")
	@ApiOperation(value = "Fetch column names for data records.", 
		notes = "Data values in the records are returned in the same order as the column names are returned here.",
		responseClass = "com.nordija.statistic.mnm.rest.model.ListResult[java.lang.String]")
    @ApiErrors(value = { @ApiError(code = 500, reason = "If for some reason column metadata cannot be returned.")})	
	public ListResult<String> getViewColumnNames(){
		logger.debug("Returning view column metadata.");
		try{
			return new ListResult<String>(Arrays.asList(StatsDataProcessor.viewColumns));
		}catch(Exception e){ 
			logger.error("Failed to retrieve view column metadata for statistics.");
			throw new WebApplicationException(e, 500);
		}
	}

	@GET
	@Path("/viewpage/{from}/{to}")
	@ApiOperation(value = "Fetch stats data for the period defined by the two unix timestamp parameters.",
			notes = "This method is typically used to retrieve live data with some sort of paging functionality.",
			responseClass = "com.nordija.statistic.mnm.rest.model.NestedList[java.lang.Object]")
    @ApiErrors(value = { @ApiError(code = 500, reason = "Any errors.")})
	public NestedList<Object> getViewPage(
			@ApiParam(value="Start time as unix-timestamp", required=true) 
			@PathParam("from") long from, 
			@ApiParam(value="End time as unix-timestamp", required=true) 
			@PathParam("to") long to) {
		logger.debug("Returning statistics view rows from {} to {}.", from, to);
		try {
			return fetch(from, to);
		} catch (Exception e) {
			logger.error("Failed to retrieve records for date interval {} - {}. Reason {}", new Object[]{from, to, e.getMessage()});
			throw new WebApplicationException(e, 500);
		}		
	}

	@GET
	@Path("/view/{from}/{to}")
	@ApiOperation(value = "Fetch stats data for the period defined by the two timestamp parameters.", 
		notes="The timestamp parameters must comply with the ISO8601 formats (See http://www.w3.org/TR/NOTE-datetime). " +
				"Ex. '2005-03-25', '2005-03-25T8:00', '2005-03', '2005-W12' etc.",
		responseClass = "com.nordija.statistic.mnm.rest.model.NestedList[java.lang.Object]")
    @ApiErrors(value = { @ApiError(code = 500, reason = "If the given parameters don't comply with the ISO8601 standards.")})
	public NestedList<Object> getViewPage(
			@ApiParam(value="Start time as ISO8601-string", required=true) 
			@PathParam("from") String from, 
			@ApiParam(value="End time as ISO8601-string", required=true) 
			@PathParam("to") String to) {
		DateTime fromDate, toDate;
		try{
			fromDate = DateTime.parse(from);
			toDate = DateTime.parse(to);
		}catch(Exception e){
			String err = "Not a valid date specification. Must comply with ISO8601 specification.";
			logger.error(err);
			throw new WebApplicationException(new IllegalArgumentException(err), 500);
		}
		return getViewPage(fromDate.getMillis(), toDate.getMillis());
	}

	@GET
	@Path("/viewpage/{type}/{from}/{to}")
	@ApiOperation(value = "Fetch stats data for the stats-event type and for the period defined by the two unitx timestamp parameters.", 
			notes="This method is typically used to retrieve live data with some sort of paging functionality.",
			responseClass = "com.nordija.statistic.mnm.rest.model.NestedList[java.lang.Object]")
	@ApiErrors(value = { @ApiError(code = 500, reason = "If the 'type' is not a valid stats-event type or the given timestamp parameters are not valid.")})
	public NestedList<Object> getViewPage(
			@ApiParam(value="Statistics event type", allowableValues="adAdtion,DvrUsage,LiveUsage,movieRent," + 
					"SelfCareSUBSCRIBE,shopLoaded,STARTOVERUsage,TIMESHIFTUsage,VodUsageMOVIE,VodUsageTRAILER," + 
					"WebTVLogin,widgetShow", required=true) 
			@PathParam("type") String type, 
			@ApiParam(value="Start time as unix-timestamp", required=true) 
			@PathParam("from") long from, 
			@ApiParam(value="Start time as unix-timestamp", required=true) 
			@PathParam("to") long to) {
		logger.debug("Returning statistics view rows from {} to {} for {}.", new Object[]{from, to, type});
		try {
			return fetch(type, from, to);
		} catch (Exception e) {
			logger.error("Failed to retrieve records for date interval {} - {}. Reason {}", new Object[]{from, to, e.getMessage()});
			throw new WebApplicationException(e, 500);
		}		
	}

	@GET
	@Path("/view/{type}/{from}/{to}")
	@ApiOperation(value = "Fetch stats data for the stats-event type and for the period defined by the two timestamp parameters.", 
		notes="The timestamp parameters must comply with the ISO8601 formats (See http://www.w3.org/TR/NOTE-datetime). " +
				"Ex. '2005-03-25', '2005-03-25T8:00', '2005-03', '2005-W12' etc.",
		responseClass = "com.nordija.statistic.mnm.rest.model.NestedList[java.lang.Object]")
	@ApiErrors(value = { @ApiError(code = 500, reason = "If the 'type' is not a valid stats-event type or the given timestamp parameters don't comply with the ISO8601 standards.")})
	public NestedList<Object> getViewPage(
			@ApiParam(value="Statistics event type", allowableValues="adAdtion,DvrUsage,LiveUsage,movieRent," + 
					"SelfCareSUBSCRIBE,shopLoaded,STARTOVERUsage,TIMESHIFTUsage,VodUsageMOVIE,VodUsageTRAILER," + 
					"WebTVLogin,widgetShow", required=true) 
			@PathParam("type") String type, 
			@ApiParam(value="Start time as ISO8601-string", required=true) 
			@PathParam("from") String from, 
			@ApiParam(value="End time as ISO8601-string", required=true) 
			@PathParam("to") String to) {
		DateTime fromDate, toDate;
		try{
			fromDate = DateTime.parse(from);
			toDate = DateTime.parse(to);
		}catch(Exception e){
			String err = "Not a valid date specification. Must comply with ISO8601 specification.";
			logger.error(err);
			throw new WebApplicationException(new IllegalArgumentException(err), 500);
		}
		return getViewPage(type, fromDate.getMillis(), toDate.getMillis());		
	}
	
	@GET
	@Path("/viewpage/{type}/{from}/{to}/{options}")
	@ApiOperation(value = "Fetch stats data for the stats-event type and for the period defined by the two unitx timestamp parameters.", 
			notes="This method is typically used to retrieve live data with some sort of paging functionality.<br/>" +
					"Allowable values for the 'options' parameter are : [title],[duration|viewers],[top|low],[1-9][0-9]*" +
					"<ul><li>[title]: If the data-lookup should take the title into account. Specify this if searching data for TV programs.</li>" +
					"<li>[duration|viewers]: The results should be returned based on the total watched/used or number of viewers. The duration will not apply to all events.</li>" +
					"<li>[top|low]: Return the most or least used/viewed events</li>" +
					"<li>[1-9][0-9]*: How many records should be returned</li></ul>",					
			responseClass = "com.nordija.statistic.mnm.rest.model.NestedList[java.lang.Object]")
	@ApiErrors(value = { @ApiError(code = 500, reason = "If the 'type' is not a valid stats-event type or the given timestamp parameters are not valid.")})
	public NestedList<Object> getViewPage(
			@ApiParam(value="Statistics event type", allowableValues="adAdtion,DvrUsage,LiveUsage,movieRent," + 
					"SelfCareSUBSCRIBE,shopLoaded,STARTOVERUsage,TIMESHIFTUsage,VodUsageMOVIE,VodUsageTRAILER," + 
					"WebTVLogin,widgetShow", required=true) 
			@PathParam("type") String type, 
			@ApiParam(value="Start time as unix-timestamp", required=true) 			
			@PathParam("from") long from, 
			@ApiParam(value="Start time as unix-timestamp", required=true) 
			@PathParam("to") long to,
			@ApiParam(value="Options to furthur limit the fetch. It is a comma-separated list of options",
				required=false) 
			@PathParam("options") String options) {
		logger.debug("Returning statistics view rows from {} to {} for {} with options {}.", new Object[]{from, to, type, options});
		try {
			return fetch(type, from, to, new Options(options));
		} catch (Exception e) {
			logger.error("Failed to retrieve records for date interval {} - {}. Reason {}", new Object[]{from, to, e.getMessage()});
			throw new WebApplicationException(e, 500);
		}		
	}

	@GET
	@Path("/view/{type}/{from}/{to}/{options}")
	@ApiOperation(value = "Fetch stats data for the stats-event type and for the period defined by the two timestamp parameters.", 
			notes="The timestamp parameters must comply with the ISO8601 formats (See http://www.w3.org/TR/NOTE-datetime). " +
					"Ex. '2005-03-25', '2005-03-25T8:00', '2005-03', '2005-W12' etc.<br/>" +
					"Allowable values for the 'options' parameter are : [title],[duration|viewers],[top|low],[1-9][0-9]*" +
					"<ul><li>[title]: If the data-lookup should take the title into account. Specify this if searching data for TV programs.</li>" +
					"<li>[duration|viewers]: The results should be returned based on the total watched/used or number of viewers. The duration will not apply to all events.</li>" +
					"<li>[top|low]: Return the most or least used/viewed events</li>" +
					"<li>[1-9][0-9]*: How many records should be returned</li></ul>",					
			responseClass = "com.nordija.statistic.mnm.rest.model.NestedList[java.lang.Object]")
	public NestedList<Object> getViewPage(
			@ApiParam(value="Statistics event type", allowableValues="adAdtion,DvrUsage,LiveUsage,movieRent," + 
					"SelfCareSUBSCRIBE,shopLoaded,STARTOVERUsage,TIMESHIFTUsage,VodUsageMOVIE,VodUsageTRAILER," + 
					"WebTVLogin,widgetShow", required=true) 
			@PathParam("type") String type, 
			@ApiParam(value="Start time as ISO8601-string", required=true) 
			@PathParam("from") String from, 
			@ApiParam(value="End time as ISO8601-string", required=true) 
			@PathParam("to") String to,
			@ApiParam(value="Options to furthur limit the fetch. It is a comma-separated list of options",
					required=false) 
			@PathParam("options") String options) {
		DateTime fromDate, toDate;
		try{
			fromDate = DateTime.parse(from);
			toDate = DateTime.parse(to);
		}catch(Exception e){
			String err = "Not a valid date specification. Must comply with ISO8601 specification.";
			logger.error(err);
			throw new WebApplicationException(new IllegalArgumentException(err), 500);
		}
		return getViewPage(type, fromDate.getMillis(), toDate.getMillis(), options);
	}
	
	@GET
	@Path("/viewbatch/{type}/{from}/{to}/{options}")
	@ApiOperation(value = "Fetch stats data for the stats-event type and for the period defined by the two timestamp parameters.", 
			notes="The data is returned as list of lists, devided and grouped into predefined periods." + 
					"<ul><li>If the specified period is less than a day, then data is grouped on hourly basis</li>" +
					"<ul><li>If the specified period is less than a week, then data is grouped on daily basis</li>" +
					"<ul><li>If the specified period is less than a month, then data is grouped on weekly basis</li>" +
					"<ul><li>Otherwise data is grouped on monthly basis</li></ul><br/>" +
					"Allowable values for the 'options' parameter are : [title],[duration|viewers],[top|low],[1-9][0-9]*" +
					"<ul><li>[title]: If the data-lookup should take the title into account. Specify this if searching data for TV programs.</li>" +
					"<li>[duration|viewers]: The results should be returned based on the total watched/used or number of viewers. The duration will not apply to all events.</li>" +
					"<li>[top|low]: Return the most or least used/viewed events</li>" +
					"<li>[1-9][0-9]*: How many records should be returned</li></ul>",
			responseClass = "com.nordija.statistic.mnm.rest.model.ListResult[com.nordija.statistic.mnm.rest.model.NestedList[java.lang.Object]]")
	public ListResult<NestedList<Object>> getViewPageInBatch(
			@ApiParam(value="Statistics event type", allowableValues="adAdtion,DvrUsage,LiveUsage,movieRent," + 
					"SelfCareSUBSCRIBE,shopLoaded,STARTOVERUsage,TIMESHIFTUsage,VodUsageMOVIE,VodUsageTRAILER," + 
					"WebTVLogin,widgetShow", required=true) 
			@PathParam("type") String type, 
			@ApiParam(value="Start time as ISO8601-string", required=true) 
			@PathParam("from") String from, 
			@ApiParam(value="End time as ISO8601-string", required=true) 
			@PathParam("to") String to,
			@ApiParam(value="Options to furthur limit the fetch. It is a comma-separated list of options",
					required=false) 
			@PathParam("options") String options) {

		logger.debug("Returning statistics view rows in batch from {} to {} for {} with options {}.", new Object[]{from, to, type, options});
		DateTime fromDate, toDate;
		try{
			fromDate = DateTime.parse(from);
			toDate = DateTime.parse(to);
		}catch(Exception e){
			String err = "Not a valid date specification. Must comply with ISO8601 specification.";
			logger.error(err);
			throw new WebApplicationException(new IllegalArgumentException(err), 500);
		}
		
		ListResult<NestedList<Object>> res = new ListResult<NestedList<Object>>();
		long diff = toDate.getMillis() - fromDate.getMillis();
		if(diff <= DateTimeConstants.MILLIS_PER_HOUR){
			// return one response list for the hour
		}else if(diff <= DateTimeConstants.MILLIS_PER_DAY){
			// divide into hours and create up to 24 response-lists
			int diffInHours = (int)(diff / DateTimeConstants.MILLIS_PER_HOUR);
			DateTime fromFloor = fromDate.hourOfDay().roundFloorCopy();
			for(int i=0; i< diffInHours; i++){
				DateTime nextFrom = fromFloor.plusHours(i);
				DateTime nextTo = nextFrom.plusHours(1);
				logger.debug("Fetching from {} to {}.", nextFrom, nextTo);
				NestedList<Object> fetch = fetch(type, nextFrom.getMillis(), nextTo.getMillis(), new Options(options).setTimeunit(TimeUnit.hourly));
				res.addElement(fetch);
			}
			return res;
		}else if(diff <= DateTimeConstants.MILLIS_PER_WEEK){
			// divide into days and create up to 7 response lists
			int diffInDays = (int)(diff / DateTimeConstants.MILLIS_PER_DAY);
			DateTime fromFloor = fromDate.dayOfWeek().roundFloorCopy();
			for(int i=0; i< diffInDays; i++){
				DateTime nextFrom = fromFloor.plusDays(i);
				DateTime nextTo = nextFrom.plusDays(1);
				logger.debug("Fetching from {} to {}.", nextFrom, nextTo);
				NestedList<Object> fetch = fetch(type, nextFrom.getMillis(), nextTo.getMillis(), new Options(options).setTimeunit(TimeUnit.daily));
				res.addElement(fetch);
			}
			return res;
		}else if(diff <= (DateTimeConstants.MILLIS_PER_WEEK * 4L)){
			// divide into weeks and create up to 4 reponse lists
			int diffInWeeks = (int)(diff / DateTimeConstants.MILLIS_PER_WEEK);
			DateTime fromFloor = fromDate.weekOfWeekyear().roundFloorCopy();
			for(int i=0; i< diffInWeeks; i++){
				DateTime nextFrom = fromFloor.plusWeeks(i);
				DateTime nextTo = nextFrom.plusWeeks(1);
				logger.debug("Fetching from {} to {}.", nextFrom, nextTo);
				NestedList<Object> fetch = fetch(type, nextFrom.getMillis(), nextTo.getMillis(), new Options(options).setTimeunit(TimeUnit.weekly));
				res.addElement(fetch);
			}
			return res;
		}else{
			// divide into months and create up to 12 response lists as there are months
			int diffInMonths = 0;
			if(toDate.year().get() == fromDate.year().get()){
				diffInMonths = toDate.monthOfYear().get() - fromDate.monthOfYear().get();
			}else{
				diffInMonths = 12 - fromDate.monthOfYear().get() + toDate.monthOfYear().get();
			}
			DateTime fromFloor = fromDate.monthOfYear().roundFloorCopy();
			if(diffInMonths == 0){
				DateTime nextTo = fromFloor.plusMonths(1);
				logger.debug("Fetching from {} to {}.", fromFloor, nextTo);
				NestedList<Object> fetch = fetch(type, fromFloor.getMillis(), nextTo.getMillis(), new Options(options).setTimeunit(TimeUnit.weekly));
				res.addElement(fetch);
			}else{
				for(int i=0; i < diffInMonths; i++){					
					DateTime nextFrom = fromFloor.plusMonths(i);
					DateTime nextTo = nextFrom.plusMonths(1);
					logger.debug("Fetching from {} to {}.", nextFrom, nextTo);
					NestedList<Object> fetch = fetch(type, nextFrom.getMillis(), nextTo.getMillis(), new Options(options).setTimeunit(TimeUnit.monthly));
					res.addElement(fetch);
				}
			}
			return res;			
		}
		
		return null;
	}
		
	private NestedList<Object> fetch(String type, long from, long to, Options options) {
		NestedList<Object> res = new NestedList<Object>();
		StringBuilder q = new StringBuilder("select `type`, `name`, ").
				append(options.title != null && options.title.equals("title") ? "`title`" : "'' as `title`").
				append(", sum(`viewers`) as viewers, sum(`duration`) as duration, toTS from ").
				append(getTable(from, to)).
				append(" where type = ? and toTS > ? and toTS <= ?  ").
				append(options.title != null && options.title.equals("title") ? 
						" group by type, name, title order by " : " group by type, name order by ").
				append(options.orderBy).append(" ").
				append(options.order).
				append(" limit 0, ?");
		List<Map<String, Object>> resultList = getJdbcTemplate().queryForList(
				q.toString(),
				type, from, to, options.size);
		for (Map<String, Object> row : resultList) {
			ListResult<Object> rec = new ListResult<Object>();
			for (String col : StatsDataProcessor.topViewColumns) {
				if(col.equals("time")){
					rec.addElement(getFormattedTime(options.timeunit, (Long)row.get("toTS")));
				}else{
					rec.addElement(row.get(col));
				}
			}
			res.addRow(rec);
		}
		return res;
	}

	private final static DateTimeFormatter fmtHour = ISODateTimeFormat.dateHour();
	private final static DateTimeFormatter fmtDay = ISODateTimeFormat.date();
	private final static DateTimeFormatter fmtMonth = ISODateTimeFormat.yearMonth();
	private final static DateTimeFormatter fmtWeek = ISODateTimeFormat.weekyearWeek();
	private String getFormattedTime(TimeUnit timeunit, long ts){
		DateTime dt = new DateTime(ts);	
		switch(timeunit){
		case daily:
			return fmtDay.print(dt);
		case weekly:
			return fmtWeek.print(dt);
		case monthly:
			return fmtMonth.print(dt);
		default:
			return fmtHour.print(dt);
		}
	}
	
	private NestedList<Object> fetch(String type, long from, long to) {
		NestedList<Object> res = new NestedList<Object>(); 
		List<Map<String, Object>> resultList = getJdbcTemplate().queryForList(
				"select * from " +
				getTable(from, to) + 
				" where type = ? and toTS > ? and toTS <= ? order by fromTS, toTS", 
				type, from, to);
		for (Map<String, Object> row : resultList) {
			ListResult<Object> rec = new ListResult<Object>();
			for (String col : StatsDataProcessor.viewColumns) {					
				rec.addElement(row.get(col));
			}
			res.addRow(rec);
		}
		return res;
	}

	private NestedList<Object> fetch(long from, long to){
		NestedList<Object> res = new NestedList<Object>(); 
		List<Map<String, Object>> resultList = getJdbcTemplate().queryForList(
				"select * from " +
				getTable(from, to) +
				" where toTS > ? and toTS <= ? order by fromTS, toTS", 
				from, to);
		for (Map<String, Object> row : resultList) {
			ListResult<Object> rec = new ListResult<Object>();
			for (String col : StatsDataProcessor.viewColumns) {					
				rec.addElement(row.get(col));
			}
			res.addRow(rec);
		}
		return res;
	}
	
	public JdbcTemplate getJdbcTemplate() {
		if(jdbcTemplate == null){
			jdbcTemplate = new JdbcTemplate(dataSource);
		}
		return jdbcTemplate;
	}	
	
	private String getTable(long from, long to){
		long diff = to - from;
		if(diff <= DateTimeConstants.MILLIS_PER_HOUR){			
			return "stats_view";
		}
		if(diff <= DateTimeConstants.MILLIS_PER_DAY){
			return "stats_view_hourly";
		}
		return "stats_view_daily";
	}	
		
	static class Options {
		String title;
		String orderBy = "viewers";
		String order = "desc";
		TimeUnit timeunit = TimeUnit.hourly;
		int size = 10;
		Options(String options){
			if(options != null){
				String[] os = options.split(",");
				for (String st : os){
					if(st.equalsIgnoreCase("title")){
						title = "title";
					}else if(st.equalsIgnoreCase("duration")){
						orderBy = "duration";
					}else if(st.equalsIgnoreCase("low")){
						order = "asc";
					}else if(st.matches("[1-9][0-9]*")){
						size = Integer.valueOf(st);
					}else if(st.equalsIgnoreCase(TimeUnit.weekly.name()) || 
							st.equalsIgnoreCase(TimeUnit.monthly.name()) || 
							st.equalsIgnoreCase(TimeUnit.daily.name())){
						timeunit = TimeUnit.valueOf(st);
					}
				}
			}
		}
		public Options setTitle(String title) {
			this.title = title;
			return this;
		}
		public Options setOrderBy(String orderBy) {
			this.orderBy = orderBy;
			return this;
		}
		public Options setOrder(String order) {
			this.order = order;
			return this;
		}
		public Options setTimeunit(TimeUnit timeunit) {
			this.timeunit = timeunit;
			return this;
		}
		public Options setSize(int size) {
			this.size = size;
			return this;
		}
	}
	
	enum TimeUnit{
		hourly, daily, weekly, monthly;
	}
}
