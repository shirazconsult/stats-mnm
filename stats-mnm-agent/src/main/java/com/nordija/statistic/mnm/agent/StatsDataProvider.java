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
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.nordija.statistic.mnm.rest.model.ListResult;
import com.nordija.statistic.mnm.rest.model.NestedList;
import com.nordija.statistic.mnm.stats.StatsDataCacheLoader;
import com.nordija.statistic.mnm.stats.StatsDataProcessor;

@Service("statsDataProvider")
@Path("/stats")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
public class StatsDataProvider {
	private static final Logger logger = LoggerFactory.getLogger(StatsDataProvider.class);
	
//	@Autowired @Qualifier("streamingStatsDataCacheLoader") private StatsDataLoader statsDataLoader;
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
	@Path("/nextpage/views/{from}/{to}")
	public NestedList<Object> getViewPage(@PathParam("from") long from, @PathParam("to") long to) {
		logger.debug("Returning statistics view rows from {} to {}.", from, to);
		try {
			return fetch(from, to);
		} catch (Exception e) {
			logger.error("Failed to retrieve records for date interval {} - {}. Reason {}", new Object[]{from, to, e.getMessage()});
			throw new WebApplicationException(e, 500);
		}		
	}

	private NestedList<Object> fetch(long from, long to){
		NestedList<Object> res = new NestedList<Object>(); 
		List<Map<String, Object>> resultList = getJdbcTemplate().queryForList(
				"select * from stats_view where toTS > ? and toTS <= ? order by fromTS, toTS", from, to);
		for (Map<String, Object> row : resultList) {
			ListResult<Object> rec = new ListResult<Object>();
			for (String col : StatsDataProcessor.viewColumns) {					
				rec.addElement(row.get(col));
			}
			res.addRow(rec);
		}
		return res;
	}
	
//	@GET
//	@Path("/columns")
//	public ListResult<String> getColumnNames(){
//		logger.debug("Returning column metadata.");
//		try{
//			return new ListResult<String>(Arrays.asList(StatsDataCacheLoader.columns));
//		}catch(Exception e){ 
//			logger.error("Failed to retrieve column metadata for statistics.");
//			throw new WebApplicationException(e, 500);
//		}
//	}
//
//	@GET
//	@Path("/nextpage")
//	public NestedList<Object> getPage(@QueryParam("numOfPages") int numOfPages) {
//		logger.debug("Returning {} page(s) of statistics.", numOfPages);
//		try {
//			Collection<List<Object>> pages = statsDataLoader.getNextPages(numOfPages);
//			ArrayList<List<Object>> pagesAsList = new ArrayList<List<Object>>(pages);
//			return new NestedList<Object>(pagesAsList);
//		} catch (Exception e) {
//			logger.error("Failed to retrieve the next {} record pages. {}", numOfPages, e.getMessage());
//			throw new WebApplicationException(e, 500);
//		}		
//	}

	public JdbcTemplate getJdbcTemplate() {
		if(jdbcTemplate == null){
			jdbcTemplate = new JdbcTemplate(dataSource);
		}
		return jdbcTemplate;
	}
	
}
