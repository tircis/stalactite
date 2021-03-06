package org.gama.stalactite.persistence.engine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;

import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode;
import org.gama.stalactite.persistence.engine.IFluentEntityMappingBuilder.IFluentMappingBuilderPropertyOptions;
import org.gama.stalactite.persistence.engine.model.City;
import org.gama.stalactite.persistence.engine.model.Country;
import org.gama.stalactite.persistence.engine.model.Person;
import org.gama.stalactite.persistence.engine.model.State;
import org.gama.stalactite.persistence.engine.runtime.IConfiguredPersister;
import org.gama.stalactite.persistence.id.Identifier;
import org.gama.stalactite.persistence.id.PersistableIdentifier;
import org.gama.stalactite.persistence.id.PersistedIdentifier;
import org.gama.stalactite.persistence.id.StatefullIdentifierAlreadyAssignedIdentifierPolicy;
import org.gama.stalactite.persistence.id.provider.LongProvider;
import org.gama.stalactite.persistence.sql.HSQLDBDialect;
import org.gama.stalactite.sql.binder.DefaultParameterBinders;
import org.gama.stalactite.sql.result.ResultSetIterator;
import org.gama.stalactite.sql.test.HSQLDBInMemoryDataSource;
import org.gama.stalactite.test.JdbcConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.stream.Collectors.toSet;
import static org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode.ALL;
import static org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode.ALL_ORPHAN_REMOVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Guillaume Mary
 */
public class FluentEntityMappingConfigurationSupportToOneAndToManyMixTest {
	
	private final HSQLDBDialect dialect = new HSQLDBDialect();
	private final DataSource dataSource = new HSQLDBInMemoryDataSource();
	private PersistenceContext persistenceContext;
	private IFluentMappingBuilderPropertyOptions<City, Identifier<Long>> cityMappingConfiguration;
	private IFluentMappingBuilderPropertyOptions<Person, Identifier<Long>> personMappingConfiguration;
	
