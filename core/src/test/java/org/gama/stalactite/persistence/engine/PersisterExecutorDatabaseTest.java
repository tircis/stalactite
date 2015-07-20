package org.gama.stalactite.persistence.engine;

import org.gama.lang.Retryer;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.Maps;
import org.gama.sql.test.DerbyInMemoryDataSource;
import org.gama.sql.test.HSQLDBInMemoryDataSource;
import org.gama.stalactite.persistence.id.IdentifierGenerator;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.mapping.PersistentFieldHarverster;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.sql.ddl.DDLGenerator;
import org.gama.stalactite.persistence.sql.ddl.JavaTypeToSqlTypeMapping;
import org.gama.stalactite.persistence.sql.dml.DMLGenerator;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.persistence.structure.Table.Column;
import org.gama.stalactite.test.JdbcTransactionManager;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class PersisterExecutorDatabaseTest {
	
	private static final String DATASOURCES_DATAPROVIDER_NAME = "datasources";
	
	private PersisterExecutor<Toto> testInstance;
	private JdbcTransactionManager transactionManager;
	private InMemoryCounterIdentifierGenerator identifierGenerator;
	private ClassMappingStrategy<Toto> totoClassMappingStrategy;
	private Dialect dialect;
	private Table totoClassTable;
	
	@BeforeTest
	public void setUp() throws SQLException {
		totoClassTable = new Table(null, "Toto");
		PersistentFieldHarverster persistentFieldHarverster = new PersistentFieldHarverster();
		Map<Field, Column> totoClassMapping = persistentFieldHarverster.mapFields(Toto.class, totoClassTable);
		Map<String, Column> columns = totoClassTable.mapColumnsOnName();
		columns.get("a").setPrimaryKey(true);
		
		identifierGenerator = new InMemoryCounterIdentifierGenerator();
		totoClassMappingStrategy = new ClassMappingStrategy<>(Toto.class, totoClassTable,
				totoClassMapping, persistentFieldHarverster.getField("a"), identifierGenerator);
		
		JavaTypeToSqlTypeMapping simpleTypeMapping = new JavaTypeToSqlTypeMapping();
		simpleTypeMapping.put(Integer.class, "int");
		
		transactionManager = new JdbcTransactionManager(null);
		dialect = new Dialect(simpleTypeMapping);
	}
	
	@BeforeMethod
	public void setUpTest() throws SQLException {
		// reset id counter between 2 tests else id "overflow"
		identifierGenerator.idCounter.set(0);
		
		testInstance = new PersisterExecutor<>(totoClassMappingStrategy, new Persister.IdentifierFixer<>(totoClassMappingStrategy.getIdentifierGenerator(), totoClassMappingStrategy),
				3, transactionManager, new DMLGenerator(dialect.getColumnBinderRegistry() , new DMLGenerator.CaseSensitiveSorter()),
				Retryer.NO_RETRY, 3);
	}
	
	@DataProvider(name = DATASOURCES_DATAPROVIDER_NAME)
	public Object[][] dataSources() {
		return new Object[][] {
				{ new HSQLDBInMemoryDataSource() },
				{ new DerbyInMemoryDataSource() },
		};
	}
	
	@Test(dataProvider = DATASOURCES_DATAPROVIDER_NAME)
	public void testSelect(final DataSource dataSource) throws SQLException {
		transactionManager.setDataSource(dataSource);
		DDLDeployer ddlDeployer = new DDLDeployer(new DDLGenerator(Arrays.asList(totoClassTable), dialect.getJavaTypeToSqlTypeMapping())) {
			@Override
			protected Connection getCurrentConnection() throws SQLException {
				return dataSource.getConnection();
			}
		};
		ddlDeployer.deployDDL();
		Connection connection = dataSource.getConnection();
		connection.prepareStatement("insert into Toto(a, b, c) values (1, 10, 100)").execute();
		connection.prepareStatement("insert into Toto(a, b, c) values (2, 20, 200)").execute();
		connection.prepareStatement("insert into Toto(a, b, c) values (3, 30, 300)").execute();
		connection.prepareStatement("insert into Toto(a, b, c) values (4, 40, 400)").execute();
		connection.commit();
		List<Toto> totos = testInstance.select(Arrays.asList((Serializable) 1));
		Toto t = Iterables.first(totos);
		assertEquals(1, (Object) t.a);
		assertEquals(10, (Object) t.b);
		assertEquals(100, (Object) t.c);
		totos = testInstance.select(Arrays.asList((Serializable) 2, 3, 4));
		for (int i = 2; i <= 4; i++) {
			t = totos.get(i - 2);
			assertEquals(i, (Object) t.a);
			assertEquals(10 * i, (Object) t.b);
			assertEquals(100 * i, (Object) t.c);
		}
	}
	
	@Test(dataProvider = DATASOURCES_DATAPROVIDER_NAME)
	public void testSelect_updateRowCount(final DataSource dataSource) throws SQLException {
		transactionManager.setDataSource(dataSource);
		DDLDeployer ddlDeployer = new DDLDeployer(new DDLGenerator(Arrays.asList(totoClassTable), dialect.getJavaTypeToSqlTypeMapping())) {
			@Override
			protected Connection getCurrentConnection() throws SQLException {
				return dataSource.getConnection();
			}
		};
		ddlDeployer.deployDDL();
		
		
		// check inserted row count
		int insertedRowCount = testInstance.insert(Arrays.asList(new Toto(1, 10, 100)));
		assertEquals(1, insertedRowCount);
		insertedRowCount = testInstance.insert(Arrays.asList(new Toto(2, 20, 200), new Toto(3, 30, 300), new Toto(4, 40, 400)));
		assertEquals(3, insertedRowCount);
		
		// check updated row count roughly
		int updatedRoughlyRowCount = testInstance.updateRoughly(Arrays.asList(new Toto(1, 10, 100)));
		assertEquals(1, updatedRoughlyRowCount);
		updatedRoughlyRowCount = testInstance.insert(Arrays.asList(new Toto(2, 20, 200), new Toto(3, 30, 300), new Toto(4, 40, 400)));
		assertEquals(3, updatedRoughlyRowCount);
		updatedRoughlyRowCount = testInstance.updateRoughly(Arrays.asList(new Toto(-1, 10, 100)));
		assertEquals(0, updatedRoughlyRowCount);
		
		// check updated row count
		int updatedFullyRowCount = testInstance.updateFully(Arrays.asList((Entry<Toto, Toto>)
				new AbstractMap.SimpleEntry<>(new Toto(1, 10, 100), new Toto(1, 10, 101))));
		assertEquals(1, updatedFullyRowCount);
		updatedFullyRowCount = testInstance.updateFully(Arrays.asList((Entry<Toto, Toto>)
				new AbstractMap.SimpleEntry<>(new Toto(1, 10, 101), new Toto(1, 10, 101))));
		assertEquals(0, updatedFullyRowCount);
		updatedFullyRowCount = testInstance.updateFully(Arrays.asList((Entry<Toto, Toto>)
				new AbstractMap.SimpleEntry<>(new Toto(2, 20, 200), new Toto(2, 20, 201)),
				new AbstractMap.SimpleEntry<>(new Toto(3, 30, 300), new Toto(3, 30, 301)),
				new AbstractMap.SimpleEntry<>(new Toto(4, 40, 400), new Toto(4, 40, 401))));
		assertEquals(3, updatedFullyRowCount);
		
		// check deleted row count
		int deleteRowCount = testInstance.delete(Arrays.asList(new Toto(1, 10, 100)));
		assertEquals(1, deleteRowCount);
		deleteRowCount = testInstance.delete(Arrays.asList(new Toto(1, 10, 100), new Toto(2, 20, 200), new Toto(3, 30, 300), new Toto(4, 40, 400)));
		assertEquals(3, deleteRowCount);
		deleteRowCount = testInstance.delete(Arrays.asList(new Toto(1, 10, 100), new Toto(2, 20, 200), new Toto(3, 30, 300), new Toto(4, 40, 400)));
		assertEquals(0, deleteRowCount);
	}
	
	private static class Toto {
		private Integer a, b, c;
		
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
		
		@Override
		public String toString() {
			return getClass().getSimpleName() + "["
					+ Maps.asMap("a", (Object) a).add("b", b).add("c", c)
					+ "]";
		}
	}
	
	/**
	 * Simple id gnerator for our tests : increments a in-memory counter.
	 */
	public static class InMemoryCounterIdentifierGenerator implements IdentifierGenerator {
		
		private AtomicInteger idCounter = new AtomicInteger(0);
		
		@Override
		public Serializable generate() {
			return idCounter.addAndGet(1);
		}
		
		@Override
		public void configure(Map<String, Object> configuration) {
			
		}
	}
	
}