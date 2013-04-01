package com.nordija.statistic.mnm.agent;

import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;

import com.nordija.statistic.mnm.rest.model.ListOfMap;
import com.nordija.statistic.mnm.rest.model.ListResult;
import com.nordija.statistic.mnm.rest.model.MapResult;
import com.nordija.statistic.mnm.rest.model.NestedList;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.api.json.JSONJAXBContext;

//@Provider
public class MyContextResolver implements ContextResolver<JAXBContext> {

	private final JAXBContext context;

	public MyContextResolver() throws Exception {
		this.context = new JSONJAXBContext(JSONConfiguration.badgerFish().build(),
				NestedList.class, ListResult.class, MapResult.class, ListOfMap.class);
	}

	public JAXBContext getContext(Class<?> objectType) {
		return context;
	}

}
