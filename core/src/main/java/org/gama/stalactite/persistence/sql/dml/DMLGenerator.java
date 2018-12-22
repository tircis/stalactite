package org.gama.stalactite.persistence.sql.dml;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import org.gama.lang.Strings;
import org.gama.lang.collection.ISorter;
import org.gama.lang.collection.Iterables;
import org.gama.lang.trace.ModifiableInt;
import org.gama.sql.binder.ParameterBinder;
import org.gama.sql.binder.ParameterBinderIndex;
import org.gama.stalactite.persistence.engine.DMLExecutor;
import org.gama.stalactite.persistence.mapping.IMappingStrategy.UpwhereColumn;
import org.gama.stalactite.persistence.sql.ddl.DDLAppender;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.query.builder.DMLNameProvider;

import static org.gama.sql.dml.ExpandableSQL.ExpandableParameter.SQL_PARAMETER_MARK;
import static org.gama.sql.dml.ExpandableSQL.ExpandableParameter.SQL_PARAMETER_MARK_1;
import static org.gama.sql.dml.ExpandableSQL.ExpandableParameter.SQL_PARAMETER_MARK_10;
import static org.gama.sql.dml.ExpandableSQL.ExpandableParameter.SQL_PARAMETER_MARK_100;

/**
 * Class for DML generation dedicated to {@link DMLExecutor}. Not expected to be used elsewhere.
 *
 * @author Guillaume Mary
 */
public class DMLGenerator {
	
	private static final String EQUAL_SQL_PARAMETER_MARK_AND = " = " + SQL_PARAMETER_MARK + " and ";
	
	private final ParameterBinderIndex<Column, ParameterBinder> columnBinderRegistry;
	
	private final ISorter<Iterable<? extends Column>> columnSorter;
	
	private final DMLNameProvider dmlNameProvider;
	
	public DMLGenerator(ParameterBinderIndex<Column, ParameterBinder> columnBinderRegistry) {
		this(columnBinderRegistry, NoopSorter.INSTANCE);
	}
	
	public DMLGenerator(ParameterBinderIndex<Column, ParameterBinder> columnBinderRegistry, ISorter<Iterable<? extends Column>> columnSorter) {
		this(columnBinderRegistry, columnSorter, new DMLNameProvider(Collections.emptyMap()));
	}
	
	public DMLGenerator(ParameterBinderIndex<Column, ParameterBinder> columnBinderRegistry, ISorter<Iterable<? extends Column>> columnSorter, DMLNameProvider dmlNameProvider) {
		this.columnBinderRegistry = columnBinderRegistry;
		this.columnSorter = columnSorter;
		this.dmlNameProvider = dmlNameProvider;
	}
	
	/**
	 * Creates a SQL statement order for inserting some data in a table.
	 * Signature is made so that only columns of the same table can be used.
	 *
	 * @param columns columns that must be inserted, at least 1 element
	 * @param <T> table type
	 * @return a (kind of) prepared statement
	 */
	public <T extends Table> ColumnParameterizedSQL<T> buildInsert(Iterable<? extends Column<T, Object>> columns) {
		columns = (Iterable<? extends Column<T, Object>>) sort(columns);
		Table table = Iterables.first(columns).getTable();
		DDLAppender sqlInsert = new DDLAppender(dmlNameProvider, "insert into ", table, "(");
		sqlInsert.ccat(columns, ", ");
		sqlInsert.cat(") values (");
		
		Map<Column<T, Object>, int[]> columnToIndex = new HashMap<>();
		Map<Column<T, Object>, ParameterBinder> parameterBinders = new HashMap<>();
		ModifiableInt positionCounter = new ModifiableInt(1);
		Iterables.stream(columns).forEach(column -> {
			sqlInsert.cat(SQL_PARAMETER_MARK_1);
			columnToIndex.put(column, new int[] { positionCounter.getValue() });
			positionCounter.increment();
			parameterBinders.put(column, columnBinderRegistry.getBinder(column));
		});
		sqlInsert.cutTail(2).cat(")");
		return new ColumnParameterizedSQL<>(sqlInsert.toString(), columnToIndex, parameterBinders);
	}
	
