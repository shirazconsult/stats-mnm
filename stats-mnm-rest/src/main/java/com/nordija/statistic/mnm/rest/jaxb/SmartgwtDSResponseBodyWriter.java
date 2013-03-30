package com.nordija.statistic.mnm.rest.jaxb;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.springframework.stereotype.Component;

@Provider
@Produces(MediaType.APPLICATION_XML)
@Component("smartgwtDSResponseBodyWriter")
public class SmartgwtDSResponseBodyWriter implements MessageBodyWriter<DSResponse> {

	public long getSize(DSResponse t, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		return -1;
	}

	public boolean isWriteable(Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		return DSResponse.class.isAssignableFrom(type);
	}

	public void writeTo(DSResponse t, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders,
			OutputStream entityStream) throws IOException,
			WebApplicationException {
		if(t != null){
			StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><response>");
			sb.append(getField("stats", t.getStatus())).
				append(getField("startRow", t.getStartRow())).
				append(getField("endRow", t.getEndRow())).
				append(getField("totalRows", t.getTotalRows()));
			if(t.getData() != null && !t.getData().isEmpty()){
				sb.append("<data>");
				for (Map<String, Object> rec : t.getData()) {						
					sb.append("<record>").append(getRecord(rec)).append("</record>");
				}
				sb.append("</data>");
			}
			sb.append("</response>");
			entityStream.write(sb.toString().getBytes());
			entityStream.flush();
		}
	}
	
	private String getRecord(Map<String, Object> map) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			sb.append(getField(entry.getKey(), entry.getValue()));
		}
		return sb.toString();
	}

	private String getField(String name, Object value){
		if(value == null){
			return "";
		}
		return new StringBuilder("<").append(name).append(">").
				append(value).append("</").append(name).append(">").
				toString();
	}
}