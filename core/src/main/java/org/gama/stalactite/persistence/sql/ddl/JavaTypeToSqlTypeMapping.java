package org.gama.stalactite.persistence.sql.ddl;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.ValueFactoryHashMap;
import org.gama.stalactite.persistence.structure.Table.Column;
import org.gama.stalactite.persistence.structure.Table.SizedColumn;

/**
 * Mapping between Java classes and Sql Types.
 * Near Hibernate Dialect::register types principles, so no SQL types are defined here : it's only a storage and finding tool.
 * 
 * @see #getTypeName(Class)
 * @author Guillaume Mary
 */
public class JavaTypeToSqlTypeMapping {
	
	/**
	 * SQL types storage per Java type, dedicated to sized-types.
	 * Values are SortedMaps of size to SQL type. SortedMap are used to ease finding of types per size
	 */
	private final Map<Class, SortedMap<Integer, String>> javaTypeToSQLType = new ValueFactoryHashMap<>(input -> new TreeMap<>());
	
	/**
	 * SQL types storage per Java type, usual cases.
	 */
	private final Map<Class, String> defaultJavaTypeToSQLType = new HashMap<>();
	
	public void put(Class clazz, String sqlType) {
		defaultJavaTypeToSQLType.put(clazz, sqlType);
	}
	
	public void put(Class clazz, int size, String sqlType) {
		javaTypeToSQLType.get(clazz).put(size, sqlType);
	}
	
	/**
	 * Gives the SQL type name of a column. Main entry point of this class.
	 *
	 * @param column a column
	 * @return the SQL type for the given column
	 */
	public String getTypeName(Column column) {
		Class javaType = column.getJavaType();
		if (javaType == null) {
			throw new IllegalArgumentException("Can't give sql type for column " + column.getAbsoluteName() + " because its type is null");
		}
		if (column instanceof SizedColumn) {
			int size = ((SizedColumn) column).getSize();
			return getTypeName(javaType, size);
		} else {
			return getTypeName(javaType);
		}
	}
	
	/**
	 * Gives the SQL type of a Java class
	 *
	 * @param javaType a Java class
	 * @return the SQL type for the given column
	 */
	private String getTypeName(Class javaType) {
		String type = defaultJavaTypeToSQLType.get(javaType);
		if (type == null) {
			throw new IllegalArgumentException("No sql type defined for " + javaType);
		}
		return type;
	}
	
	/**
	 * Gives the learest SQL type of a Java class according to the expected size
	 *
	 * @param javaType a Java class
	 * @return the SQL type for the given column
	 */
	String getTypeName(Class javaType, Integer size) {
		if (size == null) {
			return getTypeName(javaType);
		} else {
			SortedMap<Integer, String> typeNames = javaTypeToSQLType.get(javaType).tailMap(size);
			String typeName = Iterables.firstValue(typeNames);
			if (typeName != null) {
				// NB: we use $l as Hibernate to ease an eventual switch between framworks
				typeName = typeName.replace("$l", String.valueOf(size));
			} else {
				typeName = getTypeName(javaType);
			}
			return typeName;
		}
	}
}
