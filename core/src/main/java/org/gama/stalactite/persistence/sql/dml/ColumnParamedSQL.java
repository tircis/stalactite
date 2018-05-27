package org.gama.stalactite.persistence.sql.dml;

import java.util.Map;

import org.gama.sql.binder.ParameterBinder;
import org.gama.sql.binder.PreparedStatementWriter;
import org.gama.sql.binder.PreparedStatementWriterIndex;
import org.gama.sql.dml.ExpandableStatement;
import org.gama.sql.dml.PreparedSQL;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * Equivalent to {@link PreparedSQL} but with Column as index identifier.
 * 
 * @author Guillaume Mary
 */
public class ColumnParamedSQL<T extends Table> extends ExpandableStatement<Column<T, ?>> {
	
	private final Map<Column<T, Object>, int[]> columnIndexes;
	
	/**
	 * Detailed constructor
	 * 
	 * @param sql any SQL statement with placeholder marks ('?')
	 * @param columnIndexes mapping between {@link Column}s (used on {@link #setValue(Object, Object)} and their indexes in the SQL statement
	 * @param parameterBinders mapping between {@link Column}s and their 
	 */
	public ColumnParamedSQL(String sql, Map<Column<T, Object>, int[]> columnIndexes, Map<Column<T, Object>, ? extends PreparedStatementWriter> parameterBinders) {
		super(sql, parameterBinders);
		this.columnIndexes = columnIndexes;
	}
	
	public ColumnParamedSQL(String sql, Map<Column<T, Object>, int[]> columnIndexes, PreparedStatementWriterIndex<Column<T, Object>, ParameterBinder> parameterBinderProvider) {
		super(sql, parameterBinderProvider);
		this.columnIndexes = columnIndexes;
	}
	
	@Override
	protected String getParameterName(Column column) {
		return column.getAbsoluteName();
	}
	
	public int[] getIndexes(Column column) {
		return columnIndexes.get(column);
	}
}