	/**
	 * Creates a SQL statement order for updating some database rows depending on a where clause.
	 * Signature is made so that only columns of the same table can be used.
	 * 
	 * @param columns columns that must be updated, at least 1 element
	 * @param where columns to use for where clause
	 * @param <T> table type
	 * @return a (kind of) prepared statement
	 */
	public <T extends Table> PreparedUpdate<T> buildUpdate(Iterable<? extends Column<T, Object>> columns, Iterable<? extends Column<T, Object>> where) {
		columns = (Iterable<? extends Column<T, Object>>) sort(columns);
		Table table = Iterables.first(columns).getTable();
		DDLAppender sqlUpdate = new DDLAppender(dmlNameProvider, "update ", table, " set ");
		Map<UpwhereColumn<T>, Integer> upsertIndexes = new HashMap<>(10);
		Map<UpwhereColumn<T>, ParameterBinder> parameterBinders = new HashMap<>();
		int positionCounter = 1;
		for (Column<T, Object> column : columns) {
			sqlUpdate.cat(column, " = " + SQL_PARAMETER_MARK_1);
			UpwhereColumn<T> upwhereColumn = new UpwhereColumn<>(column, true);
			upsertIndexes.put(upwhereColumn, positionCounter++);
			parameterBinders.put(upwhereColumn, columnBinderRegistry.getBinder(column));
		}
		sqlUpdate.cutTail(2).cat(" where ");
		for (Column<T, Object> column : where) {
			sqlUpdate.cat(column, EQUAL_SQL_PARAMETER_MARK_AND);
			UpwhereColumn<T> upwhereColumn = new UpwhereColumn<>(column, false);
			upsertIndexes.put(upwhereColumn, positionCounter++);
			parameterBinders.put(upwhereColumn, columnBinderRegistry.getBinder(column));
		}
		return new PreparedUpdate<>(sqlUpdate.cutTail(5).toString(), upsertIndexes, parameterBinders);
	}
	
	/**
	 * Creates a SQL statement order for deleting some database rows depending on a where clause.
	 * Signature is made so that only columns of the same table can be used.
	 *
	 * @param table deletion target table
	 * @param where columns to use for where clause
	 * @param <T> table type
	 * @return a (kind of) prepared statement parameterized by {@link Column}
	 */
	public <T extends Table> ColumnParameterizedSQL<T> buildDelete(T table, Iterable<? extends Column<T, Object>> where) {
		DDLAppender sqlDelete = new DDLAppender(dmlNameProvider, "delete from ", table);
		sqlDelete.cat(" where ");
		ParameterizedWhere<T> parameterizedWhere = appendWhere(sqlDelete, where);
		sqlDelete.cutTail(5);
		return new ColumnParameterizedSQL<>(sqlDelete.toString(), parameterizedWhere.columnToIndex, parameterizedWhere.parameterBinders);
	}
	
	/**
	 * Creates a SQL statement order for deleting some database rows by key (with a "in (?, ?, ? ...)")
	 * Signature is made so that only columns of the same table can be used.
	 *
	 * @param table deletion target table
	 * @param keyColumns key columns to use for where clause
	 * @param whereValuesCount number of parameter in where clause (ie number of key values in where)
	 * @param <T> table type
	 * @return a (kind of) prepared statement parameterized by {@link Column}
	 */
	@SuppressWarnings("squid:ForLoopCounterChangedCheck")
	public <T extends Table<T>> ColumnParameterizedSQL<T> buildDeleteByKey(T table, Collection<Column<T, Object>> keyColumns, int whereValuesCount) {
		DDLAppender sqlDelete = new DDLAppender(dmlNameProvider, "delete from ", table, " where ");
		ParameterizedWhere parameterizedWhere = appendTupledWhere(sqlDelete, keyColumns, whereValuesCount);
		Map<Column<T, Object>, int[]> columnToIndex = parameterizedWhere.getColumnToIndex();
		Map<Column<T, Object>, ParameterBinder> parameterBinders = parameterizedWhere.getParameterBinders();
		return new ColumnParameterizedSQL<>(sqlDelete.toString(), columnToIndex, parameterBinders);
	}
	
