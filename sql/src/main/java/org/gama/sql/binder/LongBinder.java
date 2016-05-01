package org.gama.sql.binder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@link AbstractParameterBinder} dédié aux Longs
 * 
 * @author mary
 */
public class LongBinder extends AbstractParameterBinder<Long> {

	@Override
	public void setNotNull(int valueIndex, Long value, PreparedStatement statement) throws SQLException {
		statement.setLong(valueIndex, value);
	}

	@Override
	public Long getNotNull(String columnName, ResultSet resultSet) throws SQLException {
		return resultSet.getLong(columnName);
	}
}
