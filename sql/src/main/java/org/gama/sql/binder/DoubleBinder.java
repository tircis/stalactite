package org.gama.sql.binder;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * {@link AbstractParameterBinder} dédié aux Doubles
 * 
 * @author mary
 */
public class DoubleBinder extends AbstractParameterBinder<Double> {

	@Override
	public void setNotNull(int valueIndex, Double value, PreparedStatement statement) throws SQLException {
		statement.setDouble(valueIndex, value);
	}

	@Override
	public Double getNotNull(String columnName, ResultSet resultSet) throws SQLException {
		return resultSet.getDouble(columnName);
	}
}