	/**
	 * Creates a SQL statement order for selecting some database rows depending on a where clause.
	 * Signature is made so that only columns of the same table can be used.
	 *
	 * @param table selection target table
	 * @param where columns to use for where clause
	 * @param <T> table type
	 * @return a (kind of) prepared statement parameterized by {@link Column}
	 */
	public <T extends Table<T>> ColumnParameterizedSQL<T> buildSelect(T table, Iterable<? extends Column<T, ?>> columns, Iterable<? extends Column<T, Object>> where) {
		columns = (Iterable<? extends Column<T, Object>>) sort(columns);
		DDLAppender sqlSelect = new DDLAppender(dmlNameProvider, "select ");
		sqlSelect.ccat(columns, ", ");
		sqlSelect.cat(" from ", table, " where ");
		ParameterizedWhere<T> parameterizedWhere = appendWhere(sqlSelect, where);
		sqlSelect.cutTail(5);
		return new ColumnParameterizedSQL<>(sqlSelect.toString(), parameterizedWhere.columnToIndex, parameterizedWhere.parameterBinders);
	}
	
	/**
	 * Creates a SQL statement order for selecting some database rows by key (with a "in (?, ?, ? ...)")
	 * Signature is made so that only columns of the same table can be used.
	 *
	 * @param table selection target table
	 * @param keyColumns key columns to use for where clause
	 * @param whereValuesCount number of parameter in where clause (ie number of key values in where)
	 * @param <T> table type
	 * @return a (kind of) prepared statement parameterized by {@link Column}
	 */
	public <T extends Table<T>> ColumnParameterizedSelect<T> buildSelectByKey(T table, Iterable<? extends Column<T, Object>> columns, Collection<Column<T, Object>> keyColumns, int whereValuesCount) {
		columns = (Iterable<? extends Column<T, Object>>) sort(columns);
		DDLAppender sqlSelect = new DDLAppender(dmlNameProvider, "select ");
		Map<String, ParameterBinder> selectParameterBinders = new HashMap<>();
		for (Column column : columns) {
			sqlSelect.cat(column, ", ");
			selectParameterBinders.put(dmlNameProvider.getSimpleName(column), columnBinderRegistry.getBinder(column));
		}
		sqlSelect.cutTail(2).cat(" from ", table, " where ");
		ParameterizedWhere parameterizedWhere = appendTupledWhere(sqlSelect, keyColumns, whereValuesCount);
		Map<Column<T, Object>, int[]> columnToIndex = parameterizedWhere.getColumnToIndex();
		Map<Column<T, Object>, ParameterBinder> parameterBinders = parameterizedWhere.getParameterBinders();
		return new ColumnParameterizedSelect<>(sqlSelect.toString(), columnToIndex, parameterBinders, selectParameterBinders);
	}
	
	private Iterable<? extends Column> sort(Iterable<? extends Column> columns) {
		return this.columnSorter.sort(columns);
	}
	
	public static class NoopSorter implements ISorter<Iterable<? extends Column>> {
		
		public static final NoopSorter INSTANCE = new NoopSorter();
		
		@Override
		public Iterable<? extends Column> sort(Iterable<? extends Column> columns) {
			return columns;
		}
	}
	
	/**
	 * Sorts columns passed as arguments.
	 * Optional action since it's used in appending String operation.
	 * Usefull for unit tests and debug operations : give steady (and logical) column places in SQL, without this, order
	 * is given by Column hashcode and HashMap algorithm.
	 */
	public static class CaseSensitiveSorter<C extends Column> implements ISorter<Iterable<C>> {
		
		public static final CaseSensitiveSorter INSTANCE = new CaseSensitiveSorter();
		
		@Override
		public Iterable<C> sort(Iterable<C> columns) {
			TreeSet<C> result = new TreeSet<>(ColumnNameComparator.INSTANCE);
			for (C column : columns) {
				result.add(column);
			}
			return result;
		}
	}
	
