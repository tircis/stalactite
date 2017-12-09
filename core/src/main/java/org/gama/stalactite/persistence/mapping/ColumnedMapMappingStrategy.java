package org.gama.stalactite.persistence.mapping;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.gama.lang.bean.Objects;
import org.gama.lang.collection.Collections;
import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.Iterables.ForEach;
import org.gama.sql.result.Row;
import org.gama.stalactite.persistence.sql.dml.PreparedUpdate.UpwhereColumn;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.persistence.structure.Column;

/**
 * @author Guillaume Mary
 */
public abstract class ColumnedMapMappingStrategy<C extends Map<K, V>, K, V, T> implements IEmbeddedBeanMapper<C> {
	
	private final Table targetTable;
	private final Set<Column> columns;
	private final ToMapRowTransformer<C> rowTransformer;
	
	/**
	 * Constructor 
	 * 
	 * @param targetTable table to persist in
	 * @param columns columns that will be used for persistent of Maps, expected to be a subset of targetTable columns    
	 * @param rowClass Class to instanciate for select from database, expected to be C but can't be typed due to generic complexity
	 */
	public ColumnedMapMappingStrategy(Table targetTable, Set<Column> columns, Class<? extends Map> rowClass) {
		this.targetTable = targetTable;
		this.columns = columns;
		// weird cast cause of generics
		this.rowTransformer = new ToMapRowTransformer<C>((Class<C>) rowClass) {
			/** We bind conversion on MapMappingStrategy conversion methods */
			@Override
			protected void applyRowToBean(Row row, C map) {
				for (Column column : getColumns()) {
					String columnName = column.getName();
					K key = getKey(column);
					map.put(key, toMapValue(key, row.get(columnName)));
				}
			}
		};
	}
	
	public Table getTargetTable() {
		return targetTable;
	}
	
	@Override
	public Set<Column> getColumns() {
		return columns;
	}
	
	protected String getColumnName(String columnsPrefix, int i) {
		return columnsPrefix + i;
	}
	
	@Override
	public Map<Column, Object> getInsertValues(C c) {
		Map<Column, Object> toReturn = new HashMap<>();
		Map<K, V> toIterate = c;
		if (Collections.isEmpty(c)) {
			toIterate = new HashMap<>();
		}
		Iterables.visit(toIterate.entrySet(), new ForEach<Entry<K, V>, Void>() {
			@Override
			public Void visit(Entry<K, V> mapEntry) {
				addUpsertValues(mapEntry.getKey(), mapEntry.getValue(), toReturn);
				return null;
			}
		});
		// NB: we must return all columns: we complete non-valued columns with null 
		for (Column column : columns) {
			if (!toReturn.containsKey(column)) {
				toReturn.put(column, null);
			}
		}
		return toReturn;
	}
	
	@Override
	public Map<UpwhereColumn, Object> getUpdateValues(C modified, C unmodified, boolean allColumns) {
		Map<Column, Object> unmodifiedColumns = new HashMap<>();
		Map<Column, Object> toReturn = new HashMap<>();
		if (modified != null) {
			// getting differences
			// - all of modified but different in unmodified
			for (Entry<K, V> modifiedEntry : modified.entrySet()) {
				K modifiedKey = modifiedEntry.getKey();
				V modifiedValue = modifiedEntry.getValue();
				Column column = getColumn(modifiedKey);
				if (!Objects.equalsWithNull(modifiedValue, unmodified == null ? null : unmodified.get(modifiedKey))) {
					toReturn.put(column, modifiedValue);
				} else {
					unmodifiedColumns.put(column, modifiedValue);
				}
			}
			// - all from unmodified missing in modified
			HashSet<K> missingInModified = unmodified == null ? new HashSet<>() : new HashSet<>(unmodified.keySet());
			missingInModified.removeAll(modified.keySet());
			for (K k : missingInModified) {
				addUpsertValues(k, modified.get(k), toReturn);
			}
			
			// adding complementary columns if necessary
			if (allColumns && !toReturn.isEmpty()) {
				Set<Column> missingColumns = new LinkedHashSet<>(columns);
				missingColumns.removeAll(toReturn.keySet());
				for (Column missingColumn : missingColumns) {
					Object missingValue = unmodifiedColumns.get(missingColumn);
					toReturn.put(missingColumn, missingValue);
				}
			}
		} else if (allColumns && unmodified != null) {
			for (Column column : columns) {
				toReturn.put(column, null);
			}
		}
		return convertToUpwhereColumn(toReturn);
	}
	
	private Map<UpwhereColumn, Object> convertToUpwhereColumn(Map<Column, Object> map) {
		Map<UpwhereColumn, Object> convertion = new HashMap<>();
		map.forEach((c, s) -> convertion.put(new UpwhereColumn(c, true), s));
		return convertion;
	}
	
	/**
	 * Add values to valuesToBePersisted according to key and value.
	 * Calls {@link #toDatabaseValue(Object, Object)} to transform value to the persisted Object
	 * 
	 * @param key the key to be persisted
	 * @param value the value ok key in the Map, may be transformed to be persisted
	 * @param valuesToBePersisted Map to populate
	 */
	protected void addUpsertValues(K key, V value, Map<Column, Object> valuesToBePersisted) {
		T t = toDatabaseValue(key, value);
		Column column = getColumn(key);
		valuesToBePersisted.put(column, t);
	}
	
	protected abstract Column getColumn(K k);
	
	/**
	 * Expected to return the persisted value for v of key k 
	 * @param k the key being persisted, help to determine how to convert v
	 * @param v the value to be persisted
	 * @return the dabase value to be persisted
	 */
	protected abstract T toDatabaseValue(K k, V v);
	
	/**
	 * Reverse of {@link #getColumn(Object)}: give a map key from a column name
	 * @param column
	 * @return a key for a Map
	 */
	protected abstract K getKey(Column column);
	
	/**
	 * Reverse of {@link #toDatabaseValue(Object, Object)}: give a map value from a database selected value
	 * @param k the key being read, help to determine how to convert t
	 * @param t the data from the database
	 * @return a value for a Map
	 */
	protected abstract V toMapValue(K k, Object t);

	@Override
	public C transform(Row row) {
		return this.rowTransformer.transform(row);
	}
}
