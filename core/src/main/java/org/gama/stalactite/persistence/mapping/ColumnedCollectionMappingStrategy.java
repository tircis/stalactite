package org.gama.stalactite.persistence.mapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.gama.lang.bean.Objects;
import org.gama.lang.collection.Collections;
import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.PairIterator;
import org.gama.lang.collection.PairIterator.EmptyIterator;
import org.gama.lang.collection.PairIterator.InfiniteIterator;
import org.gama.lang.collection.PairIterator.UntilBothIterator;
import org.gama.sql.result.Row;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * A class that "roughly" persists a Collection of {@link Column}s, without any bean class.
 * One may override {@link #toCollectionValue(Object)} and {@link #toDatabaseValue(Object)} to adapt values.
 * 
 * @author Guillaume Mary
 */
public class ColumnedCollectionMappingStrategy<C extends Collection<O>, O, T extends Table> implements IEmbeddedBeanMapper<C, T> {
	
	private final T targetTable;
	private final Set<Column<T, Object>> columns;
	private final ToCollectionRowTransformer<C> rowTransformer;
	
	/**
	 * Constructor 
	 *
	 * @param targetTable table to persist in
	 * @param columns columns that will be used for persistent of Collections, expected to be a subset of targetTable columns    
	 * @param rowClass Class to instanciate for select from database
	 */
	public ColumnedCollectionMappingStrategy(T targetTable, Set<Column<T, Object>> columns, Class<C> rowClass) {
		this.targetTable = targetTable;
		this.columns = columns;
		this.rowTransformer = new ToCollectionRowTransformer<C>(rowClass) {
			/** We bind conversion on {@link ColumnedCollectionMappingStrategy} conversion methods */
			@Override
			protected void applyRowToBean(Row row, C collection) {
				for (Column column : getColumns()) {
					Object value = row.get(column.getName());
					collection.add(toCollectionValue(value));
				}
			}
		};
	}
	
	public T getTargetTable() {
		return targetTable;
	}
	
	@Override
	public Set<Column<T, Object>> getColumns() {
		return columns;
	}
	
	@Override
	public Map<Column<T, Object>, Object> getInsertValues(C c) {
		Collection<O> toIterate = c;
		if (Collections.isEmpty(c)) {
			toIterate = new ArrayList<>();
		}
		// NB: we wrap c.iterator() in an InfiniteIterator to get all columns generated: overflow columns will have
		// null value (see 	InfiniteIterator#getValue)
		PairIterator<Column<T, Object>, O> valueColumnPairIterator = new PairIterator<>(columns.iterator(), new InfiniteIterator<>(toIterate.iterator()));
		return Iterables.map(() -> valueColumnPairIterator, Entry::getKey, e -> toDatabaseValue(e.getValue()));
	}
	
	@Override
	public Map<UpwhereColumn<T>, Object> getUpdateValues(C modified, C unmodified, boolean allColumns) {
		Map<Column<T, Object>, Object> toReturn = new HashMap<>();
		if (modified != null) {
			// getting differences side by side
			Map<Column, Object> unmodifiedColumns = new LinkedHashMap<>();
			Iterator<O> unmodifiedIterator = unmodified == null ? new EmptyIterator<>() : unmodified.iterator();
			UntilBothIterator<O, O> untilBothIterator = new UntilBothIterator<>(modified.iterator(), unmodifiedIterator);
			PairIterator<Column<T, Object>, Entry<O, O>> valueColumnPairIterator = new PairIterator<>(columns.iterator(), untilBothIterator);
			valueColumnPairIterator.forEachRemaining(diffEntry -> {
				Column fieldColumn = diffEntry.getKey();
				Entry<O, O> toBeCompared = diffEntry.getValue();
				if (!Objects.equalsWithNull(toBeCompared.getKey(), toBeCompared.getValue())) {
					toReturn.put(fieldColumn, toDatabaseValue(toBeCompared.getKey()));
				} else {
					unmodifiedColumns.put(fieldColumn, toBeCompared.getKey());
				}
			});
			
			// adding complementary columns if necessary
			if (allColumns && !toReturn.isEmpty()) {
				Set<Column<T, Object>> missingColumns = new LinkedHashSet<>(columns);
				missingColumns.removeAll(toReturn.keySet());
				for (Column<T, Object> missingColumn : missingColumns) {
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
	
	private Map<UpwhereColumn<T>, Object> convertToUpwhereColumn(Map<? extends Column<T, Object>, Object> map) {
		Map<UpwhereColumn<T>, Object> convertion = new HashMap<>();
		map.forEach((c, s) -> convertion.put(new UpwhereColumn<>(c, true), s));
		return convertion;
	}
	
	protected Object toDatabaseValue(O object) {
		return object;
	}
	
	/**
	 * Opposit of {@link #toDatabaseValue(Object)}: converts the database value for the collection value
	 * 
	 * @param object the value coming from the database {@link java.sql.ResultSet}
	 * @return a value for a Map
	 */
	protected O toCollectionValue(Object object) {
		return (O) object;
	}
	
	@Override
	public C transform(Row row) {
		return this.rowTransformer.transform(row);
	}
}
