package org.gama.stalactite.persistence.structure;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.KeepOrderSet;
import org.gama.stalactite.persistence.structure.Database.Schema;

/**
 * Representation of a database Table, not exhaustive but sufficient for our need.
 * Primary key is design to concern only one Column, but not foreign keys ... not really logical for now !
 * Primary key as a only-one-column design is primarly intend to simplify query and persistence conception.
 *
 * @author Guillaume Mary
 */
public class Table<SELF extends Table<SELF>> {
	
	private Schema schema;
	
	private String name;
	
	private String absoluteName;
	
	private KeepOrderSet<Column<SELF, Object>> columns = new KeepOrderSet<>();
	
	private Column<SELF, Object> primaryKey;
	
	private Set<Index> indexes = new HashSet<>();
	
	private Set<ForeignKey> foreignKeys = new HashSet<>();
	
	public Table(String name) {
		this(null, name);
	}
	
	public Table(Schema schema, String name) {
		this.schema = schema;
		this.name = name;
		this.absoluteName = (schema == null ? "" : (schema.getName() + ".")) + name;
	}
	
	public Schema getSchema() {
		return schema;
	}
	
	public String getName() {
		return name;
	}
	
	public String getAbsoluteName() {
		return absoluteName;
	}
	
	public Set<Column<SELF, Object>> getColumns() {
		return Collections.unmodifiableSet(columns.asSet());
	}
	
	public Set<Column<SELF, Object>> getColumnsNoPrimaryKey() {
		LinkedHashSet<Column<SELF, Object>> result = this.columns.asSet();
		result.remove(getPrimaryKey());
		return result;
	}
	
	public <O> Column<SELF, O> addColumn(String name, Class<O> javaType) {
		Column<SELF, O> column = new Column<>((SELF) this, name, javaType);
		this.columns.add((Column<SELF, Object>) column);
		return column;
	}
	
	public <O> Column<SELF, O> addColumn(String name, Class<O> javaType, int size) {
		Column<SELF, O> column = new Column<>((SELF) this, name, javaType, size);
		this.columns.add((Column<SELF, Object>) column);
		return column;
	}
	
	public Map<String, Column<SELF, Object>> mapColumnsOnName() {
		Map<String, Column<SELF, Object>> mapColumnsOnName = new LinkedHashMap<>(columns.size());
		for (Column<SELF, Object> column : columns) {
			mapColumnsOnName.put(column.getName(), column);
		}
		return mapColumnsOnName;
	}
	
	public Column<SELF, Object> getPrimaryKey() {
		if (primaryKey == null) {
			primaryKey = columns.stream().filter(Column::isPrimaryKey).findAny().orElse(null);
		}
		return primaryKey;
	}
	
	public Set<Index> getIndexes() {
		return Collections.unmodifiableSet(indexes);
	}
	
	public Index addIndex(String name, Column column, Column ... columns) {
		LinkedHashSet<Column> indexedColumns = Arrays.asSet(column);
		indexedColumns.addAll(java.util.Arrays.asList(columns));
		Index newIndex = new Index(name, indexedColumns);
		this.indexes.add(newIndex);
		return newIndex;
	}
	
	public Set<ForeignKey> getForeignKeys() {
		return Collections.unmodifiableSet(foreignKeys);
	}
	
	public <T1 extends Table<T1>, T2 extends Table<T2>, O> ForeignKey addForeignKey(String name, Column<T1, O> column, Column<T2, O> targetColumn) {
		ForeignKey newForeignKey = new ForeignKey(name, column, targetColumn);
		this.foreignKeys.add(newForeignKey);
		return newForeignKey;
	}
	
	public ForeignKey addForeignKey(String name, List<Column> columns, List<Column> targetColumns) {
		ForeignKey newForeignKey = new ForeignKey(name, new LinkedHashSet<>(columns), new LinkedHashSet<>(targetColumns));
		this.foreignKeys.add(newForeignKey);
		return newForeignKey;
	}
	
	/**
	 * Implementation based on name comparison. Override for comparison in Collections.
	 *
	 * @param o an Object
	 * @return true if this table name equals the other table name, case insensitive
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Table)) {
			return false;
		}
		
		Table table = (Table) o;
		return name.equalsIgnoreCase(table.name);
	}
	
	/**
	 * Implemented to be compliant with equals override
	 * @return a hash code based on table name
	 */
	@Override
	public int hashCode() {
		return name.toUpperCase().hashCode();
	}
	
}
