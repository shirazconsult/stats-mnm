package com.nordija.statistic.mnm.stats;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

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



@Service("streamingStatsDataCacheLoader")
public class StreamingStatsDataCacheLoader implements StatsDataLoader {
	private static Logger logger = LoggerFactory.getLogger(StreamingStatsDataCacheLoader.class);
	
	private int pageSizeInMillis = 10000;  // 10 sec. page size
	// Since we serve up to three pages back in time from the cache and reads are always one page behind the cache-head
	// then the maxCacheSizeInMillis should preferably be at least four times the pageSizeInMillis.
	private int maxCacheSizeInMillis = 60000; // 60 sec. cache size
	// maxCacheSize dictate when to reduce the cache size. Should be adjusted to the aggregator activity. 
	// How many records come in to the database in maxCacheSizeInMillis?
	private int maxCacheSize = 100000;
	// Reduce the cache for excess records. Remove all the entries lying behind the shrinkToMillis. in time.
	private int shrinkToMillis = maxCacheSizeInMillis - pageSizeInMillis;
	
	static ConcurrentNavigableMap<CacheKey, List<Object>> cache;
	// cacheView value is a list of cusRef, name, type, firstDeliveredTS, lastDeliveredTS, duration, devModel and eventually extra-fields like title".
	static ConcurrentMap<String, ConcurrentMap<CacheViewKey, List<Object>>> viewsMap;
	
	@Autowired @Qualifier("streamingStatsLoaderDataSource")
	private DataSource streamingStatsLoaderDataSource;
	@Autowired StatsRowListMapper rowMapper;
	@Autowired private ObjectMapper jacksonMapper;
	
	private AtomicBoolean closing = new AtomicBoolean(false);

	@Override
	public void loadCache() throws Exception{
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
				logger.debug("Restarting cursor with: {}. cache size is {}", sql, cache.size());
			}
			streamDBRowsIntoCache(jdbcTemplate, sql);
					
			// Shrink the cache if necessary
			shrinkCacheIfNecessary();
			
