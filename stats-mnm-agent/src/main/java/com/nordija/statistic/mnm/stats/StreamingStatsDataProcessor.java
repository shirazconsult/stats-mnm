package com.nordija.statistic.mnm.stats;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;

@Service("streamingStatsDataProcessor")
public class StreamingStatsDataProcessor implements StatsDataProcessor {
	private static Logger logger = LoggerFactory.getLogger(StreamingStatsDataProcessor.class);

	// cacheView value is a list of cusRef, name, type, firstDeliveredTS, lastDeliveredTS, duration, devModel and eventually extra-fields like title".
	static ConcurrentMap<String, ConcurrentMap<StatsViewKey, NavigableSet<List<Object>>>> viewsMap;
	
	@Autowired @Qualifier("streamingStatsLoaderDataSource")
	private DataSource streamingStatsLoaderDataSource;
	@Autowired @Qualifier("dataSource")
	private DataSource dataSource;
	
	@Autowired StatsRowListMapper rowMapper;
	@Autowired private ObjectMapper jacksonMapper;
	
	private AtomicBoolean closing = new AtomicBoolean(false);
	private AtomicLong totalRecords = new AtomicLong();
	private AtomicLong lastId = new AtomicLong();
	private long startFromId;
	private int timeslotSecs = 300;
	private JdbcTemplate jdbcViewPersister;
	private long lastPersistedTS;
	
	private void loadCache() throws Exception{
		StreamingResultSetJdbcTemplate jdbcTemplate = new StreamingResultSetJdbcTemplate(streamingStatsLoaderDataSource); 
		
		// Guard against too many selects in time of aggregator-inactivity periods
		long lastRestart = 0;
		long delayBeforeCursorRestart = 2000;
		long maxDelay = 10000;
		int numOfDelays = 0;
		while(!closing.get()){
			if(lastRestart >= System.currentTimeMillis() - delayBeforeCursorRestart){
				long nextDelay = Math.min(numOfDelays*delayBeforeCursorRestart, maxDelay);
				logger.info("Sleeping for {} millisecs. before restarting cursor.", nextDelay);
				Thread.sleep(nextDelay);
				numOfDelays++;
			}else{
				numOfDelays = 1;
			}
			lastRestart = System.currentTimeMillis();
			
			// Issue new sql and cursor
			String sql = getSql();
			if(logger.isDebugEnabled()){
				logger.debug("Restarting cursor with: {}. Total records read: {}.", sql, totalRecords.get());
			}
			streamDBRows(jdbcTemplate, sql);
					
		    if(closing.get()){
		    	break;
		    }
		}
	}

