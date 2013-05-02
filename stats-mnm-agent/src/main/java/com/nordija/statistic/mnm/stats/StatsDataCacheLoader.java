package com.nordija.statistic.mnm.stats;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;


@Service("statsDataCacheLoader")
public class StatsDataCacheLoader implements StatsDataLoader {
	private static Logger logger = LoggerFactory.getLogger(StatsDataCacheLoader.class);
	
	private int pageSizeInMillis = 10000;  // 10 sec. page size
	private int maxCacheSizeInMillis = 60000; // 60 sec. cache size
	private int maxCacheSize = 1000000;
	private int shrinkToMillis = maxCacheSizeInMillis;
	
	static ConcurrentNavigableMap<CacheKey, List<Object>> cache;

	@Autowired @Qualifier("dataSource")
	private DataSource dataSource;
	@Autowired StatsRowListMapper rowMapper;
	
	private AtomicBoolean closing = new AtomicBoolean(false);

	@Override
	public void loadCache() throws Exception{
		MyJdbcCursorItemReader itemReader = new MyJdbcCursorItemReader(false);
		ExecutionContext exCtx = new ExecutionContext();
		itemReader.setRowMapper(rowMapper);
		itemReader.setDataSource(dataSource);
		itemReader.setSql(getSql());
		itemReader.close();
		itemReader.open(exCtx);
		if(logger.isDebugEnabled()){
			logger.debug("Selecting: {}", itemReader.getSql());
		}
		int count = 0;
		List<Object> statsRow = null;
		while(!closing.get()){
			try {
				statsRow = itemReader.read();
				if(statsRow != null){
					count++;
					cache.put(new CacheKey((Long)statsRow.get(0), (Long)statsRow.get(deliveredTSIdx)), statsRow);
				    // Make sure that we are always 10 sec. behind (earliest rows fetched should be those coming into DB for 10 sec. ago). 
				    if((Long)statsRow.get(deliveredTSIdx) >= (System.currentTimeMillis() - (pageSizeInMillis))){
				    	try {
				    		if(logger.isDebugEnabled()){
				    			logger.debug("Cursor is within the last {} milliseconds. Taking a short nap. Collected {} recs.", pageSizeInMillis, count);
				    		}
							Thread.sleep(pageSizeInMillis);
						} catch (InterruptedException e) {
							logger.warn("Interrupted while delaying before fetching next rows.", e.getMessage());
						}
				    }
				}else{
					itemReader.close();
			    	try {	
			    		if(logger.isDebugEnabled()){
			    			logger.debug("No rows were fetched from datbase. Sleeping for {} milliseconds. Collected {} recs.", pageSizeInMillis, count);
			    		}
						Thread.sleep(pageSizeInMillis);
					} catch (InterruptedException e) {
						logger.warn("Interrupted while waiting before fetching next rows.", e.getMessage());
					}
			    	itemReader.setSql(getSql());
			    	itemReader.open(exCtx);
			    	count = 0;
		    		if(logger.isDebugEnabled()){
		    			logger.debug("new select: {}", itemReader.getSql());
		    		}
				}
			} catch (UnexpectedInputException e) {
				logger.error("Unexpected input from JdbcCursorItemReader. Proceeding to the next input row.", e);
			} catch (ParseException e) {
				logger.error("Cannot parse the item read from JdbcCursorItemReader. Proceeding to the next input row.",e);
			} catch (Exception e) {
				logger.error("Exception in reading and/or mapping row.", e);
			}
			
			// we don't need to sync. shrinking cache operation with client-threads that read data, since they
			// operate on two different regions of the cache.
		    if(isTimeToShrinkCache()){
		    	CacheKey shrinkToKey = shrinkToKey();
		    	CacheKey firstKey = cache.firstKey();
		    	if(firstKey.deliveredTS < shrinkToKey.deliveredTS){
		    		logger.info("Reducing cache size. Removing old entries from {} to {}.", firstKey, shrinkToKey);
		    		ConcurrentNavigableMap<CacheKey, List<Object>> subMap = cache.subMap(firstKey, shrinkToKey);
		    		subMap.clear();
		    	}
		    }
		    
		    if(closing.get()){
		    	break;
		    }
		}
		itemReader.close();
	}

