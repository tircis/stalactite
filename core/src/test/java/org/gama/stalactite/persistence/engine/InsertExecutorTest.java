package org.gama.stalactite.persistence.engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gama.lang.Retryer;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.Maps;
import org.gama.reflection.IReversibleAccessor;
import org.gama.reflection.PropertyAccessor;
import org.gama.sql.ConnectionProvider;
import org.gama.sql.TransactionObserverConnectionProvider;
import org.gama.sql.dml.SQLOperation.SQLOperationListener;
import org.gama.sql.dml.SQLStatement;
import org.gama.stalactite.persistence.engine.AbstractVersioningStrategy.VersioningStrategySupport;
import org.gama.stalactite.persistence.engine.InsertExecutorITTest_autoGeneratedKeys.GeneratedKeysReaderAsInt;
import org.gama.stalactite.persistence.id.manager.AlreadyAssignedIdentifierManager;
import org.gama.stalactite.persistence.id.manager.JDBCGeneratedKeysIdentifierManager;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.mapping.SinglePropertyIdAccessor;
import org.gama.stalactite.persistence.sql.dml.DMLGenerator;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.test.PairSetList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Guillaume Mary
 */
public class InsertExecutorTest extends AbstractDMLExecutorTest {
	
	private DataSet dataSet;
	
	private InsertExecutor<Toto, Integer, Table> testInstance;
	
	@BeforeEach
	public void setUp() throws SQLException {
		dataSet = new DataSet();
		DMLGenerator dmlGenerator = new DMLGenerator(dataSet.dialect.getColumnBinderRegistry(), new DMLGenerator.CaseSensitiveSorter());
		testInstance = new InsertExecutor<>(dataSet.persistenceConfiguration.classMappingStrategy, dataSet.transactionManager, dmlGenerator, Retryer.NO_RETRY, 3, 3);
	}
	
	@Test
	public void testInsert_simple() throws Exception {
		testInstance.insert(Arrays.asList(new Toto(17, 23), new Toto(29, 31), new Toto(37, 41), new Toto(43, 53)));
		
		verify(dataSet.preparedStatement, times(4)).addBatch();
		verify(dataSet.preparedStatement, times(2)).executeBatch();
		verify(dataSet.preparedStatement, times(12)).setInt(dataSet.indexCaptor.capture(), dataSet.valueCaptor.capture());
		assertEquals("insert into Toto(a, b, c) values (?, ?, ?)", dataSet.statementArgCaptor.getValue());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.newRow(1, 1).add(2, 17).add(3, 23)
				.newRow(1, 2).add(2, 29).add(3, 31)
				.newRow(1, 3).add(2, 37).add(3, 41)
				.newRow(1, 4).add(2, 43).add(3, 53);
		assertCapturedPairsEqual(dataSet, expectedPairs);
	}
	
	@Test
	public void listenerIsCalled() {
		SQLOperationListener<Column<Table, Object>> listenerMock = mock(SQLOperationListener.class);
		testInstance.setOperationListener(listenerMock);
		
		ArgumentCaptor<Map<Column<Table, Object>, ?>> statementArgCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<SQLStatement<Column<Table, Object>>> sqlArgCaptor = ArgumentCaptor.forClass(SQLStatement.class);
		
		testInstance.insert(Arrays.asList(new Toto(17, 23), new Toto(29, 31)));
		
		
		Table mappedTable = new Table("Toto");
		Column colA = mappedTable.addColumn("a", Integer.class);
		Column colB = mappedTable.addColumn("b", Integer.class);
		Column colC = mappedTable.addColumn("c", Integer.class);
		verify(listenerMock, times(2)).onValuesSet(statementArgCaptor.capture());
		assertEquals(Arrays.asList(
				Maps.asHashMap(colA, 1).add(colB, 17).add(colC, 23),
				Maps.asHashMap(colA, 2).add(colB, 29).add(colC, 31)
				), statementArgCaptor.getAllValues());
		verify(listenerMock, times(1)).onExecute(sqlArgCaptor.capture());
		assertEquals("insert into Toto(a, b, c) values (?, ?, ?)", sqlArgCaptor.getValue().getSQL());
	}
	
