package com.nordija.statistic.mnm.rest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class NestedList <T> {
	List<ListResult<T>> rows = new ArrayList<ListResult<T>>();
	MapResult<String, Object> controlData;

	public NestedList() {
		super();
	}

	public NestedList(List<List<T>> doubleList) {
		for (List<T> row : doubleList) {
			addRow(new ListResult<T>(row));
		}
	}

	public List<ListResult<T>> getRows() {
		return rows;
	}

	public void setRows(List<ListResult<T>> rows) {
		this.rows = rows;
	}
	
	public void addRow(ListResult<T> row){
		rows.add(row);
	}
	
	public MapResult<String, Object> getControlData() {
		return controlData;
	}

	public void setControlData(Map<String, Object> controlData) {
		this.controlData = new MapResult<String, Object>(controlData);
	}
	
}
