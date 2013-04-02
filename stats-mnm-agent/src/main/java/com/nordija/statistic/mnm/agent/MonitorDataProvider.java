package com.nordija.statistic.mnm.agent;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nordija.activemq.monitor.BrokerMonitor;
import com.nordija.statistic.mnm.rest.model.ListResult;
import com.nordija.statistic.mnm.rest.model.NestedList;
import com.nordija.statistic.mnm.rest.model.TARGET;
import com.nordija.statistic.monitoring.aggregator.AggregatorMonitor;

@Service("monitorDataProvider")
@Path("/monitor")
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
public class MonitorDataProvider {
	private static final Logger logger = LoggerFactory.getLogger(MonitorDataProvider.class);
	
	@Autowired
	private BrokerMonitor brokerMonitor;
	@Autowired
	private AggregatorMonitor aggregatorMonitor;
		
	@GET
	@Path("/columns/{target}")
	public ListResult<String> getColumnNames(@PathParam("target") String target){
		logger.debug("Returning column meta data for {}.", target);
		switch(TARGET.valueOf(target)){
		case ActiveMQ:
			try{
				ListResult<String> columnNames = new ListResult<String>(brokerMonitor.getDataColumns());
				return columnNames;
			}catch(Exception e){ 
				logger.error("Failed to retrieve columns metadata from ActiveMQ Monitor.");
				throw new WebApplicationException(e, 500);
			}
		case Aggregator:
			try{
				ListResult<String> columnNames = new ListResult<String>(aggregatorMonitor.getDataColumns());
				return columnNames;
			}catch(Exception e){
				logger.error("Failed to retrieve columns metadata from Aggregator Monitor.");
				throw new WebApplicationException(e, 500);
			}
		default:
			throw new WebApplicationException(Response.Status.BAD_REQUEST);
		}
	}

	/*
	 * fetch rows inclusive from exclusive to
	 */
	@POST
	@Path("/rows/{target}/{from}/{to}")
	public NestedList<String> getRows(@PathParam("target") String target, @PathParam("from") long from, @PathParam("to") long to) {
		logger.debug("Returning rows {} - {} for {}", new String[]{String.valueOf(from), String.valueOf(to), target});
		
		switch(TARGET.valueOf(target)){
		case ActiveMQ:
			try {
				return new NestedList<String>(brokerMonitor.getDataRows(from, to));
			} catch (Exception e) {
				logger.error("Failed to retrieve monitor data from ActiveMQ Monitor. Request was: ActiveMQ/{}/{}", from, to);
				throw new WebApplicationException(e, 500);
			}		
		case Aggregator:
			try {
				return new NestedList<String>(aggregatorMonitor.getDataRows(from, to));
			} catch (Exception e) {
				logger.error("Failed to retrieve monitor data from Aggregator Monitor. Request was: Aggregator/{}/{}", from, to);
				throw new WebApplicationException(e, 500);
			}		
		default:
			throw new WebApplicationException(Response.Status.BAD_REQUEST);
		}
	}
	
	@GET
	@Path("/row/{target}")
	public ListResult<String> getLastRow(@PathParam("target") String target) {
		logger.debug("Returning next row for {}", target);
		try {
			switch(TARGET.valueOf(target)){
			case ActiveMQ:
				try{
					return new ListResult<String>(brokerMonitor.getLastDataRow());
				} catch (Exception e) {
					logger.error("Failed to retrieve monitor data from ActiveMQ Monitor. Request was: ActiveMQ/row");
					throw new WebApplicationException(e, 500);
				}
			case Aggregator:
				try{
					return new ListResult<String>(aggregatorMonitor.getLastDataRow());
				} catch (Exception e) {
					logger.error("Failed to retrieve monitor data from Aggregator Monitor. Request was: Aggregator/row");
					throw new WebApplicationException(e, 500);
				}
			default:
				throw new WebApplicationException(Response.Status.BAD_REQUEST);
			}
		} catch (Exception e) {
			throw new WebApplicationException(e, 500);
		}		
		
	}
}
