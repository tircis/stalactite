package org.gama.stalactite.persistence.engine.cascade;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gama.lang.Reflections;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Maps;
import org.gama.reflection.Accessors;
import org.gama.reflection.PropertyAccessor;
import org.gama.sql.binder.ParameterBinder;
import org.gama.stalactite.persistence.engine.InMemoryCounterIdentifierGenerator;
import org.gama.stalactite.persistence.engine.Persister;
import org.gama.stalactite.persistence.engine.RowCountManager;
import org.gama.stalactite.persistence.engine.listening.NoopDeleteListener;
import org.gama.stalactite.persistence.engine.listening.NoopDeleteByIdListener;
import org.gama.stalactite.persistence.engine.listening.NoopInsertListener;
import org.gama.stalactite.persistence.engine.listening.NoopUpdateByIdListener;
import org.gama.stalactite.persistence.id.Identified;
import org.gama.stalactite.persistence.id.Identifier;
import org.gama.stalactite.persistence.id.PersistableIdentifier;
import org.gama.stalactite.persistence.id.PersistedIdentifier;
import org.gama.stalactite.persistence.id.manager.AlreadyAssignedIdentifierManager;
import org.gama.stalactite.persistence.id.manager.BeforeInsertIdentifierManager;
import org.gama.stalactite.persistence.id.manager.StatefullIdentifier;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.mapping.IdMappingStrategy;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.sql.ddl.JavaTypeToSqlTypeMapping;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.test.JdbcConnectionProvider;
import org.gama.stalactite.test.PairSetList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Guillaume Mary
 */
public class JoinedTablesPersisterTest {
	
	private JoinedTablesPersister<Toto, StatefullIdentifier<Integer>> testInstance;
	private PreparedStatement preparedStatement;
	private ArgumentCaptor<Integer> valueCaptor;
	private ArgumentCaptor<Integer> indexCaptor;
	private ArgumentCaptor<String> statementArgCaptor;
	private JdbcConnectionProvider transactionManager;
	private InMemoryCounterIdentifierGenerator identifierGenerator;
	private ClassMappingStrategy<Toto, StatefullIdentifier<Integer>> totoClassMappingStrategy_ontoTable1, totoClassMappingStrategy2_ontoTable2;
	private Dialect dialect;
	private Table totoClassTable1, totoClassTable2;
	private Column leftJoinColumn;
	private Column rightJoinColumn;
	private Persister<Toto, StatefullIdentifier<Integer>> persister2;
	
	@Before
	public void setUp() throws SQLException {
		initData();
		initTest();
	}
	
	protected void initData() throws SQLException {
		Field fieldId = Reflections.getField(Toto.class, "id");
		Field fieldA = Reflections.getField(Toto.class, "a");
		Field fieldB = Reflections.getField(Toto.class, "b");
		Field fieldX = Reflections.getField(Toto.class, "x");
		Field fieldY = Reflections.getField(Toto.class, "y");
		Field fieldZ = Reflections.getField(Toto.class, "z");
		
		totoClassTable1 = new Table("Toto1");
		leftJoinColumn = totoClassTable1.addColumn("id", fieldId.getType());
		totoClassTable1.addColumn("a", fieldA.getType());
		totoClassTable1.addColumn("b", fieldB.getType());
		Map<String, Column> columnMap1 = totoClassTable1.mapColumnsOnName();
		columnMap1.get("id").setPrimaryKey(true);
		
		totoClassTable2 = new Table("Toto2");
		rightJoinColumn = totoClassTable2.addColumn("id", fieldId.getType());
		totoClassTable2.addColumn("x", fieldX.getType());
		totoClassTable2.addColumn("y", fieldY.getType());
		totoClassTable2.addColumn("z", fieldZ.getType());
		Map<String, Column> columnMap2 = totoClassTable2.mapColumnsOnName();
		columnMap2.get("id").setPrimaryKey(true);
		
		
		PropertyAccessor<Toto, StatefullIdentifier<Integer>> identifierAccessor = Accessors.forProperty(fieldId);
		Map<PropertyAccessor, Column> totoClassMapping1 = Maps.asMap(
				(PropertyAccessor) identifierAccessor, columnMap1.get("id"))
				.add(Accessors.forProperty(fieldA), columnMap1.get("a"))
				.add(Accessors.forProperty(fieldB), columnMap1.get("b"));
		Map<PropertyAccessor, Column> totoClassMapping2 = Maps.asMap(
				(PropertyAccessor) identifierAccessor, columnMap2.get("id"))
				.add(Accessors.forProperty(fieldX), columnMap2.get("x"))
				.add(Accessors.forProperty(fieldY), columnMap2.get("y"))
				.add(Accessors.forProperty(fieldZ), columnMap2.get("z"));
		
		
		identifierGenerator = new InMemoryCounterIdentifierGenerator();
		
		BeforeInsertIdentifierManager<Toto, StatefullIdentifier<Integer>> beforeInsertIdentifierManager = new BeforeInsertIdentifierManager<>(
				IdMappingStrategy.toIdAccessor(identifierAccessor),
				() -> new PersistableIdentifier<>(identifierGenerator.next()),
				(Class<StatefullIdentifier<Integer>>) (Class) StatefullIdentifier.class);
		totoClassMappingStrategy_ontoTable1 = new ClassMappingStrategy<>(Toto.class, totoClassTable1,
				totoClassMapping1, identifierAccessor, beforeInsertIdentifierManager);
		totoClassMappingStrategy2_ontoTable2 = new ClassMappingStrategy<>(Toto.class, totoClassTable2,
				totoClassMapping2, identifierAccessor, AlreadyAssignedIdentifierManager.INSTANCE);
		
		JavaTypeToSqlTypeMapping simpleTypeMapping = new JavaTypeToSqlTypeMapping();
		simpleTypeMapping.put(Identifier.class, "int");
		
		transactionManager = new JdbcConnectionProvider(null);
		dialect = new Dialect(simpleTypeMapping);
		dialect.setInOperatorMaxSize(3);
		dialect.getColumnBinderRegistry().register(Identifier.class, new ParameterBinder<Identifier>() {
			@Override
			public Identifier get(ResultSet resultSet, String columnName) throws SQLException {
				return new PersistedIdentifier<>(resultSet.getObject(columnName));
			}
			
			@Override
			public void set(PreparedStatement statement, int valueIndex, Identifier value) throws SQLException {
				statement.setInt(valueIndex, (Integer) value.getSurrogate());
			}
		});
	}
	
	protected void initTest() throws SQLException {
		// reset id counter between 2 tests to keep independency between them
		identifierGenerator.reset();
		
		preparedStatement = mock(PreparedStatement.class);
		when(preparedStatement.executeBatch()).thenReturn(new int[] { 1 });
		
		Connection connection = mock(Connection.class);
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
		transactionManager.setDataSource(dataSource);
		testInstance = new JoinedTablesPersister<>(totoClassMappingStrategy_ontoTable1, dialect, transactionManager, 3);
		// we add a copier onto a another table
		persister2 = new Persister<>(totoClassMappingStrategy2_ontoTable2, dialect, () -> connection, 3);
		testInstance.addPersister(JoinedStrategiesSelect.FIRST_STRATEGY_NAME, persister2,
				null, leftJoinColumn, rightJoinColumn, false);
		testInstance.getPersisterListener().addInsertListener(new NoopInsertListener<Toto>() {
			@Override
			public void afterInsert(Iterable<Toto> iterables) {
				// since we only want a replicate of totos in table2, we only need to return them
				persister2.insert(iterables);
			}
		});
		testInstance.getPersisterListener().addUpdateByIdListener(new NoopUpdateByIdListener<Toto>() {
			@Override
			public void afterUpdateById(Iterable<Toto> iterables) {
				// since we only want a replicate of totos in table2, we only need to return them
				persister2.updateById(iterables);
			}
		});
		testInstance.getPersisterListener().addDeleteListener(new NoopDeleteListener<Toto>() {
			@Override
			public void beforeDelete(Iterable<Toto> iterables) {
				// since we only want a replicate of totos in table2, we only need to return them
				persister2.delete(iterables);
			}
		});
		testInstance.getPersisterListener().addDeleteByIdListener(new NoopDeleteByIdListener<Toto>() {
			@Override
			public void beforeDeleteById(Iterable<Toto> iterables) {
				// since we only want a replicate of totos in table2, we only need to return them
				persister2.deleteById(iterables);
			}
		});
	}
	
	public void assertCapturedPairsEqual(PairSetList<Integer, Integer> expectedPairs) {
		List<Map.Entry<Integer, Integer>> obtainedPairs = PairSetList.toPairs(indexCaptor.getAllValues(), valueCaptor.getAllValues());
		List<Set<Map.Entry<Integer, Integer>>> obtained = new ArrayList<>();
		int startIndex = 0;
		for (Set<Map.Entry<Integer, Integer>> expectedPair : expectedPairs.asList()) {
			obtained.add(new HashSet<>(obtainedPairs.subList(startIndex, startIndex += expectedPair.size())));
		}
		assertEquals(expectedPairs.asList(), obtained);
	}
	
	@Test
	public void testInsert() throws Exception {
		testInstance.insert(Arrays.asList(
				new Toto(17, 23, 117, 123, -117),
				new Toto(29, 31, 129, 131, -129),
				new Toto(37, 41, 137, 141, -137),
				new Toto(43, 53, 143, 153, -143)
		));
		
		verify(preparedStatement, times(8)).addBatch();
		verify(preparedStatement, times(4)).executeBatch();
		verify(preparedStatement, times(28)).setInt(indexCaptor.capture(), valueCaptor.capture());
		assertEquals(Arrays.asList("insert into Toto1(id, a, b) values (?, ?, ?)", "insert into Toto2(id, x, y, z) values (?, ?, ?, ?)"),
				statementArgCaptor.getAllValues());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.of(1, 1).add(2, 17).add(3, 23)
				.of(1, 2).add(2, 29).add(3, 31)
				.of(1, 3).add(2, 37).add(3, 41)
				.of(1, 4).add(2, 43).add(3, 53)
				
				.of(1, 1).add(2, 117).add(3, 123).add(4, -117)
				.of(1, 2).add(2, 129).add(3, 131).add(4, -129)
				.of(1, 3).add(2, 137).add(3, 141).add(4, -137)
				.of(1, 4).add(2, 143).add(3, 153).add(4, -143);
		assertCapturedPairsEqual(expectedPairs);
	}
	
	@Test
	public void testUpdateById() throws Exception {
		testInstance.updateById(Arrays.asList(
				new Toto(1, 17, 23, 117, 123, -117),
				new Toto(2, 29, 31, 129, 131, -129),
				new Toto(3, 37, 41, 137, 141, -137),
				new Toto(4, 43, 53, 143, 153, -143)
		));
		
		verify(preparedStatement, times(8)).addBatch();
		verify(preparedStatement, times(4)).executeBatch();
		verify(preparedStatement, times(28)).setInt(indexCaptor.capture(), valueCaptor.capture());
		assertEquals(Arrays.asList("update Toto1 set a = ?, b = ? where id = ?", "update Toto2 set x = ?, y = ?, z = ? where id = ?"),
				statementArgCaptor.getAllValues());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.of(1, 17).add(2, 23).add(3, 1)
				.of(1, 29).add(2, 31).add(3, 2)
				.of(1, 37).add(2, 41).add(3, 3)
				.of(1, 43).add(2, 53).add(3, 4)
				
				.of(1, 117).add(2, 123).add(3, -117).add(4, 1)
				.of(1, 129).add(2, 131).add(3, -129).add(4, 2)
				.of(1, 137).add(2, 141).add(3, -137).add(4, 3)
				.of(1, 143).add(2, 153).add(3, -143).add(4, 4);
		assertCapturedPairsEqual(expectedPairs);
	}
	
	@Test
	public void testDelete() throws Exception {
		testInstance.delete(Arrays.asList(new Toto(7, 17, 23, 117, 123, -117)));
		
		assertEquals(Arrays.asList("delete from Toto2 where id = ?", "delete from Toto1 where id = ?"), statementArgCaptor.getAllValues());
		verify(preparedStatement, times(2)).addBatch();
		verify(preparedStatement, times(2)).executeBatch();
		verify(preparedStatement, times(0)).executeUpdate();
		verify(preparedStatement, times(2)).setInt(indexCaptor.capture(), valueCaptor.capture());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.of(1, 7)
				.of(1, 7);
		assertCapturedPairsEqual(expectedPairs);
	}
	
	@Test
	public void testDelete_multiple() throws Exception {
		testInstance.getDeleteExecutor().setRowCountManager(RowCountManager.NOOP_ROW_COUNT_MANAGER);
		persister2.getDeleteExecutor().setRowCountManager(RowCountManager.NOOP_ROW_COUNT_MANAGER);
		testInstance.delete(Arrays.asList(
				new Toto(1, 17, 23, 117, 123, -117),
				new Toto(2, 29, 31, 129, 131, -129),
				new Toto(3, 37, 41, 137, 141, -137),
				new Toto(4, 43, 53, 143, 153, -143)
		));
		// 4 statements because in operator is bounded to 3 values (see testInstance creation)
		assertEquals(Arrays.asList("delete from Toto2 where id = ?", "delete from Toto1 where id = ?"), statementArgCaptor.getAllValues());
		verify(preparedStatement, times(8)).addBatch();
		verify(preparedStatement, times(4)).executeBatch();
		verify(preparedStatement, times(0)).executeUpdate();
		verify(preparedStatement, times(8)).setInt(indexCaptor.capture(), valueCaptor.capture());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.of(1, 1).add(1, 2).add(1, 3)
				.of(1, 4)
				.of(1, 1).add(1, 2).add(1, 3)
				.of(1, 4);
		assertCapturedPairsEqual(expectedPairs);
	}
	
	@Test
	public void testDeleteById() throws Exception {
		testInstance.deleteById(Arrays.asList(
				new Toto(7, 17, 23, 117, 123, -117)
		));
		
		assertEquals(Arrays.asList("delete from Toto2 where id in (?)", "delete from Toto1 where id in (?)"), statementArgCaptor.getAllValues());
		verify(preparedStatement, times(2)).executeUpdate();
		verify(preparedStatement, times(2)).setInt(indexCaptor.capture(), valueCaptor.capture());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.of(1, 7);
		assertCapturedPairsEqual(expectedPairs);
	}
	
	@Test
	public void testDeleteById_multiple() throws Exception {
		testInstance.deleteById(Arrays.asList(
				new Toto(1, 17, 23, 117, 123, -117),
				new Toto(2, 29, 31, 129, 131, -129),
				new Toto(3, 37, 41, 137, 141, -137),
				new Toto(4, 43, 53, 143, 153, -143)
		));
		// 4 statements because in operator is bounded to 3 values (see testInstance creation)
		assertEquals(Arrays.asList("delete from Toto2 where id in (?, ?, ?)", "delete from Toto2 where id in (?)",
				"delete from Toto1 where id in (?, ?, ?)", "delete from Toto1 where id in (?)"), statementArgCaptor.getAllValues());
		verify(preparedStatement, times(2)).addBatch();
		verify(preparedStatement, times(2)).executeBatch();
		verify(preparedStatement, times(2)).executeUpdate();
		verify(preparedStatement, times(8)).setInt(indexCaptor.capture(), valueCaptor.capture());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>()
				.of(1, 1).add(2, 2).add(3, 3)
				.of(1, 4)
				.of(1, 1).add(2, 2).add(3, 3)
				.of(1, 4);
		assertCapturedPairsEqual(expectedPairs);
	}
	
	@Test
	public void testSelect() throws Exception {
		// mocking executeQuery not to return null because select method will use the ResultSet
		ResultSet resultSetMock = mock(ResultSet.class);
		when(preparedStatement.executeQuery()).thenReturn(resultSetMock);
		
		List<Toto> select = testInstance.select(Arrays.asList(
				new PersistableIdentifier<>(7),
				new PersistableIdentifier<>(13),
				new PersistableIdentifier<>(17),
				new PersistableIdentifier<>(23)
		));
		
		verify(preparedStatement, times(2)).executeQuery();
		verify(preparedStatement, times(4)).setInt(indexCaptor.capture(), valueCaptor.capture());
		assertEquals(Arrays.asList(
				"select Toto1.id as Toto1_id, Toto1.a as Toto1_a, Toto1.b as Toto1_b, Toto2.id as Toto2_id, Toto2.x as Toto2_x, Toto2.y as Toto2_y,"
						+ " Toto2.z as Toto2_z from Toto1 inner join Toto2 on Toto1.id = Toto2.id where Toto1.id in (?, ?, ?)",
				"select Toto1.id as Toto1_id, Toto1.a as Toto1_a, Toto1.b as Toto1_b, Toto2.id as Toto2_id, Toto2.x as Toto2_x, Toto2.y as Toto2_y,"
						+ " Toto2.z as Toto2_z from Toto1 inner join Toto2 on Toto1.id = Toto2.id where Toto1.id in (?)"),
				statementArgCaptor.getAllValues());
		PairSetList<Integer, Integer> expectedPairs = new PairSetList<Integer, Integer>().of(1, 7).add(2, 13).add(3, 17).add(1, 23);
		assertCapturedPairsEqual(expectedPairs);
	}
	
	private static class Toto implements Identified<Integer> {
		private Identifier<Integer> id;
		private Integer a, b, x, y, z;
		
		public Toto() {
		}
		
		public Toto(int id, Integer a, Integer b, Integer x, Integer y, Integer z) {
			this.id = new PersistableIdentifier<>(id);
			this.a = a;
			this.b = b;
			this.x = x;
			this.y = y;
			this.z = z;
		}
		
		public Toto(Integer a, Integer b, Integer x, Integer y, Integer z) {
			this.a = a;
			this.b = b;
			this.x = x;
			this.y = y;
			this.z = z;
		}
		
		@Override
		public Identifier getId() {
			return id;
		}
		
		@Override
		public void setId(Identifier id) {
			this.id = id;
		}
		
		@Override
		public String toString() {
			return getClass().getSimpleName() + "["
					+ Maps.asMap("id", (Object) id).add("a", a).add("b", b).add("x", x).add("y", y).add("z", z)
					+ "]";
		}
	}
}