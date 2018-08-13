package org.gama.stalactite.persistence.structure;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;

import static org.gama.lang.collection.Iterables.pair;

/**
 * Foreign key between tables
 * 
 * @author Guillaume Mary
 */
public class ForeignKey<T extends Table, U extends Table> {
	
	private final T table;
	private final String name;
	private final LinkedHashMap<Column<T, ?>, Column<U, ?>> columns;
	private final U targetTable;
	
	public ForeignKey(String name, Column<T, ?> column, Column<U, ?> targetColumn) {
		this(name, Arrays.asSet(column), Arrays.asSet(targetColumn));
	}
	
	public ForeignKey(String name, LinkedHashSet<Column<T, ?>> columns, LinkedHashSet<Column<U, ?>> targetColumns) {
		this(name, pair(columns, targetColumns, LinkedHashMap::new));
	}
	
	public ForeignKey(String name, LinkedHashMap<Column<T, ?>, Column<U, ?>> columns) {
		// table is took from columns
		Entry<Column<T, ?>, Column<U, ?>> firstEntry = Iterables.first(columns.entrySet());
		this.table = firstEntry.getKey().getTable();
		this.targetTable = firstEntry.getValue().getTable();
		this.name = name;
		this.columns = columns;
	}
	
	public Set<Column<T, ?>> getColumns() {
		return columns.keySet();
	}
	
	public String getName() {
		return name;
	}
	
	public Collection<Column<U, ?>> getTargetColumns() {
		return columns.values();
	}
	
	public T getTable() {
		return table;
	}
	
	public U getTargetTable() {
		return targetTable;
	}
}
