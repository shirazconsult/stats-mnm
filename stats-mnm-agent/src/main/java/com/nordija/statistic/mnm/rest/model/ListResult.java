package com.nordija.statistic.mnm.rest.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ListResult <T> {
	private List<T> result = new ArrayList<T>();

	public ListResult() {
		super();
	}

	public ListResult(List<T> result) {
		super();
		this.result = result;
	}

	public List<T> getResult() {
		return result;
	}

	public void setResult(List<T> result) {
		this.result = result;
	}

	public void addElement(T element){
		this.result.add(element);
	}
	
	@Override
	public String toString() {
		return "ListResult [result=" + result + "]";
	}
	
	
}
