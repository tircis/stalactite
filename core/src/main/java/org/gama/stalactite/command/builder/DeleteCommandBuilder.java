package org.gama.stalactite.command.builder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import org.gama.lang.StringAppender;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.stalactite.sql.dml.PreparedSQL;
import org.gama.stalactite.command.model.Delete;
import org.gama.stalactite.persistence.sql.dml.binder.ColumnBinderRegistry;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.query.builder.OperatorBuilder.PreparedSQLWrapper;
import org.gama.stalactite.query.builder.OperatorBuilder.SQLAppender;
import org.gama.stalactite.query.builder.OperatorBuilder.StringAppenderWrapper;
import org.gama.stalactite.query.builder.SQLBuilder;
import org.gama.stalactite.query.builder.WhereBuilder;
import org.gama.stalactite.query.model.ColumnCriterion;
import org.gama.stalactite.query.model.UnitaryOperator;

/**
 * A SQL builder for {@link Delete} objects
 * 
 * @author Guillaume Mary
 */
public class DeleteCommandBuilder<T extends Table> implements SQLBuilder {
	
	private final Delete<T> delete;
	private final MultiTableAwareDMLNameProvider dmlNameProvider;
	
	public DeleteCommandBuilder(Delete<T> delete) {
		this.delete = delete;
		this.dmlNameProvider = new MultiTableAwareDMLNameProvider();
	}
	
	@Override
	public String toSQL() {
		return toSQL(new StringAppenderWrapper(new StringAppender(), dmlNameProvider), dmlNameProvider);
	}
	
	private String toSQL(SQLAppender result, MultiTableAwareDMLNameProvider dmlNameProvider) {
		result.cat("delete from ");
		
		// looking for additionnal Tables : more than the updated one, can be found in conditions
		Set<Column<T, Object>> whereColumns = new LinkedHashSet<>();
		delete.getCriteria().forEach(c -> {
			if (c instanceof ColumnCriterion) {
				whereColumns.add(((ColumnCriterion) c).getColumn());
				Object condition = ((ColumnCriterion) c).getCondition();
				if (condition instanceof UnitaryOperator && ((UnitaryOperator) condition).getValue() instanceof Column) {
					whereColumns.add((Column) ((UnitaryOperator) condition).getValue());
				}
			}
		});
		Set<T> additionalTables = Iterables.minus(
				Iterables.collect(whereColumns, Column::getTable, HashSet::new),
				Arrays.asList(this.delete.getTargetTable()));
		
		// update of the single-table-marker
		dmlNameProvider.setMultiTable(!additionalTables.isEmpty());
		
		result.cat(this.delete.getTargetTable().getAbsoluteName())    // main table is always referenced with name (not alias)
				.catIf(dmlNameProvider.isMultiTable(), ", ");
		// additional tables (with optional alias)
		Iterator<T> iterator = additionalTables.iterator();
		while (iterator.hasNext()) {
			T next = iterator.next();
			result.cat(next.getAbsoluteName()).catIf(iterator.hasNext(), ", ");
		}
		
		
		// append where clause
		if (delete.getCriteria().iterator().hasNext()) {
			result.cat(" where ");
			WhereBuilder whereBuilder = new WhereBuilder(this.delete.getCriteria(), dmlNameProvider);
			whereBuilder.appendSQL(result);
		}
		return result.getSQL();
	}
	
	public PreparedSQL toStatement(ColumnBinderRegistry columnBinderRegistry) {
		// We ask for SQL generation through a PreparedSQLWrapper because we need SQL placeholders for where + update clause
		PreparedSQLWrapper preparedSQLWrapper = new PreparedSQLWrapper(new StringAppenderWrapper(new StringAppender(), dmlNameProvider), columnBinderRegistry, dmlNameProvider);
		String sql = toSQL(preparedSQLWrapper, dmlNameProvider);
		
		// final assembly
		PreparedSQL result = new PreparedSQL(sql, preparedSQLWrapper.getParameterBinders());
		result.setValues(preparedSQLWrapper.getValues());
		return result;
	}
}
