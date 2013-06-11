package com.nordija.statistic.mnm.agent;

import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nordija.statistic.mnm.rest.jaxb.DSRequest;
import com.nordija.statistic.mnm.rest.jaxb.DSResponse;
import com.nordija.statistic.mnm.rest.jaxb.OperationType;
import com.nordija.statistic.monitoring.admin.AggregatorAdmin;

@Service("aggAdminResource")
@Path("/agg/admin")
@Produces( { MediaType.APPLICATION_XML})
@Consumes( { MediaType.TEXT_XML })
public class AggAdminResource {
	private static final Logger logger = LoggerFactory.getLogger(AggAdminResource.class);
	
	@Autowired private AggregatorAdmin aggAdmin;
		
	@POST
	@Path("/info")
	public DSResponse getInfo(DSRequest request){
		logger.info("agg/admin/info called with {} operation.", request.getOperationType().name());

		Map<String, Object> data = request.getData();
		if(data == null || data.get("aggregatorName") == null){
			String err = "No aggregatorName specified in the request";
			logger.error(err);
			throw new WebApplicationException(new IllegalArgumentException(err), 500);			
		}

		logger.info("Returning Aggregator Info. of "+data.get("aggregatorName"));
		try{
			DSResponse response = new DSResponse();
			response.addRecord(aggAdmin.getAggregatorInfo());
			response.setStatus(DSResponse.STATUS_SUCCESS);
			return response;
		}catch(Exception e){
			logger.error("Failed to retrieve aggregator information for {}.", data.get("aggregatorName"));
			throw new WebApplicationException(e, 500);
		}
	}
	
	@POST
	@Path("/settings")
	public DSResponse getSettings(DSRequest request){
		logger.info("agg/admin/settings called with {} operation.", request.getOperationType().name());
		
		Map<String, Object> data = request.getData();
		OperationType action = request.getOperationType();
		switch(action){
		case FETCH:
			if(data == null || data.get("aggregatorName") == null){
				String err = "No aggregatorName specified in the request";
				logger.error(err);
				throw new WebApplicationException(new IllegalArgumentException(err), 500);			
			}

			logger.info("Returning Aggregator Settings for "+data.get("aggregatorName"));
			DSResponse response = new DSResponse();
			try {
				List<Map<String, Object>> result = aggAdmin.getSettings();
				response.setData(result);
				response.setTotalRows(result.size());
				response.setStatus(DSResponse.STATUS_SUCCESS);
			} catch (Exception e) {
				String err = "Failed to retrieve settings from aggregator server.";
				logger.error(err);
				throw new WebApplicationException(new IllegalArgumentException(err), 500);			
			}
			return response;
		case UPDATE:
			Map<String, Object> oldValues = request.getOldValues();
			if(oldValues == null || oldValues.get("aggregatorName") == null){
				String err = "No aggregatorName specified in the request";
				logger.error(err);
				throw new WebApplicationException(new IllegalArgumentException(err), 500);			
			}

			Object newValue = data.get("value");
			String setting = (String)data.get("name");
			logger.info("Updating Aggregator Setting for {} from {} to {} on Aggregator {}.", new Object[]{setting, oldValues.get("value"), newValue, oldValues.get("aggregatorName")});
			return null;
		default:
			String err = "Operation type "+action.name()+" is not supported.";
			logger.error(err);
			throw new WebApplicationException(new IllegalArgumentException(err), 500);			
		}
	}	
}
