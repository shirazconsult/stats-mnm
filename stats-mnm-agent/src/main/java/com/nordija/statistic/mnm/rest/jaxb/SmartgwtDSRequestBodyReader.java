package com.nordija.statistic.mnm.rest.jaxb;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Provider
@Consumes("text/xml")
@Component("smartgwtDSRequestBodyReader")
public class SmartgwtDSRequestBodyReader implements MessageBodyReader<DSRequest> {
	private final static Log log = LogFactory.getLog(SmartgwtDSRequestBodyReader.class);
	
	@Context
	private JAXBContext context;
	
	public boolean isReadable(Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		return DSRequest.class.isAssignableFrom(type);
	}

	public DSRequest readFrom(Class<DSRequest> type,
			Type genericType, Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
			throws IOException, WebApplicationException {
		DSRequest res = new DSRequest();
		try {			
	       DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	       DocumentBuilder db = dbf.newDocumentBuilder();
		   Document doc = db.parse(entityStream);
		   Element root = (Element)doc.getElementsByTagName("request").item(0);
		   NodeList nl = root.getChildNodes();
		   Node operationType = root.getElementsByTagName("operationType").item(0);
		   if(operationType != null){
			   res.setOperationType(OperationType.valueOf(operationType.getTextContent().trim().toUpperCase()));
		   }
		   Node startRow = doc.getElementsByTagName("startRow").item(0);
		   if(startRow != null){
			   res.setStartRow(Integer.valueOf(startRow.getTextContent()));
		   }
		   Node endRow = doc.getElementsByTagName("endRow").item(0);
		   if(endRow != null){
			   res.setEndRow(Integer.valueOf(endRow.getTextContent()));
		   }
		   Node data = root.getElementsByTagName("data").item(0);
		   if(data != null){
			   NodeList dataElems = data.getChildNodes();
			   if(dataElems != null){
				   Map<String, Object> dataMap = new HashMap<String, Object>();
				   for(int i=0; i<dataElems.getLength(); i++){
					   Node item = dataElems.item(i);
					   if(item.getNodeType() == Node.ELEMENT_NODE){
						   dataMap.put(item.getNodeName(), item.getTextContent().trim());
					   }
				   }
				   res.setData(dataMap);
			   }
		   }
		   Node oldValues = root.getElementsByTagName("oldValues").item(0);
		   if(oldValues != null){
			   NodeList ovElems = oldValues.getChildNodes();
			   if(ovElems != null){
				   Map<String, Object> ovMap = new HashMap<String, Object>();
				   for(int i=0; i<ovElems.getLength(); i++){
					   Node item = ovElems.item(i);
					   if(item.getNodeType() == Node.ELEMENT_NODE){
						   ovMap.put(item.getNodeName(), item.getTextContent().trim());
					   }
				   }
				   res.setOldValues(ovMap);
			   }
		   }		   
		} catch (Exception e) {
			log.error("Error when parsing the response.", e);
			throw new WebApplicationException(e);
		}
		return res;
	}	
}