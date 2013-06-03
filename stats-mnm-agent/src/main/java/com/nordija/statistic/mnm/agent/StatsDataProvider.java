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

import org.javatuples.Quintet;
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

@Service("statsDataProvider")
@Path("/stats")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
public class StatsDataProvider {
	private static final Logger logger = LoggerFactory.getLogger(StatsDataProvider.class);
	
	@Autowired @Qualifier("dataSource") private DataSource dataSource;
	
	private JdbcTemplate jdbcTemplate;
	
	@GET
	@Path("/viewcolumns")
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
	public NestedList<Object> getViewPage(
			@PathParam("from") long from, 
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
	public NestedList<Object> getViewPage(
			@PathParam("from") String from, 
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
	public NestedList<Object> getViewPage(
			@PathParam("type") String type, 
			@PathParam("from") long from, 
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
	public NestedList<Object> getViewPage(
			@PathParam("type") String type, 
			@PathParam("from") String from, 
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
	public NestedList<Object> getViewPage(
			@PathParam("type") String type, 
			@PathParam("from") long from, 
			@PathParam("to") long to,
			@PathParam("options") String options) {
		logger.debug("Returning statistics view rows from {} to {} for {} with options {}.", new Object[]{from, to, type, options});
		try {
			return fetch(type, from, to, extractOptions(options));
		} catch (Exception e) {
			logger.error("Failed to retrieve records for date interval {} - {}. Reason {}", new Object[]{from, to, e.getMessage()});
			throw new WebApplicationException(e, 500);
		}		
	}

	@GET
	@Path("/view/{type}/{from}/{to}/{options}")
	public NestedList<Object> getViewPage(
			@PathParam("type") String type, 
			@PathParam("from") String from, 
			@PathParam("to") String to,
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
	public ListResult<NestedList<Object>> getViewPageInBatch(
			@PathParam("type") String type, 
			@PathParam("from") String from, 
			@PathParam("to") String to,
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
				Quintet<String, String, String, Integer, String> opts = extractOptions(options);
				logger.debug("Fetching from {} to {}.", nextFrom, nextTo);
				NestedList<Object> fetch = fetch(type, nextFrom.getMillis(), nextTo.getMillis(), opts.setAt4("hourly"));
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
				Quintet<String, String, String, Integer, String> opts = extractOptions(options);
				NestedList<Object> fetch = fetch(type, nextFrom.getMillis(), nextTo.getMillis(), opts.setAt4("daily"));
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
				Quintet<String, String, String, Integer, String> opts = extractOptions(options);
				NestedList<Object> fetch = fetch(type, nextFrom.getMillis(), nextTo.getMillis(), opts.setAt4("weekly"));
				res.addElement(fetch);
			}
			return res;
		}else{
			// divide into months and create up to 12 response lists as there are months
			int diffInMonths = toDate.monthOfYear().get() - fromDate.monthOfYear().get();
			Quintet<String, String, String, Integer, String> opts = extractOptions(options);
			DateTime fromFloor = fromDate.monthOfYear().roundFloorCopy();
			if(diffInMonths == 0){
				DateTime nextTo = fromFloor.plusMonths(1);
				logger.debug("Fetching from {} to {}.", fromFloor, nextTo);
				NestedList<Object> fetch = fetch(type, fromFloor.getMillis(), nextTo.getMillis(), opts.setAt4("monthly"));
				res.addElement(fetch);
			}else{
				for(int i=0; i < diffInMonths; i++){					
					DateTime nextFrom = fromFloor.plusMonths(i);
					DateTime nextTo = nextFrom.plusMonths(1);
					logger.debug("Fetching from {} to {}.", nextFrom, nextTo);
					NestedList<Object> fetch = fetch(type, nextFrom.getMillis(), nextTo.getMillis(), opts.setAt4("monthly"));
					res.addElement(fetch);
				}
			}
			return res;			
		}
		
		return null;
	}
		
	private NestedList<Object> fetch(String type, long from, long to, Quintet<String, String, String, Integer, String> options) {
		NestedList<Object> res = new NestedList<Object>();
		StringBuilder q = new StringBuilder("select `type`, `name`, ").
				append(options.getValue0() != null && options.getValue0().equals("title") ? "`title`" : "'' as `title`").
				append(", sum(`viewers`) as viewers, sum(`duration`) as duration, toTS from ").
				append(getTable(from, to)).
				append(" where type = ? and toTS > ? and toTS <= ?  ").
				append(options.getValue0() != null && options.getValue0().equals("title") ? 
						" group by type, name, title order by " : " group by type, name order by ").
				append(options.getValue1()).append(" ").
				append(options.getValue2()).
				append(" limit 0, ?");
		List<Map<String, Object>> resultList = getJdbcTemplate().queryForList(
				q.toString(),
				type, from, to, options.getValue3());
		for (Map<String, Object> row : resultList) {
			ListResult<Object> rec = new ListResult<Object>();
			for (String col : StatsDataProcessor.topViewColumns) {
				if(col.equals("time")){
					rec.addElement(getFormattedTime(options.getValue4(), (Long)row.get("toTS")));
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
	private String getFormattedTime(String timeunit, long ts){
		DateTime dt = new DateTime(ts);			
		if(timeunit.equalsIgnoreCase("daily")){
			return fmtDay.print(dt);
		}else if(timeunit.equalsIgnoreCase("weekly")){
			return fmtWeek.print(dt);
		}else if(timeunit.equalsIgnoreCase("monthly")){
			return fmtMonth.print(dt);
		}else{
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
	
	private Quintet<String, String, String, Integer, String> extractOptions(String options){
		Quintet<String, String, String, Integer, String> opts = new Quintet<String, String, String, Integer, String>(null, "viewers", "desc", 10, "hourly");
		if(options != null){
			String[] os = options.split(",");
			for (String st : os){
				if(st.equalsIgnoreCase("title")){
					opts = opts.setAt0("title");
				}else if(st.equalsIgnoreCase("duration")){
					opts = opts.setAt1("duration");
				}else if(st.equalsIgnoreCase("low")){
					opts = opts.setAt2("asc");
				}else if(st.matches("[1-9][0-9]*")){
					opts = opts.setAt3(Integer.valueOf(st));
				}else if(st.equalsIgnoreCase("weekly") || st.equalsIgnoreCase("monthly") || st.equalsIgnoreCase("daily")){
					opts = opts.setAt4(st);
				}
			}
		}
		return opts;
	}	
}
