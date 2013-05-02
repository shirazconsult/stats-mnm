package com.nordija.statistic.mnm.stats.livedatasimulation;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.springframework.batch.item.database.ItemPreparedStatementSetter;
import org.springframework.stereotype.Component;

@Component
public class StatsPreparedStatementSetter implements
		ItemPreparedStatementSetter<List<Object>> {

	@Override
	public void setValues(List<Object> item, PreparedStatement ps)
			throws SQLException {
		ps.setLong(1, (Long) item.get(0));
		ps.setString(2, (String) item.get(1));
		ps.setString(3, (String) item.get(2));
		ps.setString(4, (String) item.get(3));
		ps.setString(5, (String) item.get(4));
		ps.setString(6, (String) item.get(5));
		ps.setString(7, (String) item.get(6));
		ps.setString(8, (String) item.get(7));
		ps.setString(9, (String) item.get(8));
		ps.setString(10, (String) item.get(9));
		ps.setLong(11, (Long) item.get(10));
		ps.setLong(12, (Long) item.get(11));
		ps.setString(13, (String) item.get(12));
		ps.setLong(14, (Long) item.get(13));
		ps.setDate(15, (Date) item.get(14));
	}
}
