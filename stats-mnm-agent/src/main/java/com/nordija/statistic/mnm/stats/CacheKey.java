package com.nordija.statistic.mnm.stats;

public class CacheKey implements Comparable<CacheKey> {
	Long id;
	Long deliveredTS;
	CacheViewKey cacheViewKey;
	
	public CacheKey() {
		super();
	}

	public CacheKey(Long id, Long deliveredTS) {
		super();
		this.id = id;
		this.deliveredTS = deliveredTS;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (deliveredTS ^ (deliveredTS >>> 32));
		result = prime * result + (int) (id ^ (id >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CacheKey other = (CacheKey) obj;
		if (id != other.id)
			return false;
		if (deliveredTS != other.deliveredTS)
			return false;
		return true;
	}

	@Override
	public int compareTo(CacheKey o) {
		int res = deliveredTS.compareTo(o.deliveredTS);
		return (res == 0 ? id.compareTo(o.id) : res);
	}

	@Override
	public String toString() {
		return "CacheKey [id=" + id + ", deliveredTS=" + deliveredTS + "]";
	}
}