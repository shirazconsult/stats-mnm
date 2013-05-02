package com.nordija.statistic.mnm.stats;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class StatsRowListMapper implements RowMapper<List<Object>> {
	@Override
	public List<Object> mapRow(ResultSet rs, int rowNum)
			throws SQLException {
		List<Object> row = new ArrayList<Object>();
		row.add(rs.getLong("id"));
		row.add(rs.getString("cusRef"));
		row.add(rs.getString("devRef"));
		row.add(rs.getString("devType"));
		row.add(rs.getString("devModel"));
		row.add(rs.getString("timeZone"));
		row.add(rs.getString("cusGrRefs"));
		row.add(rs.getString("ref"));
		row.add(rs.getString("type"));
		row.add(rs.getString("name"));
		row.add(rs.getLong("time"));
		row.add(rs.getLong("duration"));
		row.add(rs.getString("extra"));
		row.add(rs.getLong("deliveredTS"));
		row.add(rs.getDate("insertedTS"));
		
		return row;
	}
}
