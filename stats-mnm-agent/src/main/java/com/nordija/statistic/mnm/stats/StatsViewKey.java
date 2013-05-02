package com.nordija.statistic.mnm.stats;

public class StatsViewKey implements Comparable<StatsViewKey> {
	// only cusRef and name are used for the natural ordering based on hashCode, equals and comparator
	String type;
	String name;
	String title;
	
	public StatsViewKey() {
		super();
	}

	public StatsViewKey(String type, String name) {
		super();
		this.type = type;
		this.name = name;
	}

	public StatsViewKey(String type, String name, String title) {
		this(type, name);
		this.title = title;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((title == null) ? 0 : title.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		StatsViewKey other = (StatsViewKey) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (title == null) {
			if (other.title != null)
				return false;
		} else if (!title.equals(other.title))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	@Override
	public int compareTo(StatsViewKey svk) {
		int res = type.compareTo(svk.type);
		res = (res == 0 ? name.compareTo(svk.name) : res);
		return res == 0 && type != null && svk.type != null ? type.compareTo(svk.title) : res;
	}

	@Override
	public String toString() {
		return "StatsViewKey [type=" + type + ", name=" + name + ", title="
				+ title + "]";
	}
}