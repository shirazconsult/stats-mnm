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

import org.javatuples.Triplet;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
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
	
	private NestedList<Object> fetch(String type, long from, long to, Triplet<String, String, Integer> options) {
		NestedList<Object> res = new NestedList<Object>();
		StringBuilder q = new StringBuilder("select `type`, `name`, `title`, sum(`viewers`) as viewers, sum(`duration`) as duration from ").
				append(getTable(from, to)).
				append(" where type = ? and toTS > ? and toTS <= ?  group by type, name, title order by ").
				append(options.getValue0()).append(" ").
				append(options.getValue1()).
				append(" limit 0, ?");
		List<Map<String, Object>> resultList = getJdbcTemplate().queryForList(
				q.toString(),
				type, from, to, options.getValue2());
		for (Map<String, Object> row : resultList) {
			ListResult<Object> rec = new ListResult<Object>();
			for (String col : StatsDataProcessor.topViewColumns) {					
				rec.addElement(row.get(col));
			}
			res.addRow(rec);
		}
		return res;
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
	
	private Triplet<String, String, Integer> extractOptions(String options){
		Triplet<String, String, Integer> opts = new Triplet<String, String, Integer>("viewers", "desc", 10);
		if(options != null){
			String[] os = options.split(",");
			for (String st : os){
				if(st.equalsIgnoreCase("duration")){
					opts = opts.setAt0("duration");
				}else if(st.equalsIgnoreCase("low")){
					opts = opts.setAt1("asc");
				}else if(st.matches("[1-9][0-9]*")){
					opts = opts.setAt2(Integer.valueOf(st));
				}
			}
		}
		return opts;
	}	
}
