package org.gama.stalactite.persistence.engine.runtime;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.gama.lang.Duo;
import org.gama.lang.Retryer;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Maps;
import org.gama.lang.collection.PairIterator;
import org.gama.reflection.AccessorByField;
import org.gama.reflection.Accessors;
import org.gama.stalactite.persistence.engine.RowCountManager;
import org.gama.stalactite.persistence.engine.StaleObjectExcepion;
import org.gama.stalactite.persistence.engine.listening.UpdateListener;
import org.gama.stalactite.persistence.engine.listening.UpdateListener.UpdatePayload;
import org.gama.stalactite.persistence.id.manager.AlreadyAssignedIdentifierManager;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.mapping.IMappingStrategy.UpwhereColumn;
import org.gama.stalactite.persistence.sql.IConnectionConfiguration.ConnectionConfigurationSupport;
import org.gama.stalactite.persistence.sql.dml.DMLGenerator;
import org.gama.stalactite.persistence.sql.dml.binder.ColumnBinderRegistry;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.sql.ConnectionProvider;
import org.gama.stalactite.sql.dml.SQLOperation.SQLOperationListener;
import org.gama.stalactite.sql.dml.SQLStatement;
import org.gama.stalactite.test.PairSetList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import static org.gama.lang.collection.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author Guillaume Mary
 */
public class UpdateExecutorTest extends AbstractDMLExecutorTest {
	
	private DataSet dataSet;
	
	private UpdateExecutor<Toto, Integer, Table> testInstance;
	
	@BeforeEach
	public void setUp() throws SQLException {
		dataSet = new DataSet();
		DMLGenerator dmlGenerator = new DMLGenerator(dataSet.dialect.getColumnBinderRegistry(), new DMLGenerator.CaseSensitiveSorter());
		testInstance = new UpdateExecutor<>(dataSet.persistenceConfiguration.classMappingStrategy,
				new ConnectionConfigurationSupport(dataSet.transactionManager, 3), dmlGenerator, Retryer.NO_RETRY, 3);
	}
	
	@Test
	public void testUpdateById() throws Exception {
		testInstance.updateById(asList(new Toto(1, 17, 23), new Toto(2, 29, 31), new Toto(3, 37, 41), new Toto(4, 43, 53)));
		
		verify(dataSet.preparedStatement, times(4)).addBatch();
		verify(dataSet.preparedStatement, times(2)).executeBatch();
		verify(dataSet.preparedStatement, times(12)).setInt(dataSet.indexCaptor.capture(), dataSet.valueCaptor.capture());
		assertEquals("update Toto set b = ?, c = ? where a = ?", dataSet.statementArgCaptor.getValue());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.newRow(1, 17).add(2, 23).add(3, 1)
				.newRow(1, 29).add(2, 31).add(3, 2)
				.newRow(1, 37).add(2, 41).add(3, 3)
				.newRow(1, 43).add(2, 53).add(3, 4);
		assertCapturedPairsEqual(dataSet, expectedPairs);
	}
	
	@Test
	public void update_mandatoryColumn() {
		Column<Table, Object> bColumn = (Column<Table, Object>) testInstance.getMappingStrategy().getTargetTable().mapColumnsOnName().get("b");
		bColumn.setNullable(false);
		
		List<Duo<Toto, Toto>> differencesIterable = asList(new Duo<>(new Toto(1, null, 23), new Toto(1, 42, 23)));
		Iterable<UpdatePayload<Toto, Table>> payloads = UpdateListener.computePayloads(differencesIterable, true,
				testInstance.getMappingStrategy());
		
		RuntimeException thrownException = assertThrows(IllegalArgumentException.class, () -> testInstance.updateMappedColumns(payloads));
		assertEquals("Expected non null value for column Toto.b on instance Toto{a=1, b=null, c=23}", thrownException.getMessage());
	}
	
