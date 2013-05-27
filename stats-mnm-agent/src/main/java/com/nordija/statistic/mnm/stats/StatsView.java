package com.nordija.statistic.mnm.stats;

public class StatsView {
	private Long id;
	private String type, name, title;
	private long viewers, duraion, fromTS, toTS;
	private boolean completed;
	
	public StatsView() {
		super();
	}

	public StatsView(String type, String name, String title) {
		super();
		this.type = type;
		this.name = name;
		this.title = title;
	}


	public Long getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public long getViewers() {
		return viewers;
	}

	public void setViewers(long viewers) {
		this.viewers = viewers;
	}

	public long getDuraion() {
		return duraion;
	}

	public void setDuraion(long duraion) {
		this.duraion = duraion;
	}

	public long getFromTS() {
		return fromTS;
	}

	public void setFromTS(long fromTS) {
		this.fromTS = fromTS;
	}

	public long getToTS() {
		return toTS;
	}

	public void setToTS(long toTS) {
		this.toTS = toTS;
	}

	public boolean isCompleted() {
		return completed;
	}

	public void setCompleted(boolean completed) {
		this.completed = completed;
	}

	public void accumulateViewers(long viewers){
		this.viewers += viewers;
	}
	
	public void accumulateDuration(long duration){
		this.duraion += duration;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
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
		StatsView other = (StatsView) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
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
	public String toString() {
		return "StatsView [id=" + id + ", type=" + type + ", name=" + name
				+ ", title=" + title + ", viewers=" + viewers + ", duraion="
				+ duraion + ", fromTS=" + fromTS + ", toTS=" + toTS
				+ ", completed=" + completed + "]";
	}
}
