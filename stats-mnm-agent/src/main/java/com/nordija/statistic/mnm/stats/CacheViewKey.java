package com.nordija.statistic.mnm.stats;

public class CacheViewKey implements Comparable<CacheViewKey> {
	// only cusRef and name are used for the natural ordering based on hashCode, equals and comparator
	String cusRef;
	String name;
	String type;
	CacheKey cacheKey;
	
	public CacheViewKey() {
		super();
	}

	public CacheViewKey(String cusRef, String name) {
		super();
		this.cusRef = cusRef;
		this.name = name;
	}

	public CacheViewKey(String cusRef, String name, String type) {
		super();
		this.cusRef = cusRef;
		this.name = name;
		this.type = type;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((cusRef == null) ? 0 : cusRef.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		CacheViewKey other = (CacheViewKey) obj;
		if (cusRef == null) {
			if (other.cusRef != null)
				return false;
		} else if (!cusRef.equals(other.cusRef))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "CacheViewKey [cusRef=" + cusRef + ", name=" + name + ", type="
				+ type + "]";
	}

	@Override
	public int compareTo(CacheViewKey o) {
		int res = cusRef.compareTo(o.cusRef);
		return res == 0 ? name.compareTo(o.name) : res;
	}
	
}