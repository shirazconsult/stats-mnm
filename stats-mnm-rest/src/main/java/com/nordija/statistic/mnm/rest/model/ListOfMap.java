package com.nordija.statistic.mnm.rest.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ListOfMap <T> {
	private List<MapResult<String, T>> elements = new ArrayList<MapResult<String, T>>();

	public ListOfMap() {
		super();
	}

	public ListOfMap(List<Map<String, T>> nestedMap) {
		for (Map<String, T> m : nestedMap) {
			elements.add(new MapResult(m));
		}
	}

	public List<MapResult<String, T>> getElements() {
		return elements;
	}

	public void setElements(List<MapResult<String, T>> elements) {
		this.elements = elements;
	}

	public void addElement(Map<String, T> elem){
		elements.add(new MapResult(elem));
	}

	public void addElement(MapResult<String, T> elem){
		elements.add(elem);
	}

}
