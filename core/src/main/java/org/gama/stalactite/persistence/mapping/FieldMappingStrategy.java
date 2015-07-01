package org.gama.stalactite.persistence.mapping;

import org.gama.lang.Reflections;
import org.gama.lang.bean.Objects;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.Iterables.ForEach;
import org.gama.lang.exception.Exceptions;
import org.gama.reflection.PropertyAccessor;
import org.gama.stalactite.persistence.sql.result.Row;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.persistence.structure.Table.Column;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author mary
 */
public class FieldMappingStrategy<T> implements IMappingStrategy<T> {
	
	private final Class<T> classToPersist;
	
	private final Map<PropertyAccessor, Column> fieldToColumn;
	
	private PropertyAccessor<T, Serializable> identifierAccessor;
	
	private final Table targetTable;
	
	private final Set<Column> columns;
	
	private final ToBeanRowTransformer<T> rowTransformer;
	
	private final Iterable<Column> keys;
	
	private final boolean singleColumnKey;
	
	private final Iterable<Column> versionedKeys;
	
	/**
	 * Build a FieldMappingStrategy from a mapping between Field and Column.
	 * Fields are expected to be from same class.
	 * Columns are expected to be from same table.
	 * No control is done about that, caller must be aware of it.
	 * First entry of <tt>fieldToColumn</tt> is used to pick up persisted class and target table.
	 * 
	 * @param fieldToColumn a mapping between Field and Column, expected to be coherent (fields of same class, column of same table)
	 * @param identifierField the field that store entity identifier   
	 */
	public FieldMappingStrategy(@Nonnull Map<Field, Column> fieldToColumn, Field identifierField) {
		this.fieldToColumn = new LinkedHashMap<>(fieldToColumn.size());
		Map<String, PropertyAccessor> columnToField = new HashMap<>();
		for (Entry<Field, Column> fieldColumnEntry : fieldToColumn.entrySet()) {
			Column column = fieldColumnEntry.getValue();
			PropertyAccessor accessorByField = PropertyAccessor.forProperty(fieldColumnEntry.getKey().getDeclaringClass(), fieldColumnEntry.getKey().getName());
			this.fieldToColumn.put(accessorByField, column);
			columnToField.put(column.getName(), accessorByField);
			if (fieldColumnEntry.getKey().equals(identifierField)) {
				if (!column.isPrimaryKey()) {
					throw new UnsupportedOperationException("Field " + identifierField.getDeclaringClass().getName()+"."+identifierField.getName()
							+ " is declared as identifier but mapped column " + column.toString() + " is not the primary key of table");
				}
				this.identifierAccessor = accessorByField;
			}
		}
		Entry<Field, Column> firstEntry = Iterables.first(fieldToColumn);
		this.targetTable = firstEntry.getValue().getTable();
		if (this.identifierAccessor == null) {
			throw new UnsupportedOperationException("No primary key field for " + targetTable.getName());
		}
		this.classToPersist = (Class<T>) identifierField.getDeclaringClass();
		this.rowTransformer = new ToBeanRowTransformer<>(Reflections.getDefaultConstructor(classToPersist), columnToField);
		this.columns = new LinkedHashSet<>(fieldToColumn.values());
		// TODO: distinguish key from version, implement id with multiple column/field
		this.keys = Collections.unmodifiableSet(Arrays.asSet(targetTable.getPrimaryKey()));
		this.singleColumnKey = true;
		this.versionedKeys = Collections.unmodifiableSet(Arrays.asSet(targetTable.getPrimaryKey()));
	}
	
	@Override
	public Table getTargetTable() {
		return targetTable;
	}
	
	@Override
	public Set<Column> getColumns() {
		return columns;
	}
	
	@Override
	public StatementValues getInsertValues(@Nonnull final T t) {
		return foreachField(new FieldVisitor() {
			@Override
			protected void visitField(Entry<PropertyAccessor, Column> fieldColumnEntry) throws IllegalAccessException {
				toReturn.putUpsertValue(fieldColumnEntry.getValue(), fieldColumnEntry.getKey().get(t));
			}
		}, true);	// primary key must be inserted (not if DB gives it but it's not implemented yet)
	}
	
	@Override
	public StatementValues getUpdateValues(@Nonnull final T modified, final T unmodified, final boolean allColumns) {
		final Map<Column, Object> unmodifiedColumns = new LinkedHashMap<>();
		// getting differences
		StatementValues toReturn = foreachField(new FieldVisitor() {
			@Override
			protected void visitField(Entry<PropertyAccessor, Column> fieldColumnEntry) throws IllegalAccessException {
				PropertyAccessor<T, Object> field = fieldColumnEntry.getKey();
				Object modifiedValue = field.get(modified);
				Object unmodifiedValue = unmodified == null ? null : field.get(unmodified);
				Column fieldColumn = fieldColumnEntry.getValue();
				if (!Objects.equalsWithNull(modifiedValue, unmodifiedValue)) {
					toReturn.putUpsertValue(fieldColumn, modifiedValue);
				} else {
					unmodifiedColumns.put(fieldColumn, modifiedValue);
				}
			}
		}, false);	// primary key mustn't be updated
		
		// adding complementary columns if necessary
		if (allColumns && !toReturn.getUpsertValues().isEmpty()) {
			for (Entry<Column, Object> unmodifiedField : unmodifiedColumns.entrySet()) {
				toReturn.putUpsertValue(unmodifiedField.getKey(), unmodifiedField.getValue());
			}
		}
		
		putVersionedKeyValues(modified, toReturn);
		return toReturn;
	}
	
	@Override
	public StatementValues getDeleteValues(@Nonnull T t) {
		StatementValues toReturn = new StatementValues();
		putVersionedKeyValues(t, toReturn);
		return toReturn;
	}
	
	@Override
	public StatementValues getSelectValues(@Nonnull Serializable id) {
		StatementValues toReturn = new StatementValues();
		toReturn.putWhereValue(this.targetTable.getPrimaryKey(), id);
		return toReturn;
	}
	
	@Override
	public StatementValues getVersionedKeyValues(@Nonnull T t) {
		StatementValues toReturn = new StatementValues();
		putVersionedKeyValues(t, toReturn);
		return toReturn;
	}
	
	public Iterable<Column> getVersionedKeys() {
		return versionedKeys;
	}
	
	public Iterable<Column> getKeys() {
		return keys;
	}
	
	public boolean isSingleColumnKey() {
		return singleColumnKey;
	}
	
	public Column getSingleColumnKey() {
		if (!singleColumnKey) {
			throw new UnsupportedOperationException("Can't give only when key when several exist");
		}
		return Iterables.first(keys);
	}
	
	@Override
	public Serializable getId(T t) {
		return identifierAccessor.get(t);
	}
	
	@Override
	public void setId(T t, Serializable identifier) {
		identifierAccessor.set(t, identifier);
	}
	
	private StatementValues foreachField(final FieldVisitor visitor, boolean withPK) {
		Map<PropertyAccessor, Column> fieldsTobeVisited = new LinkedHashMap<>(this.fieldToColumn);
		if (!withPK) {
			fieldsTobeVisited.remove(this.identifierAccessor);
		}
		Iterables.visit(fieldsTobeVisited.entrySet(), visitor);
		return visitor.toReturn;
	}
	
	protected void putVersionedKeyValues(T t, StatementValues toReturn) {
		toReturn.putWhereValue(this.targetTable.getPrimaryKey(), getId(t));
	}
	
	@Override
	public T transform(Row row) {
		return this.rowTransformer.transform(row);
	}
	
	private static abstract class FieldVisitor extends ForEach<Entry<PropertyAccessor, Column>, Void> {
		
		protected StatementValues toReturn = new StatementValues();
		
		@Override
		public final Void visit(Entry<PropertyAccessor, Column> fieldColumnEntry) {
			try {
				visitField(fieldColumnEntry);
			} catch (IllegalAccessException e) {
				Exceptions.throwAsRuntimeException(e);
			}
			return null;
		}
		
		protected abstract void visitField(Entry<PropertyAccessor, Column> fieldColumnEntry) throws IllegalAccessException;
	}
}
