package com.nordija.statistic.mnm.stats;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

public class StatsViewBatchStatementSetter implements ParameterizedPreparedStatementSetter<StatsView> {

	@Override
	public void setValues(PreparedStatement ps, StatsView sv) throws SQLException {
		ps.setString(1, sv.getType());
		ps.setString(2, sv.getName());
		ps.setString(3, sv.getTitle());
		ps.setLong(4, sv.getViewers());
		ps.setLong(5, sv.getDuraion());
		ps.setLong(6, sv.getFromTS());
		ps.setLong(7, sv.getToTS());
	}

}