	private int cursorWindow = 10000;  // 10 sec. page size
	private void streamDBRows(JdbcTemplate template, String sql){
		template.query(sql, new RowCallbackHandler() {		
			long count = 0;
			
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				if(closing.get()){
					throw new RuntimeException("Interrupting Streaming Resultset Reader. Application is closing.");
				}
				List<Object> row = new ArrayList<Object>();
				long deliveredTS = rs.getLong("deliveredTS");
				lastId.set(rs.getLong("id"));
				
				row.add(lastId.get());
				row.add(rs.getString("cusRef"));
				row.add(rs.getString("devRef"));
				row.add(rs.getString("devType"));
				row.add(rs.getString("devModel"));
				row.add(rs.getString("timeZone"));
				row.add(rs.getString("cusGrRefs"));
				row.add(rs.getString("ref"));
				row.add(rs.getString("type"));
				row.add(rs.getString("name"));
				row.add(rs.getLong("time"));
				row.add(rs.getLong("duration"));
				row.add(rs.getString("extra"));
				row.add(deliveredTS);
				row.add(rs.getDate("insertedTS"));
				
				totalRecords.set(totalRecords.get()+1);
				
				count++;
				
				// update various views
				updateViews(row);	
				
				// Persist views 
				if(count % 5000 == 0 || System.currentTimeMillis() - lastPersistedTS >= timeslotMillis){
					saveViewData();
					lastPersistedTS = System.currentTimeMillis();
				}
				
			    // Make sure that we are always 10 sec. behind (earliest rows fetched should be those coming into DB for 10 sec. ago).
				long gap = System.currentTimeMillis() - cursorWindow;
			    if(deliveredTS >= gap && !closing.get()){
			    	try {
			    		if(logger.isDebugEnabled()){
			    			logger.debug("Cursor is within the last {} milliseconds. Taking a short nap. Collected {} recs.", cursorWindow, count);
			    		}
						Thread.sleep(cursorWindow);
					} catch (InterruptedException e) {
						logger.warn("Interrupted while delaying before fetching next rows.", e.getMessage());
					}
			    }
			}

		});		
	}

	private void saveViewData() {
		List<Object[]> batch = new ArrayList<Object[]>();
		List<StatsViewKey> persisted = new ArrayList<StatsViewKey>();
		for (Entry<String, ConcurrentMap<StatsViewKey, NavigableSet<List<Object>>>> viewMap : viewsMap.entrySet()) {
			ConcurrentMap<StatsViewKey, NavigableSet<List<Object>>> view = viewMap.getValue();
			for (Entry<StatsViewKey, NavigableSet<List<Object>>> entry : view.entrySet()) {
				NavigableSet<List<Object>> recs = entry.getValue();
				for (List<Object> rec : recs) {					
					if(isAggregationCompleted(rec)){
						persisted.add(entry.getKey());
						batch.add(rec.subList(0, viewCompletedIdx).toArray());
					}
				}
			}
		}
		if(!batch.isEmpty()){
			int[] ret = jdbcViewPersister.batchUpdate(
					"insert into stats_view (type, name, title, sum, minDuration, maxDuration, totalDuration, fromTS, toTS) " +
					"values (?, ?, ?, ?, ?, ?, ?, ?, ?)"
					, batch);
			if(ret.length < batch.size()){
				logger.warn("Only {} out of {} number of records could be inserted into stats_view table.", ret, batch.size());
			}
			if(logger.isDebugEnabled()){
				logger.debug("Persisted {} view records in the stats_view table.", ret.length);
			}
		}	
		// Empty all the views for those entries which are saved into db.
		for (Entry<String, ConcurrentMap<StatsViewKey, NavigableSet<List<Object>>>> viewMap : viewsMap.entrySet()) {
			ConcurrentMap<StatsViewKey, NavigableSet<List<Object>>> view = viewMap.getValue();
			for (StatsViewKey svk : persisted) {
				NavigableSet<List<Object>> recs = view.get(svk);
				if(!CollectionUtils.isEmpty(recs)){
					// Only the first entry in the record set may not be persisted/completed				
					List<Object> first = recs.first();
					if(isAggregationCompleted(first)){
						recs.clear();
						view.remove(svk);
					}else{
						NavigableSet<List<Object>> tail = recs.tailSet(first, false);
						if(!CollectionUtils.isEmpty(tail)){
							tail.clear();
						}
					}
				}
			}
		}
		persisted.clear();
		batch.clear();
	}
	
	@SuppressWarnings("unchecked")
	private void updateViews(List<Object> row){
		String type = (String)row.get(8);
		ConcurrentMap<StatsViewKey, NavigableSet<List<Object>>> cacheView = viewsMap.get(type);

		if(cacheView == null){
			logger.warn("No view found for event of type = {}.", type);
			return;
		}
		
		String jsonExtra = (String)row.get(12);
		HashMap<String, Object> extra = null;
		if(!StringUtils.isEmpty(jsonExtra)){
			try {
				extra = jacksonMapper.readValue(jsonExtra, HashMap.class);
			} catch (Exception e1) {
				logger.warn("Unable to unmarshal the extra part of record.");
			}
		}
		String title = extra != null ? (String)extra.get("title") : "No title";
		title = title != null && title.length() > 64 ? title.substring(0, 64) : (title == null ? "No title" : title);
		long duration = row.get(durationIdx) == null ? 0L : (Long)row.get(durationIdx);
		
		StatsViewKey viewKey = new StatsViewKey((String)row.get(typeIdx), (String)row.get(nameIdx), title);
		List<Object> rec = null;
		NavigableSet<List<Object>> recs = cacheView.get(viewKey);
		if(CollectionUtils.isEmpty(recs)){
			TreeSet<List<Object>> records = new TreeSet<List<Object>>(new Comparator<List<Object>>() {
				@Override
				public int compare(List<Object> rec1, List<Object> rec2) {
					Long first = (Long)rec1.get(viewToTSIdx) - (Long)rec1.get(viewFromTSIdx);
					Long second = (Long)rec2.get(viewToTSIdx) - (Long)rec2.get(viewFromTSIdx);
					return first.compareTo(second);
				}
			});
			cacheView.put(viewKey, records);
		}else{
			rec = recs.first();
		}
		
		// We have to make sure that no records exist in cache, whose viewToTSIdx is larger than timeslotSecs (5 minutes)
		// because we cannot rely on persisting every 5 minutes.
		if(isRowTooFarAhead(row, rec)){
			rec.set(viewCompletedIdx, new Boolean(true));
		}
		if(rec == null || isAggregationCompleted(rec)){
			rec = new ArrayList<Object>();
			rec.add(viewKey.type);
			rec.add(viewKey.name);
			rec.add(title);
			rec.add(1L);	// sum
			rec.add(duration);	// minDuration
			rec.add(duration);	// maxDuration
			rec.add(duration);	// duration
			rec.add(row.get(deliveredTSIdx));		// from
			rec.add(row.get(deliveredTSIdx));		// to
			rec.add(new Boolean(false));
			cacheView.get(viewKey).add(rec);
		}else{
			rec.set(viewSumIdx, ((Long)rec.get(viewSumIdx))+1);
			rec.set(viewMinDurationIdx, Math.min((Long)rec.get(viewMinDurationIdx), duration));
			rec.set(viewMaxDurationIdx, Math.max((Long)rec.get(viewMaxDurationIdx), duration));
			rec.set(viewTotalDurationIdx, ((Long)rec.get(viewTotalDurationIdx))+duration);	// add up the duration
			rec.set(viewToTSIdx, row.get(deliveredTSIdx));	// to
		}
	}
		
	private final long timeslotMillis = timeslotSecs*1000;
	private boolean isAggregationCompleted(List<Object> rec){
		return (Boolean)rec.get(viewCompletedIdx) || (Long)rec.get(viewToTSIdx) - (Long)rec.get(viewFromTSIdx) >= timeslotMillis;
	}
	private final long tooFarAheadMillis = timeslotMillis+(timeslotMillis/5);
	private boolean isRowTooFarAhead(List<Object> row, List<Object> rec){		
		return row != null && rec != null && (Long)row.get(deliveredTSIdx) - (Long)rec.get(viewToTSIdx) >= tooFarAheadMillis;
	}

	private String getSql(){
		if(totalRecords.get() == 0){
			return "select * from statistic where id >= "+startFromId;
		}else{
			return "select * from statistic where id > "+lastId.get();
		}
	}
		
	Thread dataStreamerThread;
	
	@Override
	public void start() {
		dataStreamerThread = new Thread(new Runnable() {			
			@Override
			public void run() {
				viewsMap = new ConcurrentHashMap<String, ConcurrentMap<StatsViewKey, NavigableSet<List<Object>>>>();
				for (String event : events) {					
					viewsMap.put(event, new ConcurrentHashMap<StatsViewKey, NavigableSet<List<Object>>>());
				}
				
				jdbcViewPersister = new JdbcTemplate(dataSource);

				try {
					loadCache();
				} catch (Exception e) {
					throw new RuntimeException("Failed to load and cache data.", e);
				}
			}
		});
		
		dataStreamerThread.start();
	}

	@Override
	public void stop() {
		closing.set(true);
		try {
			dataStreamerThread.join();
		} catch (InterruptedException e) {
			// ignore.
		}
	}

	@Override
	public boolean isRunning() {
		return !dataStreamerThread.isAlive();
	}

	class StreamingResultSetJdbcTemplate extends JdbcTemplate {
	    public StreamingResultSetJdbcTemplate(final DataSource dataSource) {
	        super(dataSource);
	    }
	    public StreamingResultSetJdbcTemplate(final DataSource dataSource, final boolean lazyInit) {
	        super(dataSource, lazyInit);
	    }

	    @Override
	    protected void applyStatementSettings(final Statement stmt) throws SQLException {
	        stmt.setFetchSize(Integer.MIN_VALUE);
        	stmt.setMaxRows(0);
	        DataSourceUtils.applyTimeout(stmt, getDataSource(), getQueryTimeout());
	    }
	}

	public void setStartFromId(long startFromId) {
		this.startFromId = startFromId;
	}

	public void setTimeslotSecs(int timeslotSecs) {
		this.timeslotSecs = Math.min(this.timeslotSecs, timeslotSecs);
	}
	
}