	/**
	 * Appends a where condition (without "where" keyword) to a given sql order.
	 * Conditions are appended with the form of ands such as {@code a = ? and b = ? and ...}
	 *
	 * @param sql the sql order on which to append the clause
	 * @param conditionColumns columns of the where
	 * @param <T> type of the table
	 * @return an object that contains indexes and parameter binders of the where
	 */
	private <T extends Table> ParameterizedWhere<T> appendWhere(DDLAppender sql, Iterable<? extends Column<T, Object>> conditionColumns) {
		ParameterizedWhere<T> result = new ParameterizedWhere<>();
		ModifiableInt positionCounter = new ModifiableInt(1);
		Iterables.stream(conditionColumns).forEach(column -> {
			sql.cat(column, EQUAL_SQL_PARAMETER_MARK_AND);
			result.columnToIndex.put(column, new int[] { positionCounter.getValue() });
			positionCounter.increment();
			result.parameterBinders.put(column, columnBinderRegistry.getBinder(column));
		});
		return result;
	}
	
	/**
	 * Appends a where condition (without "where" keyword) to a given sql order.
	 * Conditions are appended with the form of tuples such as {@code (a, b) in ((?, ?), (?, ?), ...)}
	 * 
	 * @param sql the sql order on which to append the clause
	 * @param conditionColumns columns of the where
	 * @param whereValuesCount expected number of tuples
	 * @param <T> type of the table
	 * @return an object that contains indexes and parameter binders of the where
	 */
	public <T extends Table> ParameterizedWhere<T> appendTupledWhere(DDLAppender sql, Collection<Column<T, Object>> conditionColumns, int whereValuesCount) {
		ParameterizedWhere<T> result = new ParameterizedWhere<>();
		boolean isComposedKey = conditionColumns.size() > 1;
		sql.catIf(isComposedKey, "(")
				.ccat(conditionColumns, ", ")
				.catIf(isComposedKey, ")");
		sql.cat(" in (");
		StringBuilder repeat = Strings.repeat(conditionColumns.size(), SQL_PARAMETER_MARK_1, SQL_PARAMETER_MARK_100, SQL_PARAMETER_MARK_10);
		repeat.setLength(repeat.length() - 2);
		String keyMarks = repeat.toString();
		for (int i = 1; i <= whereValuesCount; i++) {
			sql.catIf(isComposedKey, "(").cat(keyMarks);
			sql.catIf(isComposedKey, ")").cat(", ");
			// because statement indexes start at 0, we must decrement index of 1
			final int startKeyMarkIndex = i-1;
			ModifiableInt pkIndex = new ModifiableInt();
			conditionColumns.forEach(keyColumn -> {
				int pkColumnIndex = startKeyMarkIndex * conditionColumns.size() + pkIndex.increment();
				result.columnToIndex.computeIfAbsent(keyColumn, k -> new int[whereValuesCount])[startKeyMarkIndex] = pkColumnIndex;
			});
		}
		conditionColumns.forEach(keyColumn -> result.parameterBinders.put(keyColumn, columnBinderRegistry.getBinder(keyColumn)));
		sql.cutTail(2).cat(")");
		return result;
	}
	
	public class ParameterizedWhere<T extends Table> {
		
		private Map<Column<T, Object>, int[]> columnToIndex = new HashMap<>();
		
		private Map<Column<T, Object>, ParameterBinder> parameterBinders = new HashMap<>();
		
		public Map<Column<T, Object>, int[]> getColumnToIndex() {
			return columnToIndex;
		}
		
		public Map<Column<T, Object>, ParameterBinder> getParameterBinders() {
			return parameterBinders;
		}
		
	}
	
	private static class ColumnNameComparator implements Comparator<Column> {
		
		private static final ColumnNameComparator INSTANCE = new ColumnNameComparator();
		
		@Override
		public int compare(Column o1, Column o2) {
			return String.CASE_INSENSITIVE_ORDER.compare(o1.getAbsoluteName(), o2.getAbsoluteName());
		}
	}
}