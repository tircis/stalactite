package org.gama.stalactite.persistence.engine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.lang.Dates;
import org.gama.lang.Duo;
import org.gama.lang.InvocationHandlerSupport;
import org.gama.lang.Reflections;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.Maps;
import org.gama.stalactite.persistence.engine.IFluentEntityMappingBuilder.IFluentMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions;
import org.gama.stalactite.persistence.engine.IFluentEntityMappingBuilder.IFluentMappingBuilderPropertyOptions;
import org.gama.stalactite.persistence.engine.configurer.FluentEmbeddableMappingConfigurationSupport;
import org.gama.stalactite.persistence.engine.configurer.PersisterBuilderImplTest.ToStringBuilder;
import org.gama.stalactite.persistence.engine.model.AbstractCountry;
import org.gama.stalactite.persistence.engine.model.City;
import org.gama.stalactite.persistence.engine.model.Country;
import org.gama.stalactite.persistence.engine.model.Gender;
import org.gama.stalactite.persistence.engine.model.Person;
import org.gama.stalactite.persistence.engine.model.PersonWithGender;
import org.gama.stalactite.persistence.engine.model.Timestamp;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredJoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredPersister;
import org.gama.stalactite.persistence.engine.runtime.JoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.PersisterWrapper;
import org.gama.stalactite.persistence.id.Identified;
import org.gama.stalactite.persistence.id.Identifier;
import org.gama.stalactite.persistence.id.PersistableIdentifier;
import org.gama.stalactite.persistence.id.PersistedIdentifier;
import org.gama.stalactite.persistence.id.StatefullIdentifierAlreadyAssignedIdentifierPolicy;
import org.gama.stalactite.persistence.sql.HSQLDBDialect;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.ForeignKey;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.sql.ConnectionProvider;
import org.gama.stalactite.sql.SimpleConnectionProvider;
import org.gama.stalactite.sql.binder.DefaultParameterBinders;
import org.gama.stalactite.sql.binder.LambdaParameterBinder;
import org.gama.stalactite.sql.binder.NullAwareParameterBinder;
import org.gama.stalactite.sql.dml.SQLOperation.SQLOperationListener;
import org.gama.stalactite.sql.dml.SQLStatement;
import org.gama.stalactite.sql.dml.SQLStatement.BindingException;
import org.gama.stalactite.sql.test.HSQLDBInMemoryDataSource;
import org.gama.stalactite.test.JdbcConnectionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.gama.lang.collection.Iterables.collect;
import static org.gama.lang.function.Functions.chain;
import static org.gama.lang.function.Functions.link;
import static org.gama.lang.test.Assertions.assertAllEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Guillaume Mary
 */
class FluentEntityMappingConfigurationSupportTest {
	
	// NB: dialect is made non static because we register binder for the same column several times in these tests
	// and this is not supported : the very first one takes priority  
	private HSQLDBDialect dialect = new HSQLDBDialect();
	private DataSource dataSource = new HSQLDBInMemoryDataSource();
	private PersistenceContext persistenceContext;
	
	@BeforeEach
	void initTest() {
		dialect.getDmlGenerator().sortColumnsAlphabetically();	// for steady checks on SQL orders
		// binder creation for our identifier
		dialect.getColumnBinderRegistry().register((Class) Identifier.class, Identifier.identifierBinder(DefaultParameterBinders.LONG_PRIMITIVE_BINDER));
		dialect.getJavaTypeToSqlTypeMapping().put(Identifier.class, "int");
		
		persistenceContext = new PersistenceContext(new JdbcConnectionProvider(dataSource), dialect);
	}
	
	@Test
	void build_identifierIsNotDefined_throwsException() {
		IFluentMappingBuilderPropertyOptions<Toto, Identifier> mappingStrategy = MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId)
				.add(Toto::getName);
		