	@Test
	public void withVersioningStrategy() throws SQLException {
		InsertExecutor<VersionnedToto, Integer, Table> testInstance;
		DMLGenerator dmlGenerator = new DMLGenerator(dataSet.dialect.getColumnBinderRegistry(), new DMLGenerator.CaseSensitiveSorter());
		
		PreparedStatement preparedStatement = mock(PreparedStatement.class);
		when(preparedStatement.executeBatch()).thenReturn(new int[] { 1 });
		Connection connection = mock(Connection.class);
		when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
		when(connection.prepareStatement(anyString(), anyInt())).thenReturn(preparedStatement);
		
		ConnectionProvider connectionProviderMock = mock(ConnectionProvider.class);
		when(connectionProviderMock.getCurrentConnection()).thenReturn(connection);
		
		ConnectionProvider connectionProvider = new TransactionObserverConnectionProvider(connectionProviderMock);
		
		Table totoTable = new Table("toto");
		Column pk = totoTable.addColumn("id", Integer.class).primaryKey();
		Column versionColumn = totoTable.addColumn("version", Long.class);
		Map<IReversibleAccessor, Column> mapping = Maps.asMap((IReversibleAccessor)
				PropertyAccessor.fromLambda(VersionnedToto::getVersion, VersionnedToto::setVersion), versionColumn)
				.add(PropertyAccessor.fromLambda(VersionnedToto::getA, VersionnedToto::setA), pk);
		testInstance = new InsertExecutor<>(new ClassMappingStrategy<VersionnedToto, Integer, Table>(VersionnedToto.class, totoTable, (Map) mapping,
				PropertyAccessor.fromLambda(VersionnedToto::getA, VersionnedToto::setA),
				new AlreadyAssignedIdentifierManager<>(Integer.class)),
				connectionProvider, dmlGenerator, Retryer.NO_RETRY, 3, 3);
		
		PropertyAccessor<VersionnedToto, Long> versioningAttributeAccessor = PropertyAccessor.fromLambda(VersionnedToto::getVersion, VersionnedToto::setVersion);
		testInstance.setVersioningStrategy(new VersioningStrategySupport<>(versioningAttributeAccessor, input -> ++input));
		
		VersionnedToto toto = new VersionnedToto(42, 17, 23);
		testInstance.insert(Arrays.asList(toto));
		assertEquals(1, toto.getVersion());
		
		// a rollback must revert sequence increment
		testInstance.getConnectionProvider().getCurrentConnection().rollback();
		assertEquals(0, toto.getVersion());
		
		// multiple rollbacks don't imply multiple sequence decrement
		testInstance.getConnectionProvider().getCurrentConnection().rollback();
		assertEquals(0, toto.getVersion());
	}
	
	public static class InsertExecutorTest_autoGenerateKeys extends AbstractDMLExecutorTest {
		
		// changing mapping strategy to add JDBCGeneratedKeysIdentifierManager and GeneratedKeysReader
		private static class DataSetInsertExecutorTest_withAutoGenerateKeys extends DataSet {
			
			protected DataSetInsertExecutorTest_withAutoGenerateKeys() throws SQLException {
				super();
			}
			
