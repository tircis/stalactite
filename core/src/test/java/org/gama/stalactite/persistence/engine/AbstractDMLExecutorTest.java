package org.gama.stalactite.persistence.engine;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.gama.reflection.PropertyAccessor;
import org.gama.stalactite.persistence.id.manager.BeforeInsertIdentifierManager;
import org.gama.stalactite.persistence.id.manager.BeforeInsertIdentifierManager.Sequence;
import org.gama.stalactite.persistence.id.manager.IdentifierInsertionManager;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.mapping.IdMappingStrategy;
import org.gama.stalactite.persistence.mapping.PersistentFieldHarverster;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.sql.ddl.JavaTypeToSqlTypeMapping;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.persistence.structure.Table.Column;
import org.gama.stalactite.test.JdbcConnectionProvider;
import org.gama.stalactite.test.PairSetList;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Guillaume Mary
 */
public abstract class AbstractDMLExecutorTest {
	
	protected static class DataSet {
		
		protected final Dialect dialect = new Dialect(new JavaTypeToSqlTypeMapping()
				.with(Integer.class, "int"));
		
		protected PersistenceConfiguration<Toto, Integer> persistenceConfiguration;
		
		protected PreparedStatement preparedStatement;
		protected ArgumentCaptor<Integer> valueCaptor;
		protected ArgumentCaptor<Integer> indexCaptor;
		protected ArgumentCaptor<String> statementArgCaptor;
		protected JdbcConnectionProvider transactionManager;
		protected Connection connection;
		
		protected DataSet() throws SQLException {
			PersistenceConfigurationBuilder persistenceConfigurationBuilder = newPersistenceConfigurationBuilder();
			persistenceConfiguration = persistenceConfigurationBuilder.build();
			
			preparedStatement = mock(PreparedStatement.class);
			when(preparedStatement.executeBatch()).thenReturn(new int[] { 1 });
			
			connection = mock(Connection.class);
			// PreparedStatement.getConnection() must gives that instance of connection because of SQLOperation that checks
			// weither or not it should prepare statement
			when(preparedStatement.getConnection()).thenReturn(connection);
			statementArgCaptor = ArgumentCaptor.forClass(String.class);
			when(connection.prepareStatement(statementArgCaptor.capture())).thenReturn(preparedStatement);
			when(connection.prepareStatement(statementArgCaptor.capture(), anyInt())).thenReturn(preparedStatement);
			
			valueCaptor = ArgumentCaptor.forClass(Integer.class);
			indexCaptor = ArgumentCaptor.forClass(Integer.class);
			
			DataSource dataSource = mock(DataSource.class);
			when(dataSource.getConnection()).thenReturn(connection);
			transactionManager = new JdbcConnectionProvider(dataSource);
		}
		
		protected PersistenceConfigurationBuilder newPersistenceConfigurationBuilder() {
			return new PersistenceConfigurationBuilder<Toto, Integer>()
					.withTableAndClass("Toto", Toto.class, (mappedClass, primaryKeyField) -> {
						Sequence<Integer> instance = InMemoryCounterIdentifierGenerator.INSTANCE;
						IdentifierInsertionManager<Toto, Integer> identifierGenerator = new BeforeInsertIdentifierManager<>(
								IdMappingStrategy.toIdAccessor(primaryKeyField), instance, Integer.class);
						return new ClassMappingStrategy<>(
								mappedClass.mappedClass,
								mappedClass.targetTable,
								mappedClass.persistentFieldHarverster.getFieldToColumn(),
								primaryKeyField,
								identifierGenerator);
					})
					.withPrimaryKeyFieldName("a");
		}
	}
	
	protected static class PersistenceConfiguration<T, I> {
		
		protected ClassMappingStrategy<T, I> classMappingStrategy;
		protected Table targetTable;
	}
	
	protected static class PersistenceConfigurationBuilder<T, I> {
		
		private Class<T> mappedClass;
		private BiFunction<TableAndClass<T>, PropertyAccessor<T, I>, ClassMappingStrategy<T, I>> classMappingStrategyBuilder;
		private String tableName;
		private String primaryKeyFieldName;
		
		public PersistenceConfigurationBuilder withTableAndClass(String tableName, Class<T> mappedClass,
						BiFunction<TableAndClass<T>, PropertyAccessor<T, I>, ClassMappingStrategy<T, I>> classMappingStrategyBuilder) {
			this.tableName = tableName;
			this.mappedClass = mappedClass;
			this.classMappingStrategyBuilder = classMappingStrategyBuilder;
			return this;
		}
		
		public PersistenceConfigurationBuilder withPrimaryKeyFieldName(String primaryKeyFieldName) {
			this.primaryKeyFieldName = primaryKeyFieldName;
			return this;
		}
		
		protected PersistenceConfiguration build() {
			PersistenceConfiguration toReturn = new PersistenceConfiguration();
			
			TableAndClass<T> tableAndClass = map(mappedClass, tableName);
			PropertyAccessor<T, I> primaryKeyField = PropertyAccessor.forProperty(tableAndClass.configurePrimaryKey(primaryKeyFieldName));
			
			toReturn.classMappingStrategy = classMappingStrategyBuilder.apply(tableAndClass, primaryKeyField);
			toReturn.targetTable = tableAndClass.targetTable;
			
			return toReturn;
		}
		
		protected TableAndClass<T> map(Class<T> mappedClass, String tableName) {
			Table targetTable = new Table(tableName);
			PersistentFieldHarverster persistentFieldHarverster = new PersistentFieldHarverster();
			Map<PropertyAccessor, Column> fieldsMapping = persistentFieldHarverster.mapFields(mappedClass, targetTable);
			return new TableAndClass<>(targetTable, mappedClass, persistentFieldHarverster);
		}
		
		protected static class TableAndClass<T> {
			
			protected final Table targetTable;
			protected final Class<T> mappedClass;
			protected final PersistentFieldHarverster persistentFieldHarverster;
			
			public TableAndClass(Table targetTable, Class<T> mappedClass, PersistentFieldHarverster persistentFieldHarverster) {
				this.targetTable = targetTable;
				this.mappedClass = mappedClass;
				this.persistentFieldHarverster = persistentFieldHarverster;
			}
			
			protected Field configurePrimaryKey(String primaryKeyFieldName) {
				Field primaryKeyField = persistentFieldHarverster.getField(primaryKeyFieldName);
				Column primaryKey = persistentFieldHarverster.getColumn(PropertyAccessor.forProperty(primaryKeyField));
				primaryKey.setPrimaryKey(true);
				return primaryKeyField;
			}
		}
		
	}
	
	public void assertCapturedPairsEqual(DataSet dataSet, PairSetList<Integer, Integer> expectedPairs) {
		List<Map.Entry<Integer, Integer>> obtainedPairs = PairSetList.toPairs(dataSet.indexCaptor.getAllValues(), dataSet.valueCaptor.getAllValues());
		List<Set<Map.Entry<Integer, Integer>>> obtained = new ArrayList<>();
		int startIndex = 0;
		for (Set<Map.Entry<Integer, Integer>> expectedPair : expectedPairs.asList()) {
			obtained.add(new HashSet<>(obtainedPairs.subList(startIndex, startIndex += expectedPair.size())));
		}
		assertEquals(expectedPairs.asList(), obtained);
	}
	
	/**
	 * Class to be persisted
	 */
	protected static class Toto {
		protected Integer a;
		protected Integer b;
		protected Integer c;
		
		public Toto() {
		}
		
		public Toto(Integer a, Integer b, Integer c) {
			this.a = a;
			this.b = b;
			this.c = c;
		}
		
		public Toto(Integer b, Integer c) {
			this.b = b;
			this.c = c;
		}
	}
	
}