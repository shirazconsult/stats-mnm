package com.nordija.statistic.mnm.rest.jaxb;

import java.util.Map;


/**
 * Utility class for representing a {@link RestDataSource} request as an object. Example:
 * <request>
 *   <data>
 *       <app>mnm-amq</app>
 *       <user>admin</user>
 *       ...
 *   </data>
 *  <dataSource>isc_DefaultRestDS_0</dataSource>
 *   <operationType>fetch</operationType>
 *  <oldValues></oldValues>
 * </request>
 * 
 * @author farhad
 *
 * @param <T>
 */
public class DSRequest {

	private Map<String, Object> data;

	private String dataSource;	
	private OperationType operationType;
	private int startRow;
	private int endRow;
	private String componentId;
	private String oldValues;
	
	public OperationType getOperationType() {
		return operationType;
	}

	public void setOperationType(OperationType operationType) {
		this.operationType = operationType;
	}

	public int getStartRow() {
		return startRow;
	}

	public void setStartRow(int startRow) {
		this.startRow = startRow;
	}

	public int getEndRow() {
		return endRow;
	}

	public void setEndRow(int endRow) {
		this.endRow = endRow;
	}
	
	public void setComponentId(String componentId) {
		this.componentId = componentId;
	}

	public String getComponentId() {
		return componentId;
	}

	public void setDataSource(String dataSource) {
		this.dataSource = dataSource;
	}

	public String getDataSource() {
		return dataSource;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public void setData(Map<String, Object> data) {
		this.data = data;
	}
}