	@Override
	public Collection<List<Object>> getNextPage(){
		return getPages(1);
	}

	@Override
	public Collection<List<Object>> getNextPages(int numOfPages) {
		numOfPages = numOfPages > 3 ? 3 : (numOfPages < 1 ? 1 : numOfPages);
		return getPages(numOfPages);
	}

	@Override
	public Collection<List<Object>> getNextPage(long from) {
		return getPages(from, 1);
	}

	@Override
	public Collection<List<Object>> getNextPages(long from, int numOfPages) {
		numOfPages = numOfPages > 3 ? 3 : (numOfPages < 1 ? 1 : numOfPages);
		return getPages(from, numOfPages);
	}

	private Collection<List<Object>> getPages(int numOfPages){
		if(!cache.isEmpty()){
			long lastFetched = cache.lastKey().deliveredTS;
			long to = lastFetched - (pageSizeInMillis*2);
			long from = to - (numOfPages*pageSizeInMillis);
		
			try{
				return cache.subMap(new CacheKey(0L, from), new CacheKey(0L, to)).values();
			}catch(IllegalArgumentException iae){
				logger.warn("Could not serve pages from {} to {}.", from, to); 
			}
		}
		return new ArrayList<List<Object>>();
	}

	private Collection<List<Object>> getPages(long from, int numOfPages) {
		if(from > System.currentTimeMillis() - maxCacheSizeInMillis &&
				from <= cache.firstKey().deliveredTS){
			long to = from + (pageSizeInMillis * numOfPages);

			try{
				return cache.subMap(new CacheKey(0L, from), new CacheKey(0L, to)).values();
			}catch(IllegalArgumentException iae){
				logger.warn("Could not serve pages from {} to {}.", from, to); 
			}
		}
		return new ArrayList<List<Object>>();
	}
	
	private String getSql(){
		if(cache.isEmpty()){
//			JdbcTemplate template = new JdbcTemplate(dbPersisterDataSource);
//			long mostRecent = template.queryForLong("select max(deliveredTS) from statistic");
//			// cache-cursor must alwasy be within 20-10 seconds range of the statistic's table.
//			return "select * from statistic where deliveredTS > "+(mostRecent - (pageSizeInMillis*2));
			return "select * from statistic";
		}else{
			// Start from where we ended last
			CacheKey lastKey = cache.lastKey();
			return "select * from statistic where deliveredTS >= "+lastKey.deliveredTS+" and id > "+lastKey.id;
		}
	}

	// shrinkToKey is the cache-key for the cache entry, which the cache size must be reduced to.
	private CacheKey shrinkToKey(){
		return new CacheKey(0L, System.currentTimeMillis()-shrinkToMillis);
	}
	// Reduce cache size when first entry in the cache is more than maxCacheSizeInMillis+pageSizeInMillis old
	// ADN the cache size has reached the maxCacheSize limit.
	private boolean isTimeToShrinkCache(){
		// Since the cursor is always 10 sec. behind, we need to subtract pageSizeInMillis also.
		long lowerBound = System.currentTimeMillis() - maxCacheSizeInMillis - pageSizeInMillis;
		return !cache.isEmpty() && cache.firstKey().deliveredTS < lowerBound && cache.size() >= maxCacheSize;
	}
	
	Thread cacheLoaderThread;
	
	@Override
	public void start() {
		cacheLoaderThread = new Thread(new Runnable() {			
			@Override
			public void run() {
				cache = new ConcurrentSkipListMap<CacheKey, List<Object>>();
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
	
	class MyJdbcCursorItemReader extends JdbcCursorItemReader<List<Object>>{
		boolean streaming;
		public MyJdbcCursorItemReader(boolean streaming) {
			super();
			this.streaming = streaming;
			setVerifyCursorPosition(!streaming);
		}

		@Override
		protected void applyStatementSettings(PreparedStatement stmt)
				throws SQLException {
			super.applyStatementSettings(stmt);
			if(streaming){
				stmt.setFetchSize(Integer.MIN_VALUE);
			}
		}
	}

	@Override
	public Collection<List<Object>> getNextViewPages(int numOfPages) {
		throw new RuntimeException("Not implemented.");
	}

	@Override
	public Collection<List<Object>> getNextViewPage(long from) {
		throw new RuntimeException("Not implemented.");
	}
}