	@Test
	public void listenerIsCalled() {
		SQLOperationListener<UpwhereColumn<Table>> listenerMock = mock(SQLOperationListener.class);
		testInstance.setOperationListener(listenerMock);
		
		ArgumentCaptor<Map<UpwhereColumn<Table>, ?>> statementArgCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<SQLStatement<UpwhereColumn<Table>>> sqlArgCaptor = ArgumentCaptor.forClass(SQLStatement.class);
		
		try {
			testInstance.updateById(Arrays.asList(new Toto(1, 17, 23), new Toto(2, 29, 31)));
		} catch (StaleObjectExcepion e) {
			// we don't care about any existing data in the database, listener must be called, so we continue whenever there are some stale objects
		}
		
		Table mappedTable = new Table("Toto");
		Column colA = mappedTable.addColumn("a", Integer.class);
		Column colB = mappedTable.addColumn("b", Integer.class);
		Column colC = mappedTable.addColumn("c", Integer.class);
		verify(listenerMock, times(2)).onValuesSet(statementArgCaptor.capture());
		assertEquals(Arrays.asList(
				Maps.asHashMap(new UpwhereColumn<>(colA, false), 1).add(new UpwhereColumn<>(colB, true), 17).add(new UpwhereColumn<>(colC, true), 23),
				Maps.asHashMap(new UpwhereColumn<>(colA, false), 2).add(new UpwhereColumn<>(colB, true), 29).add(new UpwhereColumn<>(colC, true), 31)
		), statementArgCaptor.getAllValues());
		verify(listenerMock, times(1)).onExecute(sqlArgCaptor.capture());
		assertEquals("update Toto set b = ?, c = ? where a = ?", sqlArgCaptor.getValue().getSQL());
	}
	
	@Test
	public <T extends Table<T>> void testUpdateById_noColumnToUpdate() {
		T table = (T) new Table<>("SimpleEntity");
		Column<?, Long> id = table.addColumn("id", long.class).primaryKey();
		AccessorByField<SimpleEntity, Long> idAccessor = Accessors.accessorByField(SimpleEntity.class, "id");
		ClassMappingStrategy<SimpleEntity, Long, T> simpleEntityPersistenceMapping = new ClassMappingStrategy<SimpleEntity, Long, T>
				(SimpleEntity.class, table, (Map) Maps.asMap(idAccessor, id), idAccessor, new AlreadyAssignedIdentifierManager<>(long.class, c -> {}, c -> true));
		UpdateExecutor<SimpleEntity, Long, T> testInstance = new UpdateExecutor<SimpleEntity, Long, T>(
				simpleEntityPersistenceMapping, new ConnectionConfigurationSupport(mock(ConnectionProvider.class), 4), new DMLGenerator(new ColumnBinderRegistry()), Retryer.NO_RETRY, 4);
		
		assertEquals(0, testInstance.updateById(Arrays.asList(new SimpleEntity())));
	}
	
	public static Object[][] testUpdate_diff_data() {
		return new Object[][] {
				// extreme case: no differences
				new Object[] { asList(new Toto(1, 1, 1), new Toto(2, 2, 2)),
						asList(new Toto(1, 1, 1), new Toto(2, 2, 2)),
						new ExpectedResult_TestUpdate_diff(0, 0, 0, Collections.EMPTY_LIST, new PairSetList<>()) },
				// case: always the same kind of modification: only "b" field
				new Object[] { asList(new Toto(1, 1, 1), new Toto(2, 2, 2), new Toto(3, 3, 3)),
						asList(new Toto(1, 2, 1), new Toto(2, 3, 2), new Toto(3, 4, 3)),
						new ExpectedResult_TestUpdate_diff(3, 1, 6,
								asList("update Toto set b = ? where a = ?"),
								new PairSetList<Integer, Integer>()
										.newRow(1, 2).add(2, 1)
										.add(1, 3).add(2, 2)
										.add(1, 4).add(2, 3)) },
				// case: always the same kind of modification: only "b" field, but batch should be called twice
				new Object[] { asList(new Toto(1, 1, 1), new Toto(2, 2, 2), new Toto(3, 3, 3), new Toto(4, 4, 4)),
						asList(new Toto(1, 2, 1), new Toto(2, 3, 2), new Toto(3, 4, 3), new Toto(4, 5, 4)),
						new ExpectedResult_TestUpdate_diff(4, 2, 8,
								asList("update Toto set b = ? where a = ?"),
								new PairSetList<Integer, Integer>()
										.newRow(1, 2).add(2, 1)
										.add(1, 3).add(2, 2)
										.add(1, 4).add(2, 3)
										.add(1, 5).add(2, 4)) },
				// case: always the same kind of modification: "b" + "c" fields
				new Object[] { asList(new Toto(1, 1, 1), new Toto(2, 2, 2), new Toto(3, 3, 3), new Toto(4, 4, 4)),
						asList(new Toto(1, 11, 11), new Toto(2, 22, 22), new Toto(3, 33, 33), new Toto(4, 44, 44)),
						new ExpectedResult_TestUpdate_diff(4, 2, 12,
								asList("update Toto set b = ?, c = ? where a = ?"),
								new PairSetList<Integer, Integer>()
										.newRow(1, 11).add(2, 11).add(3, 1)
										.add(1, 22).add(2, 22).add(3, 2)
										.add(1, 33).add(2, 33).add(3, 3)
										.add(1, 44).add(2, 44).add(3, 4)) },
				// more complex case: mix of modification sort, with batch updates
				new Object[] { asList(
						new Toto(1, 1, 1), new Toto(2, 2, 2), new Toto(3, 3, 3),
						new Toto(4, 4, 4), new Toto(5, 5, 5), new Toto(6, 6, 6), new Toto(7, 7, 7)),
						asList(
								new Toto(1, 11, 1), new Toto(2, 22, 2), new Toto(3, 33, 3),
								new Toto(4, 44, 444), new Toto(5, 55, 555), new Toto(6, 66, 666), new Toto(7, 7, 7)),
						new ExpectedResult_TestUpdate_diff(6, 2, 15,
								asList("update Toto set b = ? where a = ?", "update Toto set b = ?, c = ? where a = ?"),
								new PairSetList<Integer, Integer>()
										.newRow(1, 11).add(2, 1)
										.add(1, 22).add(2, 2)
										.add(1, 33).add(2, 3)
										.add(1, 44).add(2, 444).add(3, 4)
										.add(1, 55).add(2, 555).add(3, 5)
										.add(1, 66).add(2, 666).add(3, 6)) }
		};
	}
	
	private static class ExpectedResult_TestUpdate_diff {
		private final int addBatchCallCount;
		private final int executeBatchCallCount;
		private final int setIntCallCount;
		private final List<String> updateStatements;
		private final PairSetList<Integer, Integer> statementValues;
		
		public ExpectedResult_TestUpdate_diff(int addBatchCallCount, int executeBatchCallCount, int setIntCallCount, List<String> updateStatements, PairSetList<Integer, Integer> statementValues) {
			this.addBatchCallCount = addBatchCallCount;
			this.executeBatchCallCount = executeBatchCallCount;
			this.setIntCallCount = setIntCallCount;
			this.updateStatements = updateStatements;
			this.statementValues = statementValues;
		}
	}
	
