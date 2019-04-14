package org.gama.stalactite.persistence.engine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.gama.lang.collection.Iterables;
import org.gama.sql.binder.DefaultParameterBinders;
import org.gama.sql.result.ResultSetIterator;
import org.gama.sql.test.HSQLDBInMemoryDataSource;
import org.gama.stalactite.persistence.engine.CascadeOptions.RelationshipMode;
import org.gama.stalactite.persistence.engine.ColumnOptions.IdentifierPolicy;
import org.gama.stalactite.persistence.engine.IFluentMappingBuilder.IFluentMappingBuilderColumnOptions;
import org.gama.stalactite.persistence.engine.model.City;
import org.gama.stalactite.persistence.engine.model.Country;
import org.gama.stalactite.persistence.engine.model.Person;
import org.gama.stalactite.persistence.id.Identified;
import org.gama.stalactite.persistence.id.Identifier;
import org.gama.stalactite.persistence.sql.HSQLDBDialect;
import org.gama.stalactite.test.JdbcConnectionProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Guillaume Mary
 */
public class FluentEntityMappingConfigurationSupportForeignKeyTest {
	
	private static final HSQLDBDialect DIALECT = new HSQLDBDialect();
	private DataSource dataSource = new HSQLDBInMemoryDataSource();
	private Persister<Person, Identifier<Long>, ?> personPersister;
	private Persister<City, Identifier<Long>, ?> cityPersister;
	private PersistenceContext persistenceContext;
	
	@BeforeAll
	public static void initBinders() {
		// binder creation for our identifier
		DIALECT.getColumnBinderRegistry().register((Class) Identifier.class, Identifier.identifierBinder(DefaultParameterBinders.LONG_PRIMITIVE_BINDER));
		DIALECT.getJavaTypeToSqlTypeMapping().put(Identifier.class, "int");
		DIALECT.getColumnBinderRegistry().register((Class) Identified.class, Identified.identifiedBinder(DefaultParameterBinders.LONG_PRIMITIVE_BINDER));
		DIALECT.getJavaTypeToSqlTypeMapping().put(Identified.class, "int");
	}
	
	@BeforeEach
	public void initTest() {
		persistenceContext = new PersistenceContext(new JdbcConnectionProvider(dataSource), DIALECT);
		
		IFluentMappingBuilderColumnOptions<Person, Identifier<Long>> personMappingBuilder = FluentEntityMappingConfigurationSupport.from(Person.class,
				Identifier.LONG_TYPE)
				.add(Person::getId).identifier(IdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName);
		personPersister = personMappingBuilder.build(persistenceContext);
		
		IFluentMappingBuilderColumnOptions<City, Identifier<Long>> cityMappingBuilder = FluentEntityMappingConfigurationSupport.from(City.class,
				Identifier.LONG_TYPE)
				.add(City::getId).identifier(IdentifierPolicy.ALREADY_ASSIGNED)
				.add(City::getName)
				.add(City::getCountry);
		cityPersister = cityMappingBuilder.build(persistenceContext);
	}
	
	@Test
	public void testCascade_oneToOne_foreignKeyIsCreated() throws SQLException {
		// mapping building thantks to fluent API
		Persister<Country, Identifier<Long>, ?> countryPersister = FluentEntityMappingConfigurationSupport.from(Country.class,
				Identifier.LONG_TYPE)
				// setting a foreign key naming strategy to be tested
				.foreignKeyNamingStrategy(ForeignKeyNamingStrategy.DEFAULT)
				.add(Country::getId).identifier(IdentifierPolicy.ALREADY_ASSIGNED)
				.add(Country::getName)
				.add(Country::getDescription)
				.addOneToOne(Country::getPresident, personPersister)
				.addOneToManySet(Country::getCities, cityPersister).mappedBy(City::setCountry).cascading(RelationshipMode.READ_ONLY)
				.build(persistenceContext);
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		Connection currentConnection = persistenceContext.getCurrentConnection();
		ResultSetIterator<JdbcForeignKey> fkPersonIterator = new ResultSetIterator<JdbcForeignKey>(currentConnection.getMetaData().getExportedKeys(null, null,
				personPersister.getMainTable().getName().toUpperCase())) {
			@Override
			public JdbcForeignKey convert(ResultSet rs) throws SQLException {
				return new JdbcForeignKey(
						rs.getString("FK_NAME"),
						rs.getString("FKTABLE_NAME"), rs.getString("FKCOLUMN_NAME"),
						rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME")
				);
			}
		};
		JdbcForeignKey foundForeignKey = Iterables.first(fkPersonIterator);
		JdbcForeignKey expectedForeignKey = new JdbcForeignKey("FK_COUNTRY_PRESIDENTID_PERSON_ID", "COUNTRY", "PRESIDENTID", "PERSON", "ID");
		assertEquals(expectedForeignKey.getSignature(), foundForeignKey.getSignature());
		
		ResultSetIterator<JdbcForeignKey> fkCityIterator = new ResultSetIterator<JdbcForeignKey>(currentConnection.getMetaData().getExportedKeys(null, null,
				countryPersister.getMainTable().getName().toUpperCase())) {
			@Override
			public JdbcForeignKey convert(ResultSet rs) throws SQLException {
				return new JdbcForeignKey(
						rs.getString("FK_NAME"),
						rs.getString("FKTABLE_NAME"), rs.getString("FKCOLUMN_NAME"),
						rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME")
				);
			}
		};
		foundForeignKey = Iterables.first(fkCityIterator);
		expectedForeignKey = new JdbcForeignKey("FK_CITY_COUNTRYID_COUNTRY_ID", "CITY", "COUNTRYID", "COUNTRY", "ID");
		assertEquals(expectedForeignKey.getSignature(), foundForeignKey.getSignature());
	}
	
	private static class JdbcForeignKey {
		private final String name;
		private final String srcColumnName;
		private final String srcTableName;
		private final String targetColumnName;
		private final String targetTableName;
		
		
		private JdbcForeignKey(String name, String srcTableName, String srcColumnName, String targetTableName, String targetColumnName) {
			this.name = name;
			this.srcColumnName = srcColumnName;
			this.srcTableName = srcTableName;
			this.targetColumnName = targetColumnName;
			this.targetTableName = targetTableName;
		}
		
		String getSignature() {
			return "JdbcForeignKey{" +
					"name='" + name + '\'' +
					", srcColumnName='" + srcColumnName + '\'' +
					", srcTableName='" + srcTableName + '\'' +
					", targetColumnName='" + targetColumnName + '\'' +
					", targetTableName='" + targetTableName + '\'' +
					'}';
		}
	}
}