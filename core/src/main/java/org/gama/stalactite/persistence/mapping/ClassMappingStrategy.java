package org.gama.stalactite.persistence.mapping;

import org.gama.lang.Reflections;
import org.gama.reflection.PropertyAccessor;
import org.gama.stalactite.persistence.id.IdentifierGenerator;
import org.gama.stalactite.persistence.sql.result.Row;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.persistence.structure.Table.Column;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author Guillaume Mary
 */
public class ClassMappingStrategy<T> implements IMappingStrategy<T> {
	
	private Class<T> classToPersist;
	
	private FieldMappingStrategy<T> defaultMappingStrategy;
	
	private final Table targetTable;
	
	private final Set<Column> columns;
	
	private Map<PropertyAccessor, IEmbeddedBeanMapper> mappingStrategies;
	
	private final IdentifierGenerator identifierGenerator;
	
	public ClassMappingStrategy(@Nonnull Class<T> classToPersist, @Nonnull Table targetTable,
								Map<Field, Column> fieldToColumn, Field identifierField, IdentifierGenerator identifierGenerator) {
		this.classToPersist = classToPersist;
		this.targetTable = targetTable;
		this.defaultMappingStrategy = new FieldMappingStrategy<>(fieldToColumn, identifierField);
		this.columns = new HashSet<>(defaultMappingStrategy.getColumns());
		this.mappingStrategies = new HashMap<>();
		this.identifierGenerator = identifierGenerator;
	}
	
	public Class<T> getClassToPersist() {
		return classToPersist;
	}
	
	@Override
	public Table getTargetTable() {
		return targetTable;
	}
	
	@Override
	public Set<Column> getColumns() {
		return columns;
	}
	
	/**
	 * Indique une stratégie spécifique pour un attribut donné
	 * @param field
	 * @param mappingStrategy
	 */
	public void put(Field field, IEmbeddedBeanMapper mappingStrategy) {
		mappingStrategies.put(PropertyAccessor.forProperty(field), mappingStrategy);
		Reflections.ensureAccessible(field);
		// update columns list
		columns.addAll(mappingStrategy.getColumns());
	}
	
	public IdentifierGenerator getIdentifierGenerator() {
		return identifierGenerator;
	}
	
	@Override
	public StatementValues getInsertValues(@Nonnull T t) {
		StatementValues insertValues = defaultMappingStrategy.getInsertValues(t);
		for (Entry<PropertyAccessor, IEmbeddedBeanMapper> fieldStrategyEntry : mappingStrategies.entrySet()) {
			Object fieldValue = fieldStrategyEntry.getKey().get(t);
			StatementValues fieldInsertValues = fieldStrategyEntry.getValue().getInsertValues(fieldValue);
			insertValues.getUpsertValues().putAll(fieldInsertValues.getUpsertValues());
		}
		return insertValues;
	}
	
	@Override
	public StatementValues getUpdateValues(@Nonnull T modified, T unmodified, boolean allColumns) {
		StatementValues toReturn = defaultMappingStrategy.getUpdateValues(modified, unmodified, allColumns);
		for (Entry<PropertyAccessor, IEmbeddedBeanMapper> fieldStrategyEntry : mappingStrategies.entrySet()) {
			PropertyAccessor field = fieldStrategyEntry.getKey();
			Object modifiedValue = field.get(modified);
			Object unmodifiedValue = unmodified == null ?  null : field.get(unmodified);
			StatementValues fieldUpdateValues = fieldStrategyEntry.getValue().getUpdateValues(modifiedValue, unmodifiedValue, allColumns);
			toReturn.getUpsertValues().putAll(fieldUpdateValues.getUpsertValues());
		}
		if (allColumns && !toReturn.getUpsertValues().isEmpty()) {
			Set<Column> missingColumns = buildUpdatableColumns();
			missingColumns.removeAll(toReturn.getUpsertValues().keySet());
			for (Column missingColumn : missingColumns) {
				toReturn.putUpsertValue(missingColumn, null);
			}
		}
		return toReturn;
	}
	
	/**
	 * Gives columns that can be updated: columns minus keys
	 * @return columns aff all mapping strategies without getKeys()
	 */
	public Set<Column> buildUpdatableColumns() {
		Set<Column> missingColumns = new LinkedHashSet<>(getColumns());
		for (IEmbeddedBeanMapper<?> iEmbeddedBeanMapper : mappingStrategies.values()) {
			missingColumns.addAll(iEmbeddedBeanMapper.getColumns());
		}
		// keys are never updated
		for (Column column : getKeys()) {
			missingColumns.remove(column);
		}
		return missingColumns;
	}
	
	@Override
	public StatementValues getDeleteValues(@Nonnull T t) {
		return defaultMappingStrategy.getDeleteValues(t);
	}
	
	@Override
	public StatementValues getSelectValues(@Nonnull Serializable id) {
		return defaultMappingStrategy.getSelectValues(id);
	}
	
	@Override
	public StatementValues getVersionedKeyValues(@Nonnull T t) {
		return defaultMappingStrategy.getVersionedKeyValues(t);
	}
	
	public Iterable<Column> getVersionedKeys() {
		return defaultMappingStrategy.getVersionedKeys();
	}
	
	public Iterable<Column> getKeys() {
		return defaultMappingStrategy.getKeys();
	}
	
	public boolean isSingleColumnKey() {
		return defaultMappingStrategy.isSingleColumnKey();
	}
	
	public Column getSingleColumnKey() {
		return defaultMappingStrategy.getSingleColumnKey();
	}
	
	@Override
	public Serializable getId(T t) {
		return defaultMappingStrategy.getId(t);
	}
	
	/**
	 * Fix object id.
	 * 
	 * @param t a persistent bean 
	 * @param identifier the bean identifier, generated by IdentifierGenerator
	 */
	@Override
	public void setId(T t, Serializable identifier) {
		defaultMappingStrategy.setId(t, identifier);
	}
	
	@Override
	public T transform(Row row) {
		T toReturn = defaultMappingStrategy.transform(row);
		for (Entry<PropertyAccessor, IEmbeddedBeanMapper> mappingStrategyEntry : mappingStrategies.entrySet()) {
			mappingStrategyEntry.getKey().set(toReturn, mappingStrategyEntry.getValue().transform(row));
		}
		return toReturn;
	}
}