		// column should be correctly created
		assertEquals("Identifier is not defined for o.g.s.p.e.FluentEntityMappingConfigurationSupportTest$Toto," 
						+ " please add one throught o.g.s.p.e.ColumnOptions.identifier(o.g.s.p.e.ColumnOptions$IdentifierPolicy)",
				assertThrows(UnsupportedOperationException.class, () -> mappingStrategy.build(persistenceContext))
						.getMessage());
	}
	
	@Nested
	class UseConstructor {
		
		@Test
		void byDefault_constructorIsNotInvoked_setterIsCalled() {
			Table totoTable = new Table("Toto");
			Column<Table<?>, PersistedIdentifier> idColumn = totoTable.addColumn("id", PersistedIdentifier.class);
			
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			IEntityConfiguredPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto entity = new Toto();
			entity.setName("Tutu");
			persister.insert(entity);
			Toto loadedInstance = persister.select(entity.getId());
			assertTrue(loadedInstance.isSetIdWasCalled(), "setId was not called");
			assertFalse(loadedInstance.isConstructorWithIdWasCalled(), "constructor with Id was not called");
		}
		
		@Test
		void withConstructorSpecified_constructorIsInvoked() {
			Table totoTable = new Table("Toto");
			Column<Table<?>, PersistedIdentifier> idColumn = totoTable.addColumn("id", Identifier.class);
			
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			IEntityConfiguredPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.useConstructor(Toto::new, idColumn)
					.build(persistenceContext, totoTable);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto entity = new Toto();
			entity.setName("Tutu");
			persister.insert(entity);
			Toto loadedInstance = persister.select(entity.getId());
			assertTrue(loadedInstance.isSetIdWasCalled(), "setId was not called");
			assertTrue(loadedInstance.isConstructorWithIdWasCalled(), "constructor with Id was called");
		}
		
		@Test
		void withConstructorSpecified_constructorIsInvoked_setterIsCalled() {
			Table totoTable = new Table("Toto");
			Column<Table<?>, PersistedIdentifier> idColumn = totoTable.addColumn("id", Identifier.class);
			
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			IEntityConfiguredPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED).setByConstructor()
					.add(Toto::getName)
					.useConstructor(Toto::new, idColumn)
					.build(persistenceContext, totoTable);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto entity = new Toto();
			entity.setName("Tutu");
			persister.insert(entity);
			Toto loadedInstance = persister.select(entity.getId());
			assertFalse(loadedInstance.isSetIdWasCalled(), "setId was called");
			assertTrue(loadedInstance.isConstructorWithIdWasCalled(), "constructor with Id was called");
		}
		
		@Test
		void withConstructorSpecified_withSeveralArguments() {
			Table totoTable = new Table("Toto");
			Column<Table<?>, PersistedIdentifier> idColumn = totoTable.addColumn("id", Identifier.class);
			Column<Table<?>, String> nameColumn = totoTable.addColumn("name", String.class);
			
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			IEntityConfiguredPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED).setByConstructor()
					.add(Toto::getName).setByConstructor()
					.useConstructor(Toto::new, idColumn, nameColumn)
					.build(persistenceContext, totoTable);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto entity = new Toto();
			entity.setName("Hello");
			persister.insert(entity);
			Toto loadedInstance = persister.select(entity.getId());
			assertFalse(loadedInstance.isSetIdWasCalled(), "setId was called");
			assertEquals("Hello by constructor", loadedInstance.getName());
		}
	}
	
	@Test
	void add_withoutName_targetedPropertyNameIsTaken() {
		IEntityConfiguredPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Toto::getName)
				.build(persistenceContext);
		
		// column should be correctly created
		assertEquals("Toto", persister.getMappingStrategy().getTargetTable().getName());
		Column columnForProperty = (Column) persister.getMappingStrategy().getTargetTable().mapColumnsOnName().get("name");
		assertNotNull(columnForProperty);
		assertEquals(String.class, columnForProperty.getJavaType());
	}
	
	@Test
	void add_mandatory_onMissingValue_throwsException() {
		Table totoTable = new Table("Toto");
		Column idColumn = totoTable.addColumn("id", Identifier.class);
		dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
		dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
		
		IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Toto::getName).mandatory()
				.add(Toto::getFirstName).mandatory()
				.build(persistenceContext);
		
		// column should be correctly created
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		Toto toto = new Toto();
		RuntimeException thrownException = assertThrows(RuntimeException.class, () -> persister.insert(toto));
		assertEquals("Error while inserting values for " + toto, thrownException.getMessage());
		assertEquals(BindingException.class, thrownException.getCause().getClass());
		assertEquals("Expected non null value for : Toto.firstName, Toto.name", thrownException.getCause().getMessage());
	}
	
	@Test
	void add_mandatory_columnConstraintIsAdded() {
		IEntityConfiguredJoinedTablesPersister<Toto, Identifier> totoPersister = (IEntityConfiguredJoinedTablesPersister<Toto, Identifier>)
				MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Toto::getName).mandatory()
				.build(persistenceContext);
		
		assertFalse(totoPersister.getMappingStrategy().getTargetTable().getColumn("name").isNullable());
	}
	
	@Test
	void add_withColumn_columnIsTaken() {
		Table toto = new Table("Toto");
		Column<Table, String> titleColumn = toto.addColumn("title", String.class);
		IEntityConfiguredPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Toto::getName, titleColumn)
				.build(persistenceContext);
		
		// column should not have been created
		Column columnForProperty = (Column) persister.getMappingStrategy().getTargetTable().mapColumnsOnName().get("name");
		assertNull(columnForProperty);
		
		// title column is expected to be added to the mapping and participate to DML actions 
		assertEquals(Arrays.asSet("id", "title"), persister.getMappingStrategy().getInsertableColumns().stream().map(Column::getName).collect(Collectors.toSet()));
		assertEquals(Arrays.asSet("title"), persister.getMappingStrategy().getUpdatableColumns().stream().map(Column::getName).collect(Collectors.toSet()));
	}
	
	@Test
	void add_definedAsIdentifier_identifierIsStoredAsString() {
		HSQLDBDialect dialect = new HSQLDBDialect();
		PersistenceContext persistenceContext = new PersistenceContext(new JdbcConnectionProvider(new HSQLDBInMemoryDataSource()), dialect);
		
		Table totoTable = new Table("Toto");
		Column id = totoTable.addColumn("id", Identifier.class).primaryKey();
		Column name = totoTable.addColumn("name", String.class);
		// binder creation for our identifier
		dialect.getColumnBinderRegistry().register(id, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
		dialect.getJavaTypeToSqlTypeMapping().put(id, "varchar(255)");
		
		IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.build(persistenceContext, totoTable);
		// column should be correctly created
		assertTrue(totoTable.getColumn("id").isPrimaryKey());
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		Toto toto = new Toto();
		toto.setName("toto");
		persister.persist(toto);
		
		List<Duo> select = persistenceContext.select(Duo::new, id, name);
		assertEquals(1, select.size());
		assertEquals(toto.getId().getSurrogate().toString(), ((Identifier) select.get(0).getLeft()).getSurrogate().toString());
	}
	
	@Test
	void add_identifierDefinedTwice_throwsException() {
		assertEquals("Identifier is already defined by Toto::getId",
				assertThrows(IllegalArgumentException.class, () -> MappingEase.entityBuilder(Toto.class, Identifier.class)
						.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
						.add(Toto::getIdentifier).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED))
						.getMessage());
	}
	
	@Test
	void add_mappingDefinedTwiceByMethod_throwsException() {
		assertEquals("Mapping is already defined by method Toto::getName",
				assertThrows(MappingConfigurationException.class, () -> MappingEase.entityBuilder(Toto.class, Identifier.class)
						.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
						.add(Toto::getName)
						.add(Toto::setName)
						.build(persistenceContext))
						.getMessage());
	}
	
	@Test
	void add_mappingDefinedTwiceByColumn_throwsException() {
		assertEquals("Column 'xyz' of mapping 'Toto::getName' is already targetted by 'Toto::getFirstName'",
				assertThrows(MappingConfigurationException.class, () -> MappingEase.entityBuilder(Toto.class, Identifier.class)
						.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
						.add(Toto::getName, "xyz")
						.add(Toto::getFirstName, "xyz")
						.build(persistenceContext))
						.getMessage());
	}
	
	@Test
	void add_methodHasNoMatchingField_configurationIsStillValid() {
		Table toto = new Table("Toto");
		MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Toto::getNoMatchingField)
				.build(persistenceContext, toto);
		
		Column columnForProperty = (Column) toto.mapColumnsOnName().get("noMatchingField");
		assertNotNull(columnForProperty);
		assertEquals(Long.class, columnForProperty.getJavaType());
	}
	
	@Test
	void add_methodIsASetter_configurationIsStillValid() {
		Table toto = new Table("Toto");
		MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::setId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.build(persistenceContext, toto);
		
		Column columnForProperty = (Column) toto.mapColumnsOnName().get("id");
		assertNotNull(columnForProperty);
		assertEquals(Identifier.class, columnForProperty.getJavaType());
	}
	
	@Test
	void embed_definedByGetter() {
		Table toto = new Table("Toto");
		MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.embed(Toto::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate))
				.build(new PersistenceContext((ConnectionProvider) null, dialect), toto);
		
		Column columnForProperty = (Column) toto.mapColumnsOnName().get("creationDate");
		assertNotNull(columnForProperty);
		assertEquals(Date.class, columnForProperty.getJavaType());
	}
	
	@Test
	void embed_definedBySetter() {
		Table toto = new Table("Toto");
		MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.embed(Toto::setTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate))
				.build(new PersistenceContext((ConnectionProvider) null, dialect), toto);
		
		Column columnForProperty = (Column) toto.mapColumnsOnName().get("creationDate");
		assertNotNull(columnForProperty);
		assertEquals(Date.class, columnForProperty.getJavaType());
	}
	
	@Test
	void embed_withOverridenColumnName() {
		Table toto = new Table("Toto");
		MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.embed(Toto::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate))
					.overrideName(Timestamp::getCreationDate, "createdAt")
					.overrideName(Timestamp::getModificationDate, "modifiedAt")
				.build(new PersistenceContext((ConnectionProvider) null, dialect), toto);
		
		Map<String, Column> columnsByName = toto.mapColumnsOnName();
		
		// columns with getter name must be absent (hard to test: can be absent for many reasons !)
		assertNull(columnsByName.get("creationDate"));
		assertNull(columnsByName.get("modificationDate"));

		Column overridenColumn;
		// Columns with good name must be present
		overridenColumn = columnsByName.get("modifiedAt");
		assertNotNull(overridenColumn);
		assertEquals(Date.class, overridenColumn.getJavaType());
		overridenColumn = columnsByName.get("createdAt");
		assertNotNull(overridenColumn);
		assertEquals(Date.class, overridenColumn.getJavaType());
	}
	
	@Test
	void embed_withOverridenColumnName_nameAlreadyExists_throwsException() {
		Table totoTable = new Table("Toto");
		Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
		dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
		dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
		
		MappingConfigurationException thrownException = assertThrows(MappingConfigurationException.class,
				() -> MappingEase.entityBuilder(Toto.class, Identifier.class)
						.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
						.add(Toto::getName)
						.embed(Toto::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
								.add(Timestamp::getCreationDate)
								.add(Timestamp::getModificationDate))
						.overrideName(Timestamp::getCreationDate, "modificationDate")
						.build(persistenceContext));
		assertEquals("Column 'modificationDate' of mapping 'Timestamp::getCreationDate' is already targetted by 'Timestamp::getModificationDate'",
				thrownException.getMessage());
	}
	
	@Test
	void embed_withOverridenColumn() {
		Table targetTable = new Table("Toto");
		Column<Table, Date> createdAt = targetTable.addColumn("createdAt", Date.class);
		Column<Table, Date> modifiedAt = targetTable.addColumn("modifiedAt", Date.class);
		
		Connection connectionMock = InvocationHandlerSupport.mock(Connection.class);
		
		IEntityConfiguredPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.embed(Toto::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate))
					.override(Timestamp::getCreationDate, createdAt)
					.override(Timestamp::getModificationDate, modifiedAt)
				.build(new PersistenceContext(new SimpleConnectionProvider(connectionMock), dialect), targetTable);
		
		Map<String, Column> columnsByName = targetTable.mapColumnsOnName();
		
		// columns with getter name must be absent (hard to test: can be absent for many reasons !)
		assertNull(columnsByName.get("creationDate"));
		assertNull(columnsByName.get("modificationDate"));
		
		// checking that overriden column are in DML statements
		assertEquals(targetTable.getColumns(), persister.getMappingStrategy().getInsertableColumns());
		assertEquals(targetTable.getColumnsNoPrimaryKey(), persister.getMappingStrategy().getUpdatableColumns());
		assertEquals(targetTable.getColumns(), persister.getMappingStrategy().getSelectableColumns());
	}
	
	@Test
	void embed_withSomeExcludedProperty() {
		Table targetTable = new Table("Toto");
		Column<Table, Date> modifiedAt = targetTable.addColumn("modifiedAt", Date.class);
		
		Connection connectionMock = InvocationHandlerSupport.mock(Connection.class);
		
		IEntityConfiguredPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
				.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.embed(Toto::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate))
					.exclude(Timestamp::getCreationDate)
					.override(Timestamp::getModificationDate, modifiedAt)
				.build(new PersistenceContext(new SimpleConnectionProvider(connectionMock), dialect), targetTable);
		
		Map<String, Column> columnsByName = targetTable.mapColumnsOnName();
		
		// columns with getter name must be absent (hard to test: can be absent for many reasons !)
		assertNull(columnsByName.get("creationDate"));
		assertNull(columnsByName.get("modificationDate"));
		
		// checking that overriden column are in DML statements
		assertEquals(targetTable.getColumns(), persister.getMappingStrategy().getInsertableColumns());
		assertEquals(targetTable.getColumnsNoPrimaryKey(), persister.getMappingStrategy().getUpdatableColumns());
		assertEquals(targetTable.getColumns(), persister.getMappingStrategy().getSelectableColumns());
	}
	
	@Test
	void innerEmbed_withConflictingEmbeddable() throws SQLException {
		Table<?> countryTable = new Table<>("countryTable");
		
		IFluentEntityMappingBuilder<Country, Identifier<Long>> mappingBuilder = MappingEase
				.entityBuilder(Country.class, Identifier.LONG_TYPE)
				.add(Country::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Country::getName)
				.embed(Country::getPresident, MappingEase.embeddableBuilder(Person.class)
						.add(Person::getName)
						.add(Person::getVersion)
						.embed(Person::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
								.add(Timestamp::getCreationDate)
								.add(Timestamp::getModificationDate))
						.exclude(Timestamp::getCreationDate)
						.overrideName(Timestamp::getModificationDate, "presidentElectedAt"))
					.exclude(Person::getVersion)
					.overrideName(Person::getName, "presidentName")
					
				// this embed will conflict with Person one because its type is already mapped with no override
				.embed(Country::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate))
					.exclude(Timestamp::getModificationDate)
					.overrideName(Timestamp::getCreationDate, "countryCreatedAt");
		
		mappingBuilder.build(persistenceContext, countryTable);
		
		assertEquals(Arrays.asHashSet(
				// from Country
				"id", "name",
				// from Person
				"presidentName", "presidentElectedAt",
				// from Country.timestamp
				"countryCreatedAt"),
				collect(countryTable.getColumns(), Column::getName, HashSet::new));
		
		Connection connectionMock = mock(Connection.class);
		
		
		IEntityConfiguredPersister<Country, Identifier<Long>> persister = mappingBuilder.build(
				new PersistenceContext(new SimpleConnectionProvider(connectionMock), dialect), countryTable);
		
		Map<String, ? extends Column> columnsByName = countryTable.mapColumnsOnName();
		
		// columns with getter name must be absent (hard to test: can be absent for many reasons !)
		assertNull(columnsByName.get("creationDate"));
		assertNull(columnsByName.get("modificationDate"));
		
		// checking that overriden column are in DML statements
		assertEquals(countryTable.getColumns(), persister.getMappingStrategy().getInsertableColumns());
		assertEquals(countryTable.getColumnsNoPrimaryKey(), persister.getMappingStrategy().getUpdatableColumns());
		assertEquals(countryTable.getColumns(), persister.getMappingStrategy().getSelectableColumns());
		
		Country country = new Country(new PersistableIdentifier<>(1L));
		country.setName("France");
		
		Timestamp countryTimestamp = new Timestamp();
		LocalDateTime localDateTime = LocalDate.of(2018, 01, 01).atStartOfDay();
		Date countryCreationDate = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
		countryTimestamp.setCreationDate(countryCreationDate);
		country.setTimestamp(countryTimestamp);
		
		Person president = new Person();
		president.setName("François");
		
		Timestamp presidentTimestamp = new Timestamp();
		Date presidentElection = Date.from(LocalDate.of(2019, 01, 01).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
		presidentTimestamp.setModificationDate(presidentElection);
		president.setTimestamp(presidentTimestamp);
		
		country.setPresident(president);
		
		// preparing JDBC mocks and values capture
		PreparedStatement statementMock = mock(PreparedStatement.class);
		when(statementMock.executeBatch()).thenReturn(new int[] { 1 });
		Map<Column<Table, Object>, Object> capturedValues = new HashMap<>();
		when(connectionMock.prepareStatement(anyString())).thenReturn(statementMock);
		
		StringBuilder capturedSQL = new StringBuilder();
		((JoinedTablesPersister) (((PersisterWrapper) persister).getDeepestSurrogate())).getInsertExecutor().setOperationListener(new SQLOperationListener<Column<Table, Object>>() {
			@Override
			public void onValuesSet(Map<Column<Table, Object>, ?> values) {
				capturedValues.putAll(values);
			}
			
			@Override
			public void onExecute(SQLStatement<Column<Table, Object>> sqlStatement) {
				capturedSQL.append(sqlStatement.getSQL());
			}
		});
		
		// Testing ...
		persister.insert(country);
		
		assertEquals("insert into countryTable(countryCreatedAt, id, name, presidentElectedAt, presidentName) values (?, ?, ?, ?, ?)",
				capturedSQL.toString());
		assertEquals(Maps.forHashMap(Column.class, Object.class)
				.add(columnsByName.get("presidentName"), country.getPresident().getName())
				.add(columnsByName.get("presidentElectedAt"), country.getPresident().getTimestamp().getModificationDate())
				.add(columnsByName.get("name"), country.getName())
				.add(columnsByName.get("countryCreatedAt"), country.getTimestamp().getCreationDate())
				.add(columnsByName.get("id"), country.getId())
				, capturedValues);
	}
	
	@Test
	void innerEmbed_withSomeExcludedProperties() throws SQLException {
		Table<?> countryTable = new Table<>("countryTable");
		
		IFluentEntityMappingBuilder<Country, Identifier<Long>> mappingBuilder = MappingEase
				.entityBuilder(Country.class, Identifier.LONG_TYPE)
				.add(Country::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Country::getName)
				.embed(Country::getPresident, MappingEase.embeddableBuilder(Person.class)
						.add(Person::getName)
						.add(Person::getVersion)
						.embed(Person::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
								.add(Timestamp::getCreationDate)
								.add(Timestamp::getModificationDate))
						.exclude(Timestamp::getCreationDate)
						.overrideName(Timestamp::getModificationDate, "presidentElectedAt"))
					.exclude(Person::getVersion)
					.overrideName(Person::getName, "presidentName");
		
		mappingBuilder.build(persistenceContext, countryTable);
		
		assertEquals(Arrays.asHashSet(
				// from Country
				"id", "name",
				// from Person
				"presidentName", "presidentElectedAt"),
				collect(countryTable.getColumns(), Column::getName, HashSet::new));
		
		Connection connectionMock = mock(Connection.class);
		
		IEntityConfiguredPersister<Country, Identifier<Long>> persister = mappingBuilder.build(
				new PersistenceContext(new SimpleConnectionProvider(connectionMock), dialect), countryTable);
		
		Map<String, ? extends Column> columnsByName = countryTable.mapColumnsOnName();
		
		// columns with getter name must be absent (hard to test: can be absent for many reasons !)
		assertNull(columnsByName.get("creationDate"));
		assertNull(columnsByName.get("modificationDate"));
		
		// checking that overriden column are in DML statements
		assertEquals(countryTable.getColumns(), persister.getMappingStrategy().getInsertableColumns());
		assertEquals(countryTable.getColumnsNoPrimaryKey(), persister.getMappingStrategy().getUpdatableColumns());
		assertEquals(countryTable.getColumns(), persister.getMappingStrategy().getSelectableColumns());
		
		Country country = new Country(new PersistableIdentifier<>(1L));
		country.setName("France");
		
		Person president = new Person();
		president.setName("François");
		
		Timestamp presidentTimestamp = new Timestamp();
		Date presidentElection = Date.from(LocalDate.of(2019, 01, 01).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
		presidentTimestamp.setModificationDate(presidentElection);
		president.setTimestamp(presidentTimestamp);
		
		country.setPresident(president);
		
		// preparing JDBC mocks and values capture
		PreparedStatement statementMock = mock(PreparedStatement.class);
		when(statementMock.executeBatch()).thenReturn(new int[] { 1 });
		Map<Column<Table, Object>, Object> capturedValues = new HashMap<>();
		when(connectionMock.prepareStatement(anyString())).thenReturn(statementMock);
		
		StringBuilder capturedSQL = new StringBuilder();
		((JoinedTablesPersister) (((PersisterWrapper) persister).getDeepestSurrogate())).getInsertExecutor().setOperationListener(new SQLOperationListener<Column<Table, Object>>() {
			@Override
			public void onValuesSet(Map<Column<Table, Object>, ?> values) {
				capturedValues.putAll(values);
			}
			
			@Override
			public void onExecute(SQLStatement<Column<Table, Object>> sqlStatement) {
				capturedSQL.append(sqlStatement.getSQL());
			}
		});
		
		// Testing ...
		persister.insert(country);
		
		assertEquals("insert into countryTable(id, name, presidentElectedAt, presidentName) values (?, ?, ?, ?)",
				capturedSQL.toString());
		assertEquals(Maps.forHashMap(Column.class, Object.class)
				.add(columnsByName.get("presidentName"), country.getPresident().getName())
				.add(columnsByName.get("presidentElectedAt"), country.getPresident().getTimestamp().getModificationDate())
				.add(columnsByName.get("name"), country.getName())
				.add(columnsByName.get("id"), country.getId())
				, capturedValues);
	}
	
	@Test
	void innerEmbed_withTwiceSameInnerEmbeddableName() {
		Table<?> countryTable = new Table<>("countryTable");
		IFluentMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions<Country, Identifier<Long>, Timestamp> mappingBuilder = MappingEase.entityBuilder(Country.class,
				Identifier.LONG_TYPE)
				.add(Country::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Country::getName)
				.embed(Country::getPresident, MappingEase.embeddableBuilder(Person.class)
						.add(Person::getId)
						.add(Person::getName)
						.add(Person::getVersion)
						.embed(Person::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
								.add(Timestamp::getCreationDate)
								.add(Timestamp::getModificationDate)))
					.overrideName(Person::getId, "presidentId")
					.overrideName(Person::getName, "presidentName")
				// this embed will conflict with Country one because its type is already mapped with no override
				.embed(Country::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate));
		MappingConfigurationException thrownException = assertThrows(MappingConfigurationException.class, () -> mappingBuilder
				.build(persistenceContext, countryTable));
		assertEquals("Person::getTimestamp conflicts with Country::getTimestamp while embedding a o.g.s.p.e.m.Timestamp" +
				", column names should be overriden : Timestamp::getCreationDate, Timestamp::getModificationDate", thrownException.getMessage());
		
		// we add an override, exception must still be thrown, with different message
		mappingBuilder.overrideName(Timestamp::getModificationDate, "modifiedAt");
		
		thrownException = assertThrows(MappingConfigurationException.class, () -> mappingBuilder
				.build(persistenceContext));
		assertEquals("Person::getTimestamp conflicts with Country::getTimestamp while embedding a o.g.s.p.e.m.Timestamp" +
				", column names should be overriden : Timestamp::getCreationDate", thrownException.getMessage());
		
		// we override the last field, no exception is thrown
		mappingBuilder.overrideName(Timestamp::getCreationDate, "createdAt");
		mappingBuilder.build(persistenceContext, countryTable);
		
		assertEquals(Arrays.asHashSet(
				// from Country
				"id", "name",
				// from Person
				"presidentId", "version", "presidentName", "creationDate", "modificationDate",
				// from Country.timestamp
				"createdAt", "modifiedAt"),
				collect(countryTable.getColumns(), Column::getName, HashSet::new));
	}
	
	@Nested
	class EmbedWithExternalEmbbededBean {
		
		@Test
		void simpleCase() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			Column<Table, Date> creationDate = totoTable.addColumn("creationDate", Date.class);
			Column<Table, Date> modificationDate = totoTable.addColumn("modificationDate", Date.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			// embeddeable mapping to be reused
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate)
					.add(Timestamp::getModificationDate);
			
			IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.embed(Toto::getTimestamp, timestampMapping)
					.build(persistenceContext);
			
			// column should be correctly created
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto toto = new Toto();
			// this partial instanciation of Timestamp let us test its partial load too
			toto.setTimestamp(new Timestamp(Dates.nowAsDate(), null));
			persister.insert(toto);
			
			// Is everything fine in database ?
			List<Timestamp> select = persistenceContext.select(Timestamp::new, creationDate, modificationDate);
			assertEquals(toto.getTimestamp(), select.get(0));
			
			// Is loading is fine too ?
			Toto loadedToto = persister.select(toto.getId());
			assertEquals(toto.getTimestamp(), loadedToto.getTimestamp());
		}
		
		@Test
		void embed_withMappedSuperClass() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			Column<Table, Date> creationDate = totoTable.addColumn("creationDate", Date.class);
			Column<Table, Date> modificationDate = totoTable.addColumn("modificationDate", Date.class);
			Column<Table, Locale> localeColumn = totoTable.addColumn("locale", Locale.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			dialect.getColumnBinderRegistry().register(Locale.class, new NullAwareParameterBinder<>(new LambdaParameterBinder<>(
					(resultSet, columnName) -> Locale.forLanguageTag(resultSet.getString(columnName)),
					(preparedStatement, valueIndex, value) -> preparedStatement.setString(valueIndex, value.toLanguageTag()))));
			dialect.getJavaTypeToSqlTypeMapping().put(Locale.class, "VARCHAR(20)");
			
			// embeddeable mapping to be reused
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate)
					.add(Timestamp::getModificationDate);
			
			EmbeddableMappingConfigurationProvider<TimestampWithLocale> timestampWithLocaleMapping = MappingEase.embeddableBuilder(TimestampWithLocale.class)
					.add(TimestampWithLocale::getLocale)
					.mapSuperClass(timestampMapping);
			
			IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.embed(Toto::getTimestamp, timestampWithLocaleMapping)
					.build(persistenceContext);
			
			// column should be correctly created
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto toto = new Toto();
			// this partial instanciation of Timestamp let us test its partial load too
			toto.setTimestamp(new TimestampWithLocale(Dates.nowAsDate(), null, Locale.US));
			persister.insert(toto);
			
			// Is everything fine in database ?
			List<TimestampWithLocale> select = persistenceContext.select(TimestampWithLocale::new, creationDate, modificationDate, localeColumn);
			assertEquals(toto.getTimestamp(), select.get(0));
			
			// Is loading is fine too ?
			Toto loadedToto = persister.select(toto.getId());
			assertEquals(toto.getTimestamp(), loadedToto.getTimestamp());
		}
		
		@Test
		void embed_withMappedSuperClassAndOverride() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			Column<Table, Date> creationDate = totoTable.addColumn("creationDate", Date.class);
			Column<Table, Date> modificationDate = totoTable.addColumn("modificationTime", Date.class);
			Column<Table, Locale> localeColumn = totoTable.addColumn("locale", Locale.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			dialect.getColumnBinderRegistry().register(Locale.class, new NullAwareParameterBinder<>(new LambdaParameterBinder<>(
					(resultSet, columnName) -> Locale.forLanguageTag(resultSet.getString(columnName)),
					(preparedStatement, valueIndex, value) -> preparedStatement.setString(valueIndex, value.toLanguageTag()))));
			dialect.getJavaTypeToSqlTypeMapping().put(Locale.class, "VARCHAR(20)");
			
			// embeddeable mapping to be reused
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate)
					.add(Timestamp::getModificationDate);
			
			EmbeddableMappingConfigurationProvider<TimestampWithLocale> timestampWithLocaleMapping = MappingEase.embeddableBuilder(TimestampWithLocale.class)
					.add(TimestampWithLocale::getLocale)
					.mapSuperClass(timestampMapping);
			
			IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.embed(Toto::getTimestamp, timestampWithLocaleMapping)
						.override(Timestamp::getModificationDate, modificationDate)
					.build(persistenceContext, totoTable);
			
			// column should be correctly created
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto toto = new Toto();
			// this partial instanciation of Timestamp let us test its partial load too
			toto.setTimestamp(new TimestampWithLocale(Dates.nowAsDate(), null, Locale.US));
			persister.insert(toto);
			
			// Is everything fine in database ?
			List<TimestampWithLocale> select = persistenceContext.select(TimestampWithLocale::new, creationDate, modificationDate, localeColumn);
			assertEquals(toto.getTimestamp(), select.get(0));
			
			// Is loading is fine too ?
			Toto loadedToto = persister.select(toto.getId());
			assertEquals(toto.getTimestamp(), loadedToto.getTimestamp());
		}
		
		@Test
		void overrideName() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			Column<Table, Date> creationDate = totoTable.addColumn("createdAt", Date.class);
			Column<Table, Date> modificationDate = totoTable.addColumn("modificationDate", Date.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate)
					.add(Timestamp::getModificationDate);
			
			IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.embed(Toto::getTimestamp, timestampMapping)
						.overrideName(Timestamp::getCreationDate, "createdAt")
					.build(persistenceContext);
			
			Map<String, Column> columnsByName = totoTable.mapColumnsOnName();
			
			// columns with getter name must be absent (hard to test: can be absent for many reasons !)
			assertNull(columnsByName.get("creationDate"));
			
			// column should be correctly created
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto toto = new Toto();
			// this partial instanciation of Timestamp let us test its partial load too
			toto.setTimestamp(new Timestamp(Dates.nowAsDate(), null));
			persister.insert(toto);
			
			// Is everything fine in database ?
			List<Timestamp> select = persistenceContext.select(Timestamp::new, creationDate, modificationDate);
			assertEquals(toto.getTimestamp(), select.get(0));
			
			// Is loading is fine too ?
			Toto loadedToto = persister.select(toto.getId());
			assertEquals(toto.getTimestamp(), loadedToto.getTimestamp());
		}
		
		@Test
		void overrideName_nameAlreadyExists_throwsException() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			// embeddeable mapping to be reused
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate)
					.add(Timestamp::getModificationDate);
			
			MappingConfigurationException thrownException = assertThrows(MappingConfigurationException.class,
					() -> MappingEase.entityBuilder(Toto.class, Identifier.class)
							.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
							.add(Toto::getName)
							.embed(Toto::getTimestamp, timestampMapping)
								.overrideName(Timestamp::getCreationDate, "modificationDate")
							.build(persistenceContext));
			assertEquals("Column 'modificationDate' of mapping 'Timestamp::getCreationDate' is already targetted by 'Timestamp::getModificationDate'",
					thrownException.getMessage());
		}
		
		@Test
		void overrideName_nameIsAlreadyOverriden_nameIsOverwritten() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			Column<Table, Date> creationDate = totoTable.addColumn("createdAt", Date.class);
			Column<Table, Date> modificationDate = totoTable.addColumn("modificationDate", Date.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			// embeddeable mapping to be reused
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate, "creation")
					.add(Timestamp::getModificationDate);
			
			IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.embed(Toto::getTimestamp, timestampMapping)
						.overrideName(Timestamp::getCreationDate, "createdAt")
					.build(persistenceContext);
			
			// column should be correctly created
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto toto = new Toto();
			// this partial instanciation of Timestamp let us test its partial load too
			toto.setTimestamp(new Timestamp(Dates.nowAsDate(), null));
			persister.insert(toto);
			
			// Is everything fine in database ?
			List<Timestamp> select = persistenceContext.select(Timestamp::new, creationDate, modificationDate);
			assertEquals(toto.getTimestamp(), select.get(0));
			
			// Is loading is fine too ?
			Toto loadedToto = persister.select(toto.getId());
			assertEquals(toto.getTimestamp(), loadedToto.getTimestamp());
		}
		
		@Test
		void mappingDefinedTwice_throwsException() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			// embeddeable mapping to be reused
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate)
					.add(Timestamp::getModificationDate)
					.add(Timestamp::setModificationDate);
			
			MappingConfigurationException thrownException = assertThrows(MappingConfigurationException.class,
					() -> MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.embed(Toto::getTimestamp, timestampMapping)
					.build(persistenceContext));
			
			assertEquals("Mapping is already defined by method Timestamp::getModificationDate", thrownException.getMessage());
		}
		
		@Test
		void exclude() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			// embeddeable mapping to be reused
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate, "creation")
					.add(Timestamp::getModificationDate);
			
			IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.embed(Toto::getTimestamp, timestampMapping)
						.exclude(Timestamp::getCreationDate)
					.build(persistenceContext);
			
			Map map = totoTable.mapColumnsOnName();
			assertNull(map.get("creationDate"));
			
			// column should be correctly created
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto toto = new Toto();
			// this partial instanciation of Timestamp let us test its partial load too
			toto.setTimestamp(new Timestamp(Dates.nowAsDate(), null));
			persister.insert(toto);
			
			// Is loading is fine too ?
			Toto loadedToto = persister.select(toto.getId());
			// timestamp is expected to be null because all columns in database are null, which proves that creationDate is not taken into account
			assertNull(loadedToto.getTimestamp());
		}
		
		@Test
		void overrideColumn() {
			Table totoTable = new Table("Toto");
			Column<Table, Identifier> idColumn = totoTable.addColumn("id", Identifier.class);
			Column<Table, Date> creationDate = totoTable.addColumn("createdAt", Date.class);
			Column<Table, Date> modificationDate = totoTable.addColumn("modificationDate", Date.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			// embeddeable mapping to be reused
			EmbeddableMappingConfigurationProvider<Timestamp> timestampMapping = MappingEase.embeddableBuilder(Timestamp.class)
					.add(Timestamp::getCreationDate, "creation")
					.add(Timestamp::getModificationDate);
			
			IEntityPersister<Toto, Identifier> persister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.embed(Toto::getTimestamp, timestampMapping)
						.override(Timestamp::getCreationDate, creationDate)
					.build(persistenceContext, totoTable);
			
			Map map = totoTable.mapColumnsOnName();
			assertNull(map.get("creationDate"));
			
			/// column should be correctly created
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto toto = new Toto();
			// this partial instanciation of Timestamp let us test its partial load too
			toto.setTimestamp(new Timestamp(Dates.nowAsDate(), null));
			persister.insert(toto);
			
			// Is everything fine in database ?
			List<Timestamp> select = persistenceContext.select(Timestamp::new, creationDate, modificationDate);
			assertEquals(toto.getTimestamp(), select.get(0));
			
			// Is loading is fine too ?
			Toto loadedToto = persister.select(toto.getId());
			assertEquals(toto.getTimestamp(), loadedToto.getTimestamp());
		}
	}
	
	@Test
	void withEnum_byDefault_ordinalIsUsed() {
		IEntityConfiguredPersister<PersonWithGender, Identifier<Long>> personPersister = MappingEase.entityBuilder(PersonWithGender.class, Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName)
				// no type of mapping given: neither ordinal nor name
				.addEnum(PersonWithGender::getGender)
				.build(persistenceContext);
		
		Column gender = (Column) personPersister.getMappingStrategy().getTargetTable().mapColumnsOnName().get("gender");
		dialect.getJavaTypeToSqlTypeMapping().put(gender, "VARCHAR(255)");
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		PersonWithGender person = new PersonWithGender(new PersistableIdentifier<>(1L));
		person.setName("toto");
		person.setGender(Gender.FEMALE);
		personPersister.insert(person);
		
		// checking that name was used
		List<Integer> result = persistenceContext.newQuery("select * from PersonWithGender", Integer.class)
				.mapKey(Integer::new, "gender", Integer.class)
				.execute();
		assertEquals(Arrays.asList(1), result);
	}
	
	@Test
	void addEnum_mandatory_onMissingValue_throwsException() {
		IEntityConfiguredPersister<PersonWithGender, Identifier<Long>> personPersister = MappingEase.entityBuilder(PersonWithGender.class, Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName)
				// no type of mapping given: neither ordinal nor name
				.addEnum(PersonWithGender::getGender).mandatory()
				.build(persistenceContext);
		
		Column gender = (Column) personPersister.getMappingStrategy().getTargetTable().mapColumnsOnName().get("gender");
		dialect.getJavaTypeToSqlTypeMapping().put(gender, "VARCHAR(255)");
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		PersonWithGender person = new PersonWithGender(new PersistableIdentifier<>(1L));
		person.setName("toto");
		person.setGender(null);
		
		RuntimeException thrownException = assertThrows(RuntimeException.class, () -> personPersister.insert(person));
		assertEquals("Error while inserting values for " + person, thrownException.getMessage());
		assertEquals(BindingException.class, thrownException.getCause().getClass());
		assertEquals("Expected non null value for : PersonWithGender.gender", thrownException.getCause().getMessage());
	}
	
	@Test
	void addEnum_mandatory_columnConstraintIsAdded() {
		IEntityConfiguredPersister<PersonWithGender, Identifier<Long>> personPersister = MappingEase.entityBuilder(PersonWithGender.class, Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName)
				// no type of mapping given: neither ordinal nor name
				.addEnum(PersonWithGender::getGender).mandatory()
				.build(persistenceContext);
		
		assertFalse(personPersister.getMappingStrategy().getTargetTable().getColumn("gender").isNullable());
	}
	
	@Test
	void withEnum_mappedWithOrdinal() {
		IEntityConfiguredPersister<PersonWithGender, Identifier<Long>> personPersister = MappingEase.entityBuilder(PersonWithGender.class, Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName)
				.addEnum(PersonWithGender::getGender).byOrdinal()
				.build(persistenceContext);
		
		Column gender = (Column) personPersister.getMappingStrategy().getTargetTable().mapColumnsOnName().get("gender");
		dialect.getJavaTypeToSqlTypeMapping().put(gender, "INT");
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		PersonWithGender person = new PersonWithGender(new PersistableIdentifier<>(1L));
		person.setName("toto");
		person.setGender(Gender.FEMALE);
		personPersister.insert(person);
		
		// checking that ordinal was used
		List<Integer> result = persistenceContext.newQuery("select * from PersonWithGender", Integer.class)
				.mapKey(Integer::valueOf, "gender", String.class)
				.execute();
		assertEquals(Arrays.asList(person.getGender().ordinal()), result);
	}
	
	@Test
	void withEnum_columnMappedWithOrdinal() {
		Table personTable = new Table<>("PersonWithGender");
		Column<Table, Gender> genderColumn = personTable.addColumn("gender", Gender.class);
		
		IEntityPersister<PersonWithGender, Identifier<Long>> personPersister = MappingEase.entityBuilder(PersonWithGender.class, Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName)
				.addEnum(PersonWithGender::getGender, genderColumn).byOrdinal()
				.build(persistenceContext);
		
		dialect.getJavaTypeToSqlTypeMapping().put(genderColumn, "INT");
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		PersonWithGender person = new PersonWithGender(new PersistableIdentifier<>(1L));
		person.setName("toto");
		person.setGender(Gender.FEMALE);
		personPersister.insert(person);
		
		// checking that ordinal was used
		List<Integer> result = persistenceContext.newQuery("select * from PersonWithGender", Integer.class)
				.mapKey(Integer::valueOf, "gender", String.class)
				.execute();
		assertEquals(Arrays.asList(person.getGender().ordinal()), result);
	}
	
	@Test
	void insert() {
		IEntityConfiguredPersister<PersonWithGender, Identifier<Long>> personPersister = MappingEase.entityBuilder(PersonWithGender.class, Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName)
				// no type of mapping given: neither ordinal nor name
				.addEnum(PersonWithGender::getGender)
				.build(persistenceContext);
		
		Column gender = (Column) personPersister.getMappingStrategy().getTargetTable().mapColumnsOnName().get("gender");
		dialect.getJavaTypeToSqlTypeMapping().put(gender, "VARCHAR(255)");
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		PersonWithGender person = new PersonWithGender(new PersistableIdentifier<>(1L));
		person.setName(null);
		person.setGender(Gender.FEMALE);
		
		personPersister.insert(person);
		
		PersonWithGender loadedPerson = personPersister.select(person.getId());
		assertEquals(Gender.FEMALE, loadedPerson.getGender());
	}
	
	@Test
	void insert_nullValues() {
		IEntityConfiguredPersister<PersonWithGender, Identifier<Long>> personPersister = MappingEase.entityBuilder(PersonWithGender.class, Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName)
				// no type of mapping given: neither ordinal nor name
				.addEnum(PersonWithGender::getGender)
				.build(persistenceContext);
		
		Column gender = (Column) personPersister.getMappingStrategy().getTargetTable().mapColumnsOnName().get("gender");
		dialect.getJavaTypeToSqlTypeMapping().put(gender, "VARCHAR(255)");
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		PersonWithGender person = new PersonWithGender(new PersistableIdentifier<>(1L));
		person.setName(null);
		person.setGender(null);
		
		personPersister.insert(person);
		
		PersonWithGender loadedPerson = personPersister.select(person.getId());
		assertEquals(null, loadedPerson.getGender());
	}
	
	@Test
	void update_nullValues() {
		IEntityConfiguredPersister<PersonWithGender, Identifier<Long>> personPersister = MappingEase.entityBuilder(PersonWithGender.class, Identifier.LONG_TYPE)
				.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
				.add(Person::getName)
				// no type of mapping given: neither ordinal nor name
				.addEnum(PersonWithGender::getGender)
				.build(persistenceContext);
		
		Column gender = (Column) personPersister.getMappingStrategy().getTargetTable().mapColumnsOnName().get("gender");
		dialect.getJavaTypeToSqlTypeMapping().put(gender, "VARCHAR(255)");
		
		DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
		ddlDeployer.deployDDL();
		
		PersonWithGender person = new PersonWithGender(new PersistableIdentifier<>(1L));
		person.setName("toto");
		person.setGender(Gender.MALE);
		
		personPersister.insert(person);
		
		PersonWithGender updatedPerson = new PersonWithGender(person.getId());
		int updatedRowCount = personPersister.update(updatedPerson, person, true);
		assertEquals(1, updatedRowCount);
		PersonWithGender loadedPerson = personPersister.select(person.getId());
		assertEquals(null, loadedPerson.getGender());
	}
	
	@Nested
	class CollectionOfElements {
		
		@Test
		void insert() {
			IEntityConfiguredPersister<Person, Identifier<Long>> personPersister = MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Person person = new Person(new PersistableIdentifier<>(1L));
			person.setName("toto");
			person.initNicknames();
			person.addNickname("tonton");
			
			personPersister.insert(person);
			
			Person loadedPerson = personPersister.select(person.getId());
			assertEquals(Arrays.asSet("tonton"), loadedPerson.getNicknames());
		}
		
		@Test
		void update_withNewObject() {
			IEntityConfiguredPersister<Person, Identifier<Long>> personPersister = MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Person person = new Person(new PersistableIdentifier<>(1L));
			person.setName("toto");
			person.initNicknames();
			person.addNickname("tonton");
			person.addNickname("tintin");
			
			personPersister.insert(person);
			
			Person loadedPerson = personPersister.select(person.getId());
			assertEquals(Arrays.asSet("tintin", "tonton"), loadedPerson.getNicknames());
			
			
			loadedPerson.addNickname("toutou");
			personPersister.update(loadedPerson, person, true);
			loadedPerson = personPersister.select(person.getId());
			assertEquals(Arrays.asSet("tintin", "tonton", "toutou"), loadedPerson.getNicknames());
		}
		
		@Test
		void update_objectRemoval() {
			IEntityConfiguredPersister<Person, Identifier<Long>> personPersister = MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Person person = new Person(new PersistableIdentifier<>(1L));
			person.setName("toto");
			person.initNicknames();
			person.addNickname("tonton");
			person.addNickname("tintin");
			
			personPersister.insert(person);
			
			Person loadedPerson = personPersister.select(person.getId());
			
			person.getNicknames().remove("tintin");
			personPersister.update(person, loadedPerson, true);
			loadedPerson = personPersister.select(person.getId());
			assertEquals(Arrays.asSet("tonton"), loadedPerson.getNicknames());
		}
		
		@Test
		void delete() {
			IEntityConfiguredPersister<Person, Identifier<Long>> personPersister = MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Person person = new Person(new PersistableIdentifier<>(1L));
			person.setName("toto");
			person.initNicknames();
			person.addNickname("tonton");
			person.addNickname("tintin");
			
			personPersister.insert(person);
			
			Person loadedPerson = personPersister.select(person.getId());
			personPersister.delete(loadedPerson);
			List<String> remainingNickNames = persistenceContext.newQuery("select nickNames from Person_nicknames", String.class)
					.mapKey(String::new, "nickNames", String.class)
					.execute();
			assertEquals(Collections.emptyList(), remainingNickNames);
		}
		
		@Test
		void withCollectionFactory() {
			IEntityConfiguredPersister<Person, Identifier<Long>> personPersister = MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
						.withCollectionFactory(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER.reversed()))
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Person person = new Person(new PersistableIdentifier<>(1L));
			person.setName("toto");
			person.initNicknames();
			person.addNickname("d");
			person.addNickname("a");
			person.addNickname("c");
			person.addNickname("b");
			
			// because nickNames is initialized with HashSet we get this order
			assertEquals(Arrays.asSet("a", "b", "c", "d"), person.getNicknames());
			
			personPersister.insert(person);
			
			Person loadedPerson = personPersister.select(person.getId());
			// because nickNames is initialized with TreeSet with reversed order we get this order
			assertAllEquals(Arrays.asSet("d", "c", "b", "a"), loadedPerson.getNicknames());
		}
		
		@Test
		void foreignKeyIsPresent() {
			MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
					.build(persistenceContext);
			
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			Map<String, Table> tablePerName = Iterables.map(tables, Table::getName);
			Table personTable = tablePerName.get("Person");
			Table nickNamesTable = tablePerName.get("Person_nicknames");
			
			Function<Column, String> columnPrinter = ToStringBuilder.of(", ",
					Column::getAbsoluteName,
					chain(Column::getJavaType, (Function<Class, String>) Reflections::toString));
			Function<ForeignKey, String> fkPrinter = ToStringBuilder.of(", ",
					ForeignKey::getName,
					link(ForeignKey::getColumns, ToStringBuilder.asSeveral(columnPrinter)),
					link(ForeignKey::getTargetColumns, ToStringBuilder.asSeveral(columnPrinter)));
			
			assertAllEquals(
					Arrays.asHashSet(new ForeignKey("FK_Person_nicknames_id_Person_id", nickNamesTable.getColumn("id"), personTable.getColumn("id"))),
					nickNamesTable.getForeignKeys(),
					fkPrinter);
		}
		
		
		@Test
		void mappedBy() {
			MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
						.mappedBy("identifier")
					.build(persistenceContext);
			
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			Map<String, Table> tablePerName = Iterables.map(tables, Table::getName);
			Table personTable = tablePerName.get("Person");
			Table nickNamesTable = tablePerName.get("Person_nicknames");
			assertNotNull(nickNamesTable);
			
			Function<Column, String> columnPrinter = ToStringBuilder.of(", ",
					Column::getAbsoluteName,
					chain(Column::getJavaType, (Function<Class, String>) Reflections::toString));
			Function<ForeignKey, String> fkPrinter = ToStringBuilder.of(", ",
					ForeignKey::getName,
					link(ForeignKey::getColumns, ToStringBuilder.asSeveral(columnPrinter)),
					link(ForeignKey::getTargetColumns, ToStringBuilder.asSeveral(columnPrinter)));
			
			assertAllEquals(
					Arrays.asHashSet(new ForeignKey("FK_Person_nicknames_identifier_Person_id", nickNamesTable.getColumn("identifier"), personTable.getColumn("id"))),
					nickNamesTable.getForeignKeys(),
					fkPrinter);
		}
		
		@Test
		void withTableNaming() {
			MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.withElementCollectionTableNaming(accessorDefinition -> "Toto")
					.addCollection(Person::getNicknames, String.class)
					.build(persistenceContext);
			
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			Map<String, Table> tablePerName = Iterables.map(tables, Table::getName);
			Table personTable = tablePerName.get("Person");
			Table nickNamesTable = tablePerName.get("Toto");
			assertNotNull(nickNamesTable);
			
			Function<Column, String> columnPrinter = ToStringBuilder.of(", ",
					Column::getAbsoluteName,
					chain(Column::getJavaType, (Function<Class, String>) Reflections::toString));
			Function<ForeignKey, String> fkPrinter = ToStringBuilder.of(", ",
					ForeignKey::getName,
					link(ForeignKey::getColumns, ToStringBuilder.asSeveral(columnPrinter)),
					link(ForeignKey::getTargetColumns, ToStringBuilder.asSeveral(columnPrinter)));
			
			assertAllEquals(
					Arrays.asHashSet(new ForeignKey("FK_Toto_id_Person_id", nickNamesTable.getColumn("id"), personTable.getColumn("id"))),
					nickNamesTable.getForeignKeys(),
					fkPrinter);
		}
		
		@Test
		void withTable() {
			Table nickNamesTable = new Table("Toto");
			
			MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
						.withTable(nickNamesTable)
					.build(persistenceContext);
			
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			Map<String, Table> tablePerName = Iterables.map(tables, Table::getName);
			Table personTable = tablePerName.get("Person");
			
			Function<Column, String> columnPrinter = ToStringBuilder.of(", ",
					Column::getAbsoluteName,
					chain(Column::getJavaType, (Function<Class, String>) Reflections::toString));
			Function<ForeignKey, String> fkPrinter = ToStringBuilder.of(", ",
					ForeignKey::getName,
					link(ForeignKey::getColumns, ToStringBuilder.asSeveral(columnPrinter)),
					link(ForeignKey::getTargetColumns, ToStringBuilder.asSeveral(columnPrinter)));
			
			assertAllEquals(
					Arrays.asHashSet(new ForeignKey("FK_Toto_id_Person_id", nickNamesTable.getColumn("id"), personTable.getColumn("id"))),
					nickNamesTable.getForeignKeys(),
					fkPrinter);
		}
		
		@Test
		void withTableName() {
			MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
						.withTable("Toto")
					.build(persistenceContext);
			
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			Map<String, Table> tablePerName = Iterables.map(tables, Table::getName);
			Table personTable = tablePerName.get("Person");
			Table nickNamesTable = tablePerName.get("Toto");
			assertNotNull(nickNamesTable);
			
			Function<Column, String> columnPrinter = ToStringBuilder.of(", ",
					Column::getAbsoluteName,
					chain(Column::getJavaType, (Function<Class, String>) Reflections::toString));
			Function<ForeignKey, String> fkPrinter = ToStringBuilder.of(", ",
					ForeignKey::getName,
					link(ForeignKey::getColumns, ToStringBuilder.asSeveral(columnPrinter)),
					link(ForeignKey::getTargetColumns, ToStringBuilder.asSeveral(columnPrinter)));
			
			assertAllEquals(
					Arrays.asHashSet(new ForeignKey("FK_Toto_id_Person_id", nickNamesTable.getColumn("id"), personTable.getColumn("id"))),
					nickNamesTable.getForeignKeys(),
					fkPrinter);
		}
		
		@Test
		void overrideColumnName() {
			MappingEase.entityBuilder(Person.class, Identifier.LONG_TYPE)
					.add(Person::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Person::getName)
					.addCollection(Person::getNicknames, String.class)
						.override("toto")
					.build(persistenceContext);
			
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			Map<String, Table> tablePerName = Iterables.map(tables, Table::getName);
			Table personTable = tablePerName.get("Person");
			Table nickNamesTable = tablePerName.get("Person_nicknames");
			
			assertEquals(Arrays.asHashSet("id", "toto"), nickNamesTable.mapColumnsOnName().keySet());
		}
		
		
		@Test
		void crudEnum() {
			Table totoTable = new Table("Toto");
			Column idColumn = totoTable.addColumn("id", Identifier.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			IEntityConfiguredPersister<Toto, Identifier> personPersister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.addCollection(Toto::getPossibleStates, State.class)
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto person = new Toto();
			person.setName("toto");
			person.getPossibleStates().add(State.DONE);
			person.getPossibleStates().add(State.IN_PROGRESS);
			
			personPersister.insert(person);
			
			Toto loadedPerson = personPersister.select(person.getId());
			assertEquals(Arrays.asSet(State.DONE, State.IN_PROGRESS), loadedPerson.getPossibleStates());
		}
		
		@Test
		void crudComplexType() {
			Table totoTable = new Table("Toto");
			Column idColumn = totoTable.addColumn("id", Identifier.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			IEntityConfiguredPersister<Toto, Identifier> personPersister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.addCollection(Toto::getTimes, Timestamp.class, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate))
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Toto person = new Toto();
			person.setName("toto");
			Timestamp timestamp1 = new Timestamp(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1));
			Timestamp timestamp2 = new Timestamp(LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2));
			person.getTimes().add(timestamp1);
			person.getTimes().add(timestamp2);
			
			personPersister.insert(person);
			
			Toto loadedPerson = personPersister.select(person.getId());
			assertEquals(Arrays.asSet(timestamp1, timestamp2), loadedPerson.getTimes());
		}
		
		@Test
		void crudComplexType_overrideColumnName() {
			Table totoTable = new Table("toto");
			Column idColumn = totoTable.addColumn("id", Identifier.class);
			dialect.getColumnBinderRegistry().register(idColumn, Identifier.identifierBinder(DefaultParameterBinders.UUID_PARAMETER_BINDER));
			dialect.getJavaTypeToSqlTypeMapping().put(idColumn, "VARCHAR(255)");
			
			IEntityConfiguredPersister<Toto, Identifier> personPersister = MappingEase.entityBuilder(Toto.class, Identifier.class)
					.add(Toto::getId).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.add(Toto::getName)
					.addCollection(Toto::getTimes, Timestamp.class, MappingEase.embeddableBuilder(Timestamp.class)
						.add(Timestamp::getCreationDate)
						.add(Timestamp::getModificationDate))
					.overrideName(Timestamp::getCreationDate, "createdAt")
					.build(persistenceContext);
			
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			Map<String, Table> tablePerName = Iterables.map(tables, Table::getName);
			Table totoTimesTable = tablePerName.get("Toto_times");
			Map<String, Column> timesTableColumn = totoTimesTable.mapColumnsOnName();
			assertNotNull(timesTableColumn.get("createdAt"));
			
			Toto person = new Toto();
			person.setName("toto");
			Timestamp timestamp1 = new Timestamp(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1));
			Timestamp timestamp2 = new Timestamp(LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2));
			person.getTimes().add(timestamp1);
			person.getTimes().add(timestamp2);
			
			personPersister.insert(person);
			
			Toto loadedPerson = personPersister.select(person.getId());
			assertEquals(Arrays.asSet(timestamp1, timestamp2), loadedPerson.getTimes());
		}
	}
	
	/**
	 * Test to check that the API returns right Object which means:
	 * - interfaces are well written to return right types, so one can chain others methods
	 * - at runtime instance of the right type is also returned
	 * (avoid "java.lang.ClassCastException: com.sun.proxy.$Proxy10 cannot be cast to org.gama.stalactite.persistence.engine
	 * .IFluentEmbeddableMappingBuilder")
	 * <p>
	 * As many as possible combinations of method chaining should be done here, because all combination seems impossible, this test must be
	 * considered
	 * as a best effort, and any regression found in user code should be added here
	 */
	@Test
	void apiUsage() {
		try {
			MappingEase.entityBuilder(Country.class, long.class)
					.add(Country::getName).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.embed(Country::getPresident, MappingEase.embeddableBuilder(Person.class)
							.add(Person::getName))
					.overrideName(Person::getId, "personId")
					.overrideName(Person::getName, "personName")
					.embed(Country::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
							.add(Timestamp::getCreationDate)
							.add(Timestamp::getModificationDate))
					.add(Country::getId)
					.add(Country::setDescription, "zxx")
					.mapSuperClass(new FluentEmbeddableMappingConfigurationSupport<>(Country.class))
					.build(persistenceContext);
		} catch (RuntimeException e) {
			// Since we only want to test compilation, we don't care about that the above code throws an exception or not
		}
		
		try {
			MappingEase.entityBuilder(Country.class, long.class)
					.add(Country::getName).identifier(StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED)
					.embed(Country::getPresident, MappingEase.embeddableBuilder(Person.class)
							.add(Person::getName))
					.embed(Country::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
							.add(Timestamp::getCreationDate)
							.add(Timestamp::getModificationDate))
					.add(Country::getId, "zz")
					.addOneToOne(Country::getPresident, MappingEase.entityBuilder(Person.class, long.class))
					.mapSuperClass(new FluentEmbeddableMappingConfigurationSupport<>(Country.class))
					.add(Country::getDescription, "xx")
					.build(persistenceContext);
		} catch (RuntimeException e) {
			// Since we only want to test compilation, we don't care about that the above code throws an exception or not
		}
		
		try {
			MappingEase.entityBuilder(Country.class, long.class)
					.add(Country::getName)
					.add(Country::getId, "zz")
					.mapSuperClass((EmbeddableMappingConfigurationProvider<Country>) new FluentEmbeddableMappingConfigurationSupport<>(Country.class))
					// embed with setter
					.embed(Country::setPresident, MappingEase.embeddableBuilder(Person.class)
							.add(Person::getName))
					// embed with setter
					.embed(Country::setTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
							.add(Timestamp::getCreationDate)
							.add(Timestamp::getModificationDate))
					.addOneToManySet(Country::getCities, MappingEase.entityBuilder(City.class, long.class))
						// testing mappedBy with inheritance
						.mappedBy((SerializableFunction<City, AbstractCountry>) City::getAbstractCountry)
					.add(Country::getDescription, "xx")
					.add(Country::getDummyProperty, "dd")
					.build(persistenceContext);
		} catch (RuntimeException e) {
			// Since we only want to test compilation, we don't care about that the above code throws an exception or not
		}
		
		try {
			EmbeddableMappingConfigurationProvider<Person> personMappingBuilder = MappingEase.embeddableBuilder(Person.class)
					.add(Person::getName);
			
			MappingEase.entityBuilder(Country.class, long.class)
					.add(Country::getName)
					.add(Country::getId, "zz")
					.addOneToOne(Country::setPresident, MappingEase.entityBuilder(Person.class, long.class))
					.mapSuperClass((EmbeddableMappingConfigurationProvider<Country>) new FluentEmbeddableMappingConfigurationSupport<>(Country.class))
					// embed with setter
					.embed(Country::getPresident, personMappingBuilder)
					.build(persistenceContext);
		} catch (RuntimeException e) {
			// Since we only want to test compilation, we don't care about that the above code throws an exception or not
		}
		
		try {
			EmbeddableMappingConfigurationProvider<Person> personMappingBuilder = MappingEase.embeddableBuilder(Person.class)
					.add(Person::getName);
			
			MappingEase.entityBuilder(Country.class, long.class)
					.add(Country::getName)
					.add(Country::getId, "zz")
					.embed(Country::getPresident, personMappingBuilder)
					.mapSuperClass(new FluentEmbeddableMappingConfigurationSupport<>(Country.class))
					.addOneToOne(Country::setPresident, MappingEase.entityBuilder(Person.class, long.class))
					// reusing embeddable ...
					.embed(Country::getPresident, personMappingBuilder)
					// with getter override
					.overrideName(Person::getName, "toto")
					// with setter override
					.overrideName(Person::setName, "tata")
					.build(persistenceContext);
		} catch (RuntimeException e) {
			// Since we only want to test compilation, we don't care about that the above code throws an exception or not
		}
		
		class PersonTable extends Table<PersonTable> {
			
			Column<PersonTable, Gender> gender = addColumn("gender", Gender.class);
			Column<PersonTable, String> name = addColumn("name", String.class);
			
			PersonTable() {
				super("Person");
			}
		}
		PersonTable personTable = new PersonTable();
		try {
			MappingEase.entityBuilder(PersonWithGender.class, long.class)
					.add(Person::getName)
					.add(Person::getName, personTable.name)
					.addEnum(PersonWithGender::getGender).byOrdinal()
					.embed(Person::setTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
							.add(Timestamp::getCreationDate)
							.add(Timestamp::getModificationDate))
					.overrideName(Timestamp::getCreationDate, "myDate")
					.addEnum(PersonWithGender::getGender, "MM").byOrdinal()
					.addEnum(PersonWithGender::getGender, personTable.gender).byOrdinal()
					.add(PersonWithGender::getId, "zz")
					.addEnum(PersonWithGender::setGender).byName()
					.embed(Person::getTimestamp, MappingEase.embeddableBuilder(Timestamp.class)
							.add(Timestamp::getCreationDate)
							.add(Timestamp::getModificationDate))
					.addEnum(PersonWithGender::setGender, "MM").byName()
					.build(persistenceContext, new Table<>("person"));
		} catch (RuntimeException e) {
			// Since we only want to test compilation, we don't care about that the above code throws an exception or not
		}
	}
	
	protected static class Toto implements Identified<UUID> {
		
		private final Identifier<UUID> id;
		
		private Identifier<UUID> identifier;
		
		private String name;
		
		private String firstName;
		
		private Timestamp timestamp;
		
		private Set<State> possibleStates = new HashSet<>();
		
		private Set<Timestamp> times = new HashSet<>();
		
		private boolean setIdWasCalled;
		private boolean constructorWithIdWasCalled;
		
		public Toto() {
			id = new PersistableIdentifier<>(UUID.randomUUID());
		}
		
		public Toto(PersistedIdentifier<UUID> id) {
			this.id = id;
			this.constructorWithIdWasCalled = true;
		}
		
		public Toto(PersistedIdentifier<UUID> id, String name) {
			this.id = id;
			this.name = name + " by constructor";
		}
		
		public String getName() {
			return name;
		}
		
		public void setName(String name) {
			this.name = name;
		}
		
		public String getFirstName() {
			return name;
		}
		
		public Identifier<UUID> getIdentifier() {
			return identifier;
		}
		
		public void setIdentifier(Identifier<UUID> id) {
			this.identifier = id;
		}
		
		public Long getNoMatchingField() {
			return null;
		}
		
		public void setNoMatchingField(Long s) {
		}
		
		public long getNoMatchingFieldPrimitive() {
			return 0;
		}
		
		public void setNoMatchingFieldPrimitive(long s) {
		}
		
		@Override
		public Identifier<UUID> getId() {
			return id;
		}
		
		public void setId(Identifier<UUID> id) {
			// this method is a lure for default ReversibleAccessor mecanism because it matches getter by its name but does nothing special about id
			setIdWasCalled = true;
		}
		
		public boolean isSetIdWasCalled() {
			return setIdWasCalled;
		}
		
		public boolean isConstructorWithIdWasCalled() {
			return constructorWithIdWasCalled;
		}
		
		public Timestamp getTimestamp() {
			return timestamp;
		}
		
		public void setTimestamp(Timestamp timestamp) {
			this.timestamp = timestamp;
		}
		
		public Set<State> getPossibleStates() {
			return possibleStates;
		}
		
		public void setPossibleStates(Set<State> possibleStates) {
			this.possibleStates = possibleStates;
		}
		
		public Set<Timestamp> getTimes() {
			return times;
		}
		
		public void setTimes(Set<Timestamp> times) {
			this.times = times;
		}
	}
	
	private enum State {
		TODO,
		IN_PROGRESS,
		DONE
	}
	
	static class TimestampWithLocale extends Timestamp {
		
		private Locale locale;
		
		TimestampWithLocale() {
			this(null, null, null);
		}
		
		public TimestampWithLocale(Date creationDate, Date modificationDate, Locale locale) {
			super(creationDate, modificationDate);
			this.locale = locale;
		}
		
		public Locale getLocale() {
			return locale;
		}
		
		public TimestampWithLocale setLocale(Locale locale) {
			this.locale = locale;
			return this;
		}
	}
	
}
