package org.gama.stalactite.persistence.structure;

import javax.annotation.Nonnull;

/**
 * Column of a table.
 * 
 * @param <O> the Java type this columns is mapped to
 * @author Guillaume Mary
 */
public class Column<T extends Table, O> {
	
	private final T table;
	private final String name;
	private final Class<O> javaType;
	private final Integer size;
	private final String absoluteName;
	private final String alias;
	private boolean primaryKey;
	private boolean autoGenerated;
	private boolean nullable;
	
	/**
	 * Build a column
	 */
	public Column(@Nonnull T owner, String name, Class<O> javaType) {
		this(owner, name, javaType, null);
	}
	
	/**
	 * Build a column with a size
	 */
	public Column(@Nonnull T owner, String name, Class<O> javaType, Integer size) {
		this.table = owner;
		this.name = name;
		this.javaType = javaType;
		this.size = size;
		this.absoluteName = getTable().getName() + "." + getName();
		this.alias = getTable().getName() + "_" + getName();
		this.nullable = !javaType.isPrimitive();	// default basic principle
	}
	
	public T getTable() {
		return table;
	}
	
	public String getName() {
		return name;
	}
	
	/**
	 * Gives the column name prefixed by table name. Allows column identification in a schema.
	 *
	 * @return getTable().getName() + "." + getName()
	 */
	public String getAbsoluteName() {
		return absoluteName;
	}
	
	/**
	 * Provides a default alias usable for select clause
	 * @return getTable().getName() +"_" + getName()
	 */
	public String getAlias() {
		return alias;
	}
	
	public Class<O> getJavaType() {
		return javaType;
	}
	
	public Integer getSize() {
		return size;
	}
	
	public boolean isNullable() {
		return nullable;
	}
	
	public void setNullable(boolean nullable) {
		this.nullable = nullable;
	}
	
	/**
	 * Fluent API
	 * @param nullable is this Column is optional or mandatory
	 * @return this
	 */
	public Column nullable(boolean nullable) {
		setNullable(nullable);
		return this;
	}
	
	public boolean isPrimaryKey() {
		return primaryKey;
	}
	
	public void setPrimaryKey(boolean primaryKey) {
		this.primaryKey = primaryKey;
	}
	
	/**
	 * Fluent API. Set this column as primary of the table.
	 * @return this
	 */
	public Column<T, O> primaryKey() {
		setPrimaryKey(true);
		return this;
	}
	
	public boolean isAutoGenerated() {
		return autoGenerated;
	}
	
	public void setAutoGenerated(boolean autoGenerated) {
		if (!isPrimaryKey() && autoGenerated) {
			throw new UnsupportedOperationException("Auto generate operation is only supported for primary key, please declare the column as primary key before");
		}
		this.autoGenerated = autoGenerated;
	}
	
	public Column<T, O> autoGenerated() {
		setAutoGenerated(true);
		return this;
	}
	
	/**
	 * Implementation based on absolute name comparison. Done for Collections comparison.
	 *
	 * @param o un Object
	 * @return true if absolute name of both Column (this and o) are the same ignoring case.
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		
		Column column = (Column) o;
		return getAbsoluteName().equalsIgnoreCase(column.getAbsoluteName());
	}
	
	@Override
	public int hashCode() {
		return getAbsoluteName().toUpperCase().hashCode();
	}
	
	/**
	 * Overriden only for simple print (debug)
	 */
	@Override
	public String toString() {
		return getAbsoluteName();
	}
}