	@BeforeEach
	public void initTest() {
		// binder creation for our identifier
		dialect.getColumnBinderRegistry().register((Class) Identifier.class, Identifier.identifierBinder(DefaultParameterBinders.LONG_PRIMITIVE_BINDER));
		dialect.getJavaTypeToSqlTypeMapping().put(Identifier.class, "int");
		persistenceContext = new PersistenceContext(new JdbcConnectionProvider(dataSource), dialect);
		
		IFluentMappingBuilderPropertyOptions<Person, Identifier<Long>> personMappingBuilder = MappingEase.entityBuilder(Person.class,
				Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName);
		personMappingConfiguration = personMappingBuilder;
		
		IFluentMappingBuilderPropertyOptions<City, Identifier<Long>> cityMappingBuilder = MappingEase.entityBuilder(City.class,
				Identifier.LONG_TYPE)
				.add(City::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(City::getName);
		cityMappingConfiguration = cityMappingBuilder;
	}
	
	@Test
	void foreignKeyIsCreated() throws SQLException {
		// mapping building thanks to fluent API
		IConfiguredPersister<Country, Identifier<Long>> countryPersister = MappingEase.entityBuilder(Country.class,
				Identifier.LONG_TYPE)
				// setting a foreign key naming strategy to be tested
				.withForeignKeyNaming(ForeignKeyNamingStrategy.DEFAULT)
				.add(Country::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Country::getName)
				.add(Country::getDescription)
				.addOneToOne(Country::getPresident, personMappingConfiguration)
				.addOneToManySet(Country::getCities, cityMappingConfiguration).mappedBy(City::setCountry).cascading(RelationMode.READ_ONLY)
				.build(persistenceContext);
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		Connection currentConnection = persistenceContext.getConnectionProvider().getCurrentConnection();
		ResultSetIterator<JdbcForeignKey> fkPersonIterator = new ResultSetIterator<JdbcForeignKey>(currentConnection.getMetaData().getExportedKeys(null, null,
				((IConfiguredPersister) persistenceContext.getPersister(Person.class)).getMappingStrategy().getTargetTable().getName().toUpperCase())) {
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
				countryPersister.getMappingStrategy().getTargetTable().getName().toUpperCase())) {
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
	
	@Test
	public void testCascade_oneToOneAndOneToMany_CRUD() {
		// mapping building thanks to fluent API
		IEntityPersister<Country, Identifier<Long>> countryPersister = MappingEase.entityBuilder(Country.class, Identifier.LONG_TYPE)
				.add(Country::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Country::getName)
				.addOneToOne(Country::getPresident, personMappingConfiguration).cascading(ALL)
				.addOneToManySet(Country::getCities, cityMappingConfiguration).cascading(ALL)
				.build(persistenceContext);
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		LongProvider countryIdProvider = new LongProvider();
		Country dummyCountry = new Country(countryIdProvider.giveNewIdentifier());
		dummyCountry.setName("France");
		
		Person person = new Person(new LongProvider().giveNewIdentifier());
		person.setName("French president");
		dummyCountry.setPresident(person);
		
		LongProvider cityIdentifierProvider = new LongProvider();
		City capital = new City(cityIdentifierProvider.giveNewIdentifier());
		capital.setName("Paris");
		dummyCountry.addCity(capital);
		
		// testing insert cascade
		countryPersister.insert(dummyCountry);
		Country persistedCountry = countryPersister.select(dummyCountry.getId());
		assertEquals(new PersistedIdentifier<>(0L), persistedCountry.getId());
		assertEquals("French president", persistedCountry.getPresident().getName());
		assertEquals("Paris", Iterables.first(persistedCountry.getCities()).getName());
		assertTrue(persistedCountry.getPresident().getId().isPersisted());
		assertTrue(Iterables.first(persistedCountry.getCities()).getId().isPersisted());
		
		// testing update cascade
		persistedCountry.getPresident().setName("New french president");
		City grenoble = new City(cityIdentifierProvider.giveNewIdentifier());
		grenoble.setName("Grenoble");
		persistedCountry.addCity(grenoble);
		countryPersister.update(persistedCountry, dummyCountry, true);
		
		persistedCountry = countryPersister.select(dummyCountry.getId());
		assertEquals(new PersistedIdentifier<>(0L), persistedCountry.getId());
		assertEquals("New french president", persistedCountry.getPresident().getName());
		assertEquals(Arrays.asHashSet("Grenoble", "Paris"), Iterables.collect(persistedCountry.getCities(), City::getName, HashSet::new));
		assertTrue(persistedCountry.getPresident().getId().isPersisted());
		assertTrue(Iterables.first(persistedCountry.getCities()).getId().isPersisted());
	}
	
	
	@Test
	public void testCascade_SetSetMix_update() {
		IFluentMappingBuilderPropertyOptions<State, Identifier<Long>> stateMappingBuilder = MappingEase.entityBuilder(State.class,
				Identifier.LONG_TYPE)
				.add(State::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(State::getName);
		
		IEntityPersister<Country, Identifier<Long>> countryPersister = MappingEase.entityBuilder(Country.class, Identifier.LONG_TYPE)
				.add(Country::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Country::getName)
				.add(Country::getDescription)
				.addOneToManySet(Country::getCities, cityMappingConfiguration).mappedBy(City::setCountry).cascading(ALL_ORPHAN_REMOVAL)
				.addOneToManySet(Country::getStates, stateMappingBuilder).mappedBy(State::setCountry).cascading(ALL)
				.build(persistenceContext);
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		LongProvider countryIdProvider = new LongProvider();
		Country dummyCountry = new Country(countryIdProvider.giveNewIdentifier());
		dummyCountry.setName("France");
		dummyCountry.setDescription("Smelly cheese !");
		LongProvider cityIdProvider = new LongProvider();
		City paris = new City(cityIdProvider.giveNewIdentifier());
		paris.setName("Paris");
		dummyCountry.addCity(paris);
		City lyon = new City(cityIdProvider.giveNewIdentifier());
		lyon.setName("Lyon");
		dummyCountry.addCity(lyon);
		
		LongProvider stateIdProvider = new LongProvider();
		State isere = new State(new PersistableIdentifier<>(stateIdProvider.giveNewIdentifier()));
		isere.setName("Isere");
		dummyCountry.addState(isere);
		State ain = new State(new PersistableIdentifier<>(stateIdProvider.giveNewIdentifier()));
		ain.setName("ain");
		dummyCountry.addState(ain);
		
		countryPersister.insert(dummyCountry);
		
		// Changing country cities to see what happens when we save it to the database
		Country persistedCountry = countryPersister.select(dummyCountry.getId());
		persistedCountry.getCities().remove(paris);
		City grenoble = new City(cityIdProvider.giveNewIdentifier());
		grenoble.setName("Grenoble");
		persistedCountry.addCity(grenoble);
		Iterables.first(persistedCountry.getCities()).setName("changed");
		
		persistedCountry.getStates().remove(ain);
		State ardeche = new State(new PersistableIdentifier<>(cityIdProvider.giveNewIdentifier()));
		ardeche.setName("ardeche");
		persistedCountry.addState(ardeche);
		Iterables.first(persistedCountry.getStates()).setName("changed");
		
		countryPersister.update(persistedCountry, dummyCountry, true);
		
		Country persistedCountry2 = countryPersister.select(dummyCountry.getId());
		// Checking deletion : for cities we asked for deletion of removed entities so the reloaded instance must have the same content as the memory one
		// but we didn't for regions, so all of them must be there
		// (comparison are done on equals/hashCode => id)
		assertEquals(Arrays.asHashSet(lyon, grenoble), persistedCountry2.getCities());
		assertEquals(Arrays.asHashSet(ardeche, isere), persistedCountry2.getStates());
		// Checking update is done too
		assertEquals(Arrays.asHashSet("changed", "Grenoble"), persistedCountry2.getCities().stream().map(City::getName).collect(toSet()));
		assertEquals(Arrays.asHashSet("changed", "ardeche"), persistedCountry2.getStates().stream().map(State::getName).collect(toSet()));
		
		// Ain should'nt have been deleted because we didn't asked for orphan removal
		List<Long> loadedAin = persistenceContext.newQuery("select id from State where id = " + ain.getId().getSurrogate(), Long.class)
				.mapKey(Long::new, "id", long.class)
				.execute();
		assertNotNull(Iterables.first(loadedAin));
	}
	
	@Test
	public void testCascade_ListSetMix_listContainsDuplicate_CRUD() {
		IFluentMappingBuilderPropertyOptions<State, Identifier<Long>> stateMappingBuilder = MappingEase.entityBuilder(State.class,
				Identifier.LONG_TYPE)
				.add(State::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(State::getName);
		
		IEntityPersister<Country, Identifier<Long>> countryPersister = MappingEase.entityBuilder(Country.class, Identifier.LONG_TYPE)
				.add(Country::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Country::getName)
				.add(Country::getDescription)
				.addOneToManyList(Country::getAncientCities, cityMappingConfiguration).reverselySetBy(City::setCountry).cascading(ALL_ORPHAN_REMOVAL)
				.addOneToManySet(Country::getStates, stateMappingBuilder).mappedBy(State::setCountry).cascading(ALL)
				.build(persistenceContext);
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		LongProvider countryIdProvider = new LongProvider();
		Country dummyCountry = new Country(countryIdProvider.giveNewIdentifier());
		dummyCountry.setName("France");
		dummyCountry.setDescription("Smelly cheese !");
		LongProvider cityIdProvider = new LongProvider();
		City paris = new City(cityIdProvider.giveNewIdentifier());
		paris.setName("Paris");
		dummyCountry.addAncientCity(paris);
		City lyon = new City(cityIdProvider.giveNewIdentifier());
		lyon.setName("Lyon");
		dummyCountry.addAncientCity(lyon);
		// we add a duplicate
		dummyCountry.addAncientCity(lyon);
		
		LongProvider stateIdProvider = new LongProvider();
		State isere = new State(new PersistableIdentifier<>(stateIdProvider.giveNewIdentifier()));
		isere.setName("Isere");
		dummyCountry.addState(isere);
		State ain = new State(new PersistableIdentifier<>(stateIdProvider.giveNewIdentifier()));
		ain.setName("ain");
		dummyCountry.addState(ain);
		
		countryPersister.insert(dummyCountry);
		
		// Changing country cities to see what happens when we save it to the database
		Country persistedCountry = countryPersister.select(dummyCountry.getId());
		assertEquals(dummyCountry, persistedCountry);
		persistedCountry.getAncientCities().remove(paris);
		City grenoble = new City(cityIdProvider.giveNewIdentifier());
		grenoble.setName("Grenoble");
		persistedCountry.addAncientCity(grenoble);
		Iterables.first(persistedCountry.getAncientCities()).setName("changed");
		
		persistedCountry.getStates().remove(ain);
		State ardeche = new State(new PersistableIdentifier<>(cityIdProvider.giveNewIdentifier()));
		ardeche.setName("ardeche");
		persistedCountry.addState(ardeche);
		Iterables.first(persistedCountry.getStates()).setName("changed");
		
		countryPersister.update(persistedCountry, dummyCountry, true);
		
		Country persistedCountry2 = countryPersister.select(dummyCountry.getId());
		// Checking deletion : for cities we asked for deletion of removed entities so the reloaded instance must have the same content as the memory one
		// but we didn't for regions, so all of them must be there
		// (comparison are done on equals/hashCode => id)
		assertEquals(Arrays.asList(lyon, lyon, grenoble), persistedCountry2.getAncientCities());
		assertEquals(Arrays.asHashSet(ardeche, isere), persistedCountry2.getStates());
		// Checking update is done too
		assertEquals(Arrays.asHashSet("changed", "Grenoble"), persistedCountry2.getAncientCities().stream().map(City::getName).collect(toSet()));
		assertEquals(Arrays.asHashSet("changed", "ardeche"), persistedCountry2.getStates().stream().map(State::getName).collect(toSet()));
		
		// Ain should'nt have been deleted because we didn't asked for orphan removal
		List<Long> loadedAin = persistenceContext.newQuery("select id from State where id = " + ain.getId().getSurrogate(), Long.class)
				.mapKey(Long::new, "id", long.class)
				.execute();
		assertNotNull(Iterables.first(loadedAin));
	}
}