			@Override
			protected PersistenceConfigurationBuilder newPersistenceConfigurationBuilder() {
				return new PersistenceConfigurationBuilder<Toto, Integer, Table>()
						.withTableAndClass("Toto", Toto.class, (tableAndClass, primaryKeyField) -> {
							Set<Column<? extends Table, Object>> primaryKeyColumns = tableAndClass.targetTable.getPrimaryKey().getColumns();
							Column<? extends Table, Object> primaryKeyColumn = Iterables.first(primaryKeyColumns);
							primaryKeyColumn.setAutoGenerated(true);
							
							String primaryColumnName = primaryKeyColumn.getName();
							return new ClassMappingStrategy<Toto, Integer, Table>(
									tableAndClass.mappedClass,
									tableAndClass.targetTable,
									(Map) tableAndClass.persistentFieldHarverster.getFieldToColumn(),
									primaryKeyField,
									new JDBCGeneratedKeysIdentifierManager<>(
											new SinglePropertyIdAccessor<>(primaryKeyField),
											new GeneratedKeysReaderAsInt(primaryColumnName),
											primaryColumnName,
											Integer.class));
						})
						.withPrimaryKeyFieldName("a");
			}
		}
		
		@Test
		public void testInsert_generatedPK() throws Exception {
			DataSet dataSet = new DataSetInsertExecutorTest_withAutoGenerateKeys();
			// additional configuration for generated keys method capture
			when(dataSet.connection.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(dataSet.preparedStatement);
			
			// Implementing a ResultSet that gives results
			ResultSet generatedKeyResultSetMock = mock(ResultSet.class);
			when(dataSet.preparedStatement.getGeneratedKeys()).thenReturn(generatedKeyResultSetMock);
			// the ResultSet instance will be called for all batch operations so values returned must reflect that
			when(generatedKeyResultSetMock.next()).thenReturn(true, true, true, false, true, false);
			when(generatedKeyResultSetMock.getInt(eq(1))).thenReturn(1, 2, 3, 4);
			// getObject is for null value detection, so values are not really important
			when(generatedKeyResultSetMock.getObject(eq(1))).thenReturn(1, 2, 3, 4);
			
			// we rebind statement argument capture because by default it's bound to the "non-generating keys" preparedStatement(..) signature 
			when(dataSet.connection.prepareStatement(dataSet.statementArgCaptor.capture(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(dataSet.preparedStatement);
			
			
			DMLGenerator dmlGenerator = new DMLGenerator(dataSet.dialect.getColumnBinderRegistry(), new DMLGenerator.CaseSensitiveSorter());
			InsertExecutor<Toto, Integer, Table> testInstance = new InsertExecutor<>(dataSet.persistenceConfiguration.classMappingStrategy, dataSet.transactionManager, dmlGenerator, Retryer.NO_RETRY, 3, 3);
			List<Toto> totoList = Arrays.asList(new Toto(17, 23), new Toto(29, 31), new Toto(37, 41), new Toto(43, 53));
			testInstance.insert(totoList);
			
			verify(dataSet.preparedStatement, times(4)).addBatch();
			verify(dataSet.preparedStatement, times(2)).executeBatch();
			verify(dataSet.preparedStatement, times(8)).setInt(dataSet.indexCaptor.capture(), dataSet.valueCaptor.capture());
			assertEquals("insert into Toto(b, c) values (?, ?)", dataSet.statementArgCaptor.getValue());
			PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
					.newRow(1, 17).add(2, 23)
					.newRow(1, 29).add(2, 31)
					.newRow(1, 37).add(2, 41)
					.newRow(1, 43).add(2, 53);
			assertCapturedPairsEqual(dataSet, expectedPairs);
			
			verify(generatedKeyResultSetMock, times(6)).next();
			verify(generatedKeyResultSetMock, times(4)).getInt(eq(1));
			
			// Verfy that database generated keys were set into Java instances
			int i = 1;
			for (Toto toto : totoList) {
				assertEquals(i++, (int) toto.a);
			}
		}
	}
	
	protected static class VersionnedToto extends Toto {
		protected long version;
		
		public VersionnedToto(int a, int b, int c) {
			super(a, b, c);
		}
		
		public Integer getA() {
			return a;
		}
		
		public void setA(Integer a) {
			this.a = a;
		}
		
		public long getVersion() {
			return version;
		}
		
		public void setVersion(long version) {
			this.version = version;
		}
		
	}
}