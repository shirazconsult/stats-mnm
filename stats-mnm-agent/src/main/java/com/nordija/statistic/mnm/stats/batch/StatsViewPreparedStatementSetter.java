package com.nordija.statistic.mnm.stats.batch;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.springframework.batch.item.database.ItemPreparedStatementSetter;
import org.springframework.stereotype.Component;

import com.nordija.statistic.mnm.stats.StatsView;

@Component
public class StatsViewPreparedStatementSetter implements ItemPreparedStatementSetter<StatsView> {

	@Override
	public void setValues(StatsView sv, PreparedStatement ps)
			throws SQLException {
		if(sv != null){
			ps.setString(1, sv.getType());
			ps.setString(2, sv.getName());
			ps.setString(3, sv.getTitle());
			ps.setLong(4, sv.getViewers());
			ps.setLong(5, sv.getDuraion());
			ps.setLong(6, sv.getFromTS());
			ps.setLong(7, sv.getToTS());
		}
	}
}