		    if(closing.get()){
		    	break;
		    }
		}
	}

	private void streamDBRowsIntoCache(JdbcTemplate template, String sql){
		template.query(sql, new RowCallbackHandler() {		
			long count = 0;
			
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				if(closing.get()){
					throw new RuntimeException("Interrupting Streaming Resultset Reader. Application is closing.");
				}
				List<Object> row = new ArrayList<Object>();
				row.add(rs.getLong("id"));
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
				row.add(rs.getLong("deliveredTS"));
				row.add(rs.getDate("insertedTS"));
				
				count++;
				CacheKey key = new CacheKey((Long)row.get(0), (Long)row.get(deliveredTSIdx));
				cache.put(key, row);
				
				// update various views
				updateCacheViews(key, row);
				
			    // Make sure that we are always 10 sec. behind (earliest rows fetched should be those coming into DB for 10 sec. ago).
				long gap = System.currentTimeMillis() - pageSizeInMillis;
			    if(key.deliveredTS >= gap){
			    	try {
			    		if(logger.isDebugEnabled()){
			    			logger.debug("Cursor is within the last {} milliseconds. Taking a short nap. Collected {} recs.", pageSizeInMillis, count);
			    		}
			    		shrinkCacheIfNecessary();
						Thread.sleep(pageSizeInMillis);
					} catch (InterruptedException e) {
						logger.warn("Interrupted while delaying before fetching next rows.", e.getMessage());
					}
			    }
			}
		});		
	}
	
	private void updateCacheViews(CacheKey cacheKey, List<Object> row){
		// List of cusRef, name, type, firstDeliveredTS, lastDeliveredTS, duration, devModel and eventually extra-fields like title".
		CacheViewKey cacheViewKey = null;
		String type = (String)row.get(8);

		cacheViewKey = updateCacheView(viewsMap.get(type), row);
		if(cacheViewKey != null){
			if(cacheViewKey.cacheKey != null){
				// The previous cacheKey entry refered by this cacheViewkey is obsolete and shouldn't refer to any view entry.
				cacheViewKey.cacheKey.cacheViewKey = null;
			}
			cacheViewKey.cacheKey = cacheKey;
			cacheKey.cacheViewKey = cacheViewKey;
		}
	}

	@SuppressWarnings("unchecked")
	private CacheViewKey updateCacheView(ConcurrentMap<CacheViewKey, List<Object>> cacheView, List<Object> row){
		if(cacheView == null){
			return null;
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

		String type = (String)row.get(8);
		long duration = row.get(11) == null ? 0L : (Long)row.get(11);
		CacheViewKey viewKey = new CacheViewKey((String)row.get(1), (String)row.get(9), type);
		List<Object> rec = cacheView.get(viewKey);
		if(rec == null){
			rec = new ArrayList<Object>();
			rec.add(viewKey.cusRef);
			rec.add(viewKey.name);		
			rec.add(type);
			rec.add(row.get(deliveredTSIdx));	// firstDeliveredTS
			rec.add(row.get(deliveredTSIdx));	// lastDeliveredTS
			rec.add(duration);	// duration
			rec.add(row.get(4));		// devModel
			if(extra != null){
				rec.add(extra.get("title"));	// extra.title
			}
			cacheView.put(viewKey, rec);
		}else{
			rec.set(5, (Long)rec.get(5)+duration);	// add up the duration
			rec.set(4, row.get(deliveredTSIdx));	// lastDeliveredTS
		}
		return viewKey;
	}
	
	@Override
	public Collection<List<Object>> getNextViewPages(int numOfPages) {
		List<List<Object>> result = new ArrayList<List<Object>>();

		ConcurrentNavigableMap<CacheKey, List<Object>> cacheSegment = getCacheSegment(numOfPages);
		if(cacheSegment != null){
			for (CacheKey ck : cacheSegment.keySet()) {
				if(ck.cacheViewKey != null){
					ConcurrentMap<CacheViewKey, List<Object>> cacheView = viewsMap.get(ck.cacheViewKey.type);
					result.add(cacheView.get(ck.cacheViewKey));
				}
			}
		}
		return result;
	}

	@Override
	public Collection<List<Object>> getNextViewPage(long from) {
		List<List<Object>> result = new ArrayList<List<Object>>();

		ConcurrentNavigableMap<CacheKey, List<Object>> cacheSegment = getCacheSegment(from, 1);
		for (CacheKey ck : cacheSegment.keySet()) {
			if(ck.cacheViewKey != null){
				ConcurrentMap<CacheViewKey, List<Object>> cacheView = viewsMap.get(ck.cacheViewKey.type);
				result.add(cacheView.get(ck.cacheViewKey));
			}
		}
		return result;
	}

	@Override
	public Collection<List<Object>> getNextPage(){
		return getNextPages(1);
	}

	@Override
	public Collection<List<Object>> getNextPages(int numOfPages) {
		numOfPages = numOfPages > 3 ? 3 : (numOfPages < 1 ? 1 : numOfPages);
		ConcurrentNavigableMap<CacheKey, List<Object>> cachePortion = getCacheSegment(numOfPages);
		if(cachePortion != null){
			return cachePortion.values();
		}
		return new ArrayList<List<Object>>();
	}

	@Override
	public Collection<List<Object>> getNextPage(long from) {
		return getNextPages(from, 1);
	}

	@Override
	public Collection<List<Object>> getNextPages(long from, int numOfPages) {
		numOfPages = numOfPages > 3 ? 3 : (numOfPages < 1 ? 1 : numOfPages);
		ConcurrentNavigableMap<CacheKey, List<Object>> cachePortion = getCacheSegment(from, numOfPages);
		if(cachePortion != null){
			return cachePortion.values();
		}
		return new ArrayList<List<Object>>();
	}

	private ConcurrentNavigableMap<CacheKey, List<Object>> getCacheSegment(int numOfPages){
		if(!cache.isEmpty()){
			long lastFetched = cache.lastKey().deliveredTS;
			// The last page in cache should not be accessed, since it may contend with insert operations.
			// Therefore reads should always be one page behind from the cache-head.
			long to = lastFetched - (pageSizeInMillis);
			long from = to - (numOfPages*pageSizeInMillis);
		
			try{
				return cache.subMap(new CacheKey(0L, from), new CacheKey(0L, to));
			}catch(IllegalArgumentException iae){
				logger.warn("Could not serve pages from {} to {}.", from, to); 
			}
		}
		return null;
	}

	private ConcurrentNavigableMap<CacheKey, List<Object>> getCacheSegment(long from, int numOfPages){
		if(from > System.currentTimeMillis() - maxCacheSizeInMillis &&
				from <= cache.firstKey().deliveredTS){
			long to = from + (pageSizeInMillis * numOfPages);

			try{
				return cache.subMap(new CacheKey(0L, from), new CacheKey(0L, to));
			}catch(IllegalArgumentException iae){
				logger.warn("Could not serve pages from {} to {}.", from, to); 
			}
		}
		return null;
	}
	
	private String getSql(){
		if(cache.isEmpty()){
			return "select * from statistic where deliveredTS > "+(System.currentTimeMillis() - maxCacheSizeInMillis);
		}else{
			// Start from where we ended last
			CacheKey lastKey = cache.lastKey();
			return "select * from statistic where deliveredTS >= "+lastKey.deliveredTS+" and id > "+lastKey.id;
		}
	}

	private void shrinkCacheIfNecessary(){
		// we don't need to synchronize the cache-shrinking operation with client-threads that read from cache since they
		// operate on two different regions of the cache.
		if(isTimeToShrinkCache()){
			CacheKey shrinkToKey = new CacheKey(0L, System.currentTimeMillis()-shrinkToMillis);
			CacheKey firstKey = cache.firstKey();
			if(firstKey.deliveredTS < shrinkToKey.deliveredTS){
				int size = cache.size();
				ConcurrentMap<CacheKey, List<Object>> subMap = cache.subMap(firstKey, shrinkToKey);
				for (CacheKey ck : subMap.keySet()) {
					if(ck.cacheViewKey != null){
						ConcurrentMap<CacheViewKey, List<Object>> cacheView = viewsMap.get(ck.cacheViewKey.type);
						if(cacheView != null){
							cacheView.remove(ck.cacheViewKey);
						}
					}
					subMap.remove(ck);
				}
				if(logger.isDebugEnabled()){
					logger.debug("Reducing cache size from {} to {}.", size, cache.size());
				}
			}
		}		
	}
		
	// Reduce cache size when first entry in the cache is more than maxCacheSizeInMillis+pageSizeInMillis old
	// ADN the cache size has reached the maxCacheSize limit.
	private boolean isTimeToShrinkCache(){
		// Since the cursor is always 10 sec. behind, we need to subtract pageSizeInMillis also.
		long lowerBound = System.currentTimeMillis() - maxCacheSizeInMillis;
		int size = cache.size();
		return size >= maxCacheSize || (size > pageSizeInMillis && cache.firstKey().deliveredTS < lowerBound);
	}
	
	Thread cacheLoaderThread;
	
	@Override
	public void start() {
		cacheLoaderThread = new Thread(new Runnable() {			
			@Override
			public void run() {
				cache = new ConcurrentSkipListMap<CacheKey, List<Object>>();
				
				viewsMap = new ConcurrentHashMap<String, ConcurrentMap<CacheViewKey,List<Object>>>();
				for (String event : events) {					
					viewsMap.put(event, new ConcurrentHashMap<CacheViewKey, List<Object>>());
				}
				
				try {
					loadCache();
				} catch (Exception e) {
					throw new RuntimeException("Failed to load and cache data.", e);
				}
			}
		});
		cacheLoaderThread.start();
	}

	@Override
	public void stop() {
		closing.set(true);
		try {
			cacheLoaderThread.join();
		} catch (InterruptedException e) {
			// ignore.
		}
		cache.clear();
	}

	@Override
	public boolean isRunning() {
		return !cacheLoaderThread.isAlive();
	}

	public int getPageSizeInMillis() {
		return pageSizeInMillis;
	}

	public void setPageSizeInMillis(int pageSizeInMillis) {
		this.pageSizeInMillis = pageSizeInMillis;
	}

	public int getMaxCacheSizeInMillis() {
		return maxCacheSizeInMillis;
	}

	public void setMaxCacheSizeInMillis(int maxCacheSizeInMillis) {
		this.maxCacheSizeInMillis = maxCacheSizeInMillis;
	}	
	
	public void setMaxCacheSize(int maxCacheSize) {
		this.maxCacheSize = maxCacheSize;
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
}
