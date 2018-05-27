package org.gama.stalactite.persistence.sql.dml;

import java.util.Map;

import org.gama.sql.binder.ParameterBinder;
import org.gama.sql.binder.ParameterBinderIndex;
import org.gama.sql.binder.PreparedStatementWriterIndex;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * Specialization of {@link ColumnParamedSQL} for select statement: gives access to selected columns through {@link #getSelectParameterBinders()}
 * 
 * @author Guillaume Mary
 */
public class ColumnParamedSelect<T extends Table> extends ColumnParamedSQL<T> {
	
	private final ParameterBinderIndex<String, ParameterBinder> selectParameterBinders;
	
	public ColumnParamedSelect(String sql,
							   Map<Column<T, Object>, int[]> columnIndexes,
							   Map<Column<T, Object>, ParameterBinder> parameterBinders,
							   Map<String, ParameterBinder> selectParameterBinders) {
		super(sql, columnIndexes, parameterBinders);
		this.selectParameterBinders = ParameterBinderIndex.fromMap(selectParameterBinders);
	}
	
	public ColumnParamedSelect(String sql,
							   Map<Column<T, Object>, int[]> columnIndexes,
							   PreparedStatementWriterIndex<Column<T, Object>, ParameterBinder> parameterBinderProvider,
							   Map<String, ParameterBinder> selectParameterBinders) {
		super(sql, columnIndexes, parameterBinderProvider);
		this.selectParameterBinders = ParameterBinderIndex.fromMap(selectParameterBinders);
	}
	
	public ColumnParamedSelect(String sql,
							   Map<Column<T, Object>, int[]> columnIndexes,
							   PreparedStatementWriterIndex<Column<T, Object>, ParameterBinder> parameterBinderProvider,
							   ParameterBinderIndex<String, ParameterBinder> selectParameterBinders) {
		super(sql, columnIndexes, parameterBinderProvider);
		this.selectParameterBinders = selectParameterBinders;
	}
	
	public ParameterBinderIndex<String, ParameterBinder> getSelectParameterBinders() {
		return selectParameterBinders;
	}
}