	@ParameterizedTest
	@MethodSource("testUpdate_diff_data")
	public void testUpdate_diff(List<Toto> originalInstances, List<Toto> modifiedInstances, ExpectedResult_TestUpdate_diff expectedResult) throws Exception {
		testInstance.setRowCountManager(RowCountManager.NOOP_ROW_COUNT_MANAGER);
		// variable introduced to bypass generics problem
		UpdateExecutor<Toto, Integer, Table> localTestInstance = testInstance;
		localTestInstance.updateVariousColumns(UpdateListener.computePayloads(() -> new PairIterator<>(modifiedInstances, originalInstances),
				false, localTestInstance.getMappingStrategy()));
		
		verify(dataSet.preparedStatement, times(expectedResult.addBatchCallCount)).addBatch();
		verify(dataSet.preparedStatement, times(expectedResult.executeBatchCallCount)).executeBatch();
		verify(dataSet.preparedStatement, times(expectedResult.setIntCallCount)).setInt(dataSet.indexCaptor.capture(), dataSet.valueCaptor.capture());
		assertEquals(expectedResult.updateStatements, dataSet.statementArgCaptor.getAllValues());
		assertCapturedPairsEqual(dataSet, expectedResult.statementValues);
	}
	
	@Test
	public void testUpdate_diff_allColumns() throws Exception {
		List<Toto> originalInstances = asList(
				new Toto(1, 17, 23), new Toto(2, 29, 31), new Toto(3, 37, 41),
				new Toto(4, 43, 53), new Toto(5, -1, -2));
		List<Toto> modifiedInstances = asList(
				new Toto(1, 17, 123), new Toto(2, 129, 31), new Toto(3, 137, 141),
				new Toto(4, 143, 153), new Toto(5, -1, -2));
		testInstance.setRowCountManager(RowCountManager.NOOP_ROW_COUNT_MANAGER);
		// variable introduced to bypass generics problem
		UpdateExecutor<Toto, Integer, Table> localTestInstance = testInstance;
		localTestInstance.updateMappedColumns(UpdateListener.computePayloads(() -> new PairIterator<>(modifiedInstances, originalInstances),
				true, localTestInstance.getMappingStrategy()));
		
		verify(dataSet.preparedStatement, times(4)).addBatch();
		verify(dataSet.preparedStatement, times(2)).executeBatch();
		verify(dataSet.preparedStatement, times(12)).setInt(dataSet.indexCaptor.capture(), dataSet.valueCaptor.capture());
		assertEquals(asList("update Toto set b = ?, c = ? where a = ?"), dataSet.statementArgCaptor.getAllValues());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.newRow(1, 17).add(2, 123).add(3, 1)
				.newRow(1, 129).add(2, 31).add(3, 2)
				.newRow(1, 137).add(2, 141).add(3, 3)
				.newRow(1, 143).add(2, 153).add(3, 4);
		assertCapturedPairsEqual(dataSet, expectedPairs);
	}
	
	@Test
	public <T extends Table<T>> void testUpdate_noColumnToUpdate() {
		T table = (T) new Table<>("SimpleEntity");
		Column<?, Long> id = table.addColumn("id", long.class).primaryKey();
		AccessorByField<SimpleEntity, Long> idAccessor = Accessors.accessorByField(SimpleEntity.class, "id");
		ClassMappingStrategy<SimpleEntity, Long, T> simpleEntityPersistenceMapping = new ClassMappingStrategy<SimpleEntity, Long, T>
				(SimpleEntity.class, table, (Map) Maps.asMap(idAccessor, id), idAccessor, new AlreadyAssignedIdentifierManager<>(long.class, c -> {}, c -> false));
		UpdateExecutor<SimpleEntity, Long, T> testInstance = new UpdateExecutor<>(
				simpleEntityPersistenceMapping, new ConnectionConfigurationSupport(mock(ConnectionProvider.class), 4), new DMLGenerator(new ColumnBinderRegistry()), Retryer.NO_RETRY, 4);
		
		
		Iterable<UpdatePayload<SimpleEntity, T>> updatePayloads = UpdateListener.computePayloads(Arrays.asList(new Duo<>(new SimpleEntity(), 
				new SimpleEntity())), true, testInstance.getMappingStrategy());
		assertEquals(0, testInstance.updateMappedColumns(updatePayloads));
	}
	
	private static class SimpleEntity {
		
		private long id;
		
		private SimpleEntity() {
		}
		
	}
}