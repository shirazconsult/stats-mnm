package com.nordija.statistic.mnm.stats.batch;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.nordija.statistic.mnm.stats.StatsView;

@Component
public class StatsViewRowListMapper implements RowMapper<StatsView> {
	@Override
	public StatsView mapRow(ResultSet rs, int rowNum)
			throws SQLException {
		StatsView sv = new StatsView();
		sv.setType(rs.getString("type"));
		sv.setName(rs.getString("name"));
		sv.setTitle(rs.getString("title"));
		sv.setViewers(rs.getLong("viewers"));
		sv.setDuraion(rs.getLong("duration"));
		sv.setFromTS(rs.getLong("fromTS"));
		sv.setToTS(rs.getLong("toTS"));
		
		return sv;
	}
}
