package org.gama.stalactite.persistence.engine;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.stalactite.persistence.engine.model.AbstractVehicle;
import org.gama.stalactite.persistence.engine.model.Car;
import org.gama.stalactite.persistence.engine.model.Color;
import org.gama.stalactite.persistence.engine.model.Vehicle;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredPersister;
import org.gama.stalactite.persistence.id.Identifier;
import org.gama.stalactite.persistence.id.PersistedIdentifier;
import org.gama.stalactite.persistence.sql.HSQLDBDialect;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.sql.ConnectionProvider;
import org.gama.stalactite.sql.binder.LambdaParameterBinder;
import org.gama.stalactite.sql.binder.NullAwareParameterBinder;
import org.gama.stalactite.sql.test.HSQLDBInMemoryDataSource;
import org.gama.stalactite.test.JdbcConnectionProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.gama.stalactite.persistence.engine.MappingEase.embeddableBuilder;
import static org.gama.stalactite.persistence.engine.MappingEase.entityBuilder;
import static org.gama.stalactite.persistence.id.Identifier.LONG_TYPE;
import static org.gama.stalactite.persistence.id.Identifier.identifierBinder;
import static org.gama.stalactite.persistence.id.StatefullIdentifierAlreadyAssignedIdentifierPolicy.ALREADY_ASSIGNED;
import static org.gama.stalactite.query.model.QueryEase.select;
import static org.gama.stalactite.sql.binder.DefaultParameterBinders.INTEGER_PRIMITIVE_BINDER;
import static org.gama.stalactite.sql.binder.DefaultParameterBinders.LONG_PRIMITIVE_BINDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Guillaume Mary
 */
public class FluentEntityMappingConfigurationSupportInheritanceTest {
	
	private static final HSQLDBDialect DIALECT = new HSQLDBDialect();
	private final DataSource dataSource = new HSQLDBInMemoryDataSource();
	private final ConnectionProvider connectionProvider = new JdbcConnectionProvider(dataSource);
	private PersistenceContext persistenceContext;
	
	@BeforeAll
	static void initBinders() {
		// binder creation for our identifier
		DIALECT.getColumnBinderRegistry().register((Class) Identifier.class, identifierBinder(LONG_PRIMITIVE_BINDER));
		DIALECT.getJavaTypeToSqlTypeMapping().put(Identifier.class, "int");
		DIALECT.getColumnBinderRegistry().register(Color.class, new NullAwareParameterBinder<>(new LambdaParameterBinder<>(INTEGER_PRIMITIVE_BINDER, Color::new, Color::getRgb)));
		DIALECT.getJavaTypeToSqlTypeMapping().put(Color.class, "int");
	}
	
	@BeforeEach
	public void beforeTest() {
		persistenceContext = new PersistenceContext(connectionProvider, DIALECT);
	}
	
	@Nested
	class MappedSuperClass {
		
		@Test
		void simpleCase() {
			MappedSuperClassData mappedSuperClassData = new MappedSuperClassData();
			
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					// concrete class defines id
					.add(Car::getId).identifier(ALREADY_ASSIGNED)
					.mapSuperClass(MappingEase.embeddableBuilder(Vehicle.class)
							.add(Vehicle::getColor))
					.build(persistenceContext);
			
			// as a mapped super class, the table shouldn't be in the context, nor its persister exists
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			assertFalse(tables.contains(mappedSuperClassData.vehicleTable));
			assertNull(persistenceContext.getPersister(Vehicle.class));
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.select(Car::new, mappedSuperClassData.carTable.idColumn, m -> m
					.add(mappedSuperClassData.carTable.modelColumn, Car::setModel)
					.add(mappedSuperClassData.carTable.colorColumn, Car::setColor));
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void simpleCase_withTableDefinedInSuperClass() {
			MappedSuperClassData mappedSuperClassData = new MappedSuperClassData();
			
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					// concrete class defines id
					.add(Car::getId).identifier(ALREADY_ASSIGNED)
					.mapSuperClass(MappingEase.embeddableBuilder(Vehicle.class)
							.add(Vehicle::getColor))
					.build(persistenceContext);
			
			// as a mapped super class, the table shouldn't be in the context, nor its persister exists
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			assertFalse(tables.contains(mappedSuperClassData.vehicleTable));
			assertNull(persistenceContext.getPersister(Vehicle.class));
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.select(Car::new, mappedSuperClassData.carTable.idColumn, m -> m
					.add(mappedSuperClassData.carTable.modelColumn, Car::setModel)
					.add(mappedSuperClassData.carTable.colorColumn, Car::setColor));
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void columnNamingStrategyChanged() {
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					.withColumnNaming(accessor -> ColumnNamingStrategy.DEFAULT.giveName(accessor) + "_col")
					// concrete class defines id
					.add(Car::getId).identifier(ALREADY_ASSIGNED)
					.mapSuperClass(MappingEase.embeddableBuilder(Vehicle.class)
							.add(Vehicle::getColor))
					.build(persistenceContext);
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.newQuery("select id_col, model_col, color_col from Car", Car.class)
					.mapKey(Car::new, "id_col", (Class<Identifier<Long>>) (Class) Identifier.class)
					.map("model_col", Car::setModel)
					.map("color_col", Car::setColor)
					.execute();
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void columnNamingStrategyChanged_inParent() {
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					// concrete class defines id
					.add(Car::getId).identifier(ALREADY_ASSIGNED)
					.mapSuperClass(MappingEase
							.embeddableBuilder(Vehicle.class)
							.add(Vehicle::getColor)
							.withColumnNaming(accessor -> ColumnNamingStrategy.DEFAULT.giveName(accessor) + "_supercol"))
					.build(persistenceContext);
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.newQuery("select id_supercol, model_supercol, color_supercol from Car", Car.class)
					.mapKey(Car::new, "id_supercol", (Class<Identifier<Long>>) (Class) Identifier.class)
					.map("model_supercol", Car::setModel)
					.map("color_supercol", Car::setColor)
					.execute();
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void columnNamingStrategyChanged_inBoth_lowestTakesPriority() {
			ColumnNamingStrategy columnNamingStrategy = accessor -> {
				if (accessor.getName().contains("Color")) {
					return ColumnNamingStrategy.DEFAULT.giveName(accessor) + "_superCol";
				} else {
					return ColumnNamingStrategy.DEFAULT.giveName(accessor) + "_col";
				}
			};
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					.withColumnNaming(columnNamingStrategy)
					// concrete class defines id
					.add(Car::getId).identifier(ALREADY_ASSIGNED)
					.mapSuperClass(MappingEase
							.embeddableBuilder(Vehicle.class)
							.add(Vehicle::getColor))
					.build(persistenceContext);
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.newQuery("select id_col, model_col, color_col from Car", Car.class)
					.mapKey(Car::new, "id_col", (Class<Identifier<Long>>) (Class) Identifier.class)
					.map("model_col", Car::setModel)
					.map("color_col", Car::setColor)
					.execute();
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void withoutIdDefined_throwsException() {
			UnsupportedOperationException thrownException = assertThrows(UnsupportedOperationException.class,
					() -> entityBuilder(Car.class, LONG_TYPE)
							.add(Car::getModel)
							.add(Car::getColor)
							.mapSuperClass(MappingEase
									.embeddableBuilder(Vehicle.class)
									.add(Vehicle::getColor))
							.build(persistenceContext));
			
			assertEquals("Identifier is not defined for o.g.s.p.e.m.Car,"
							+ " please add one throught o.g.s.p.e.ColumnOptions.identifier(o.g.s.p.e.ColumnOptions$IdentifierPolicy)",
					thrownException.getMessage());
		}
		
		@Test
		void withEmbeddable() {
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					// concrete class defines id
					.add(Car::getId).identifier(ALREADY_ASSIGNED)
					.mapSuperClass(MappingEase
							.embeddableBuilder(Vehicle.class)
							.embed(Vehicle::getColor, embeddableBuilder(Color.class)
									.add(Color::getRgb)))
					.build(persistenceContext);
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
			
			// checking with query to understand what's under the hood : rgb column is created instead of color
			List<Car> allCars = persistenceContext.newQuery("select id, model, rgb from Car", Car.class)
					.mapKey(Car::new, "id", (Class<Identifier<Long>>) (Class) Identifier.class)
					.map("model", Car::setModel)
					.map("rgb", Car::setColor, int.class, Color::new)
					.execute();
			assertEquals(Arrays.asList(dummyCar), allCars);
		}
	}
	
	@Nested
	class Inheritance {
		
		@Test
		void withIdDefinedInSuperClass() {
			MappedSuperClassData mappedSuperClassData = new MappedSuperClassData();
			
			EntityMappingConfiguration<Vehicle, Identifier<Long>> inheritanceConfiguration = entityBuilder(Vehicle.class, LONG_TYPE)
					// mapped super class defines id
					.add(Vehicle::getId).identifier(ALREADY_ASSIGNED)
					.getConfiguration();
			
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					.add(Car::getColor)	// note : we don't need to embed Color because it is defined in the Dialect registry
					.mapInheritance(inheritanceConfiguration)
					.build(persistenceContext);
			
			// as an inherited entity of non joined_tables policy, the table should not be in the context, but its persister does exist
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			assertFalse(tables.contains(mappedSuperClassData.vehicleTable));
			assertNotNull(persistenceContext.getPersister(Vehicle.class));
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.select(Car::new, mappedSuperClassData.carTable.idColumn, m -> m
					.add(mappedSuperClassData.carTable.modelColumn, Car::setModel)
					.add(mappedSuperClassData.carTable.colorColumn, Car::setColor));
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void identifierIsRedefined_throwsException() {
			EntityMappingConfiguration<AbstractVehicle, Identifier<Long>> inheritanceConfiguration = entityBuilder(AbstractVehicle.class, LONG_TYPE)
					.add(AbstractVehicle::getId).identifier(ALREADY_ASSIGNED).getConfiguration();
			
			MappingConfigurationException thrownException = assertThrows(MappingConfigurationException.class,
					() -> entityBuilder(Vehicle.class, LONG_TYPE)
							.mapInheritance(inheritanceConfiguration)
							.add(Vehicle::getId).identifier(ALREADY_ASSIGNED)
							.build(persistenceContext));
			assertEquals("Defining an identifier while inheritance is used is not supported" 
					+ " : o.g.s.p.e.m.Vehicle defines identifier AbstractVehicle::getId" 
					+ " while it inherits from o.g.s.p.e.m.AbstractVehicle", thrownException.getMessage());
		}
		
		@Test
		void multipleInheritance() {
			MappedSuperClassData mappedSuperClassData = new MappedSuperClassData();
			
			EntityMappingConfiguration<AbstractVehicle, Identifier<Long>> inheritanceConfiguration = entityBuilder(AbstractVehicle.class, LONG_TYPE)
					.add(AbstractVehicle::getId).identifier(ALREADY_ASSIGNED)
					.getConfiguration();
			
			EntityMappingConfiguration<Vehicle, Identifier<Long>> inheritanceConfiguration2 = entityBuilder(Vehicle.class, LONG_TYPE)
					.mapInheritance(inheritanceConfiguration)
					.getConfiguration();
			
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					.add(Car::getColor)
					.mapInheritance(inheritanceConfiguration2)
					.build(persistenceContext);
			
			// as an inherited entity, the table should not be in the context, and its persister does exist
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			assertFalse(tables.contains(mappedSuperClassData.vehicleTable));
			assertTrue(tables.contains(mappedSuperClassData.carTable));
			assertNotNull(persistenceContext.getPersister(Vehicle.class));
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.select(Car::new, mappedSuperClassData.carTable.idColumn, m -> m
					.add(mappedSuperClassData.carTable.modelColumn, Car::setModel)
					.add(mappedSuperClassData.carTable.colorColumn, Car::setColor));
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void multipleInheritance_joinedTables() {
			MappedSuperClassData mappedSuperClassData = new MappedSuperClassData();
			
			EntityMappingConfiguration<AbstractVehicle, Identifier<Long>> inheritanceConfiguration = entityBuilder(AbstractVehicle.class, LONG_TYPE)
					.add(AbstractVehicle::getId).identifier(ALREADY_ASSIGNED)
					.getConfiguration();
			
			EntityMappingConfiguration<Vehicle, Identifier<Long>> inheritanceConfiguration2 = entityBuilder(Vehicle.class, LONG_TYPE)
					.mapInheritance(inheritanceConfiguration).withJoinedTable()
					.getConfiguration();
			
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					.add(Car::getColor)
					.mapInheritance(inheritanceConfiguration2).withJoinedTable()
					.build(persistenceContext);
			
			// as an inherited entity, the table should be in the context, and its persister does exist
			assertEquals(Arrays.asHashSet("Car", "Vehicle", "AbstractVehicle"),
					DDLDeployer.collectTables(persistenceContext).stream().map(Table::getName).collect(Collectors.toSet()));
			assertEquals("Car", ((IEntityConfiguredPersister) persistenceContext.getPersister(Car.class)).getMappingStrategy().getTargetTable().getName());
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.select(Car::new, mappedSuperClassData.carTable.idColumn, m -> m
					.add(mappedSuperClassData.carTable.modelColumn, Car::setModel)
					.add(mappedSuperClassData.carTable.colorColumn, Car::setColor));
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void mappedSuperClass_and_entityInheritance_throwsException() {
			EntityMappingConfiguration<AbstractVehicle, Identifier<Long>> inheritanceConfiguration = entityBuilder(AbstractVehicle.class, LONG_TYPE)
					// mapped super class defines id
					.add(AbstractVehicle::getId).identifier(ALREADY_ASSIGNED)
					.getConfiguration();
			
			IFluentEntityMappingBuilder<Car, Identifier<Long>> mappingBuilder = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					.add(Car::getColor)
					.mapSuperClass(MappingEase.embeddableBuilder(Vehicle.class)
							.add(Vehicle::getColor))
					.mapInheritance(inheritanceConfiguration);
			MappingConfigurationException thrownException = assertThrows(MappingConfigurationException.class,
					() -> mappingBuilder.build(persistenceContext));
			assertEquals("Combination of mapped super class and inheritance is not supported, please remove one of them", thrownException.getMessage());
		}
		
		@Test
		void joinedTables() {
			MappedSuperClassData mappedSuperClassData = new MappedSuperClassData();
			
			EntityMappingConfiguration<Vehicle, Identifier<Long>> inheritanceConfiguration = entityBuilder(Vehicle.class, LONG_TYPE)
					// mapped super class defines id
					.add(Vehicle::getId).identifier(ALREADY_ASSIGNED)
					.add(Vehicle::getColor)
					.getConfiguration();
			
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					.mapInheritance(inheritanceConfiguration)
					.withJoinedTable()
					.build(persistenceContext, mappedSuperClassData.carTable);
			
			// as an inherited entity, the table should be in the context
			Collection<Table> tables = DDLDeployer.collectTables(persistenceContext);
			assertTrue(Iterables.collectToList(tables, Table::getName).contains(Vehicle.class.getSimpleName()));
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			List<Car> allCars = persistenceContext.newQuery(select(mappedSuperClassData.carTable.idColumn, mappedSuperClassData.carTable.modelColumn, mappedSuperClassData.vehicleTable.colorColumn)
					.from(mappedSuperClassData.carTable).innerJoin(mappedSuperClassData.carTable.idColumn, mappedSuperClassData.vehicleTable.idColumn), Car.class)
					.mapKey(Car::new, mappedSuperClassData.carTable.idColumn)
					.map(mappedSuperClassData.carTable.modelColumn, Car::setModel)
					.map(mappedSuperClassData.vehicleTable.colorColumn, Car::setColor)
					.execute();
			assertEquals(Arrays.asList(dummyCar), allCars);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
		}
		
		@Test
		void withEmbeddable() {
			EntityMappingConfiguration<Vehicle, Identifier<Long>> inheritanceConfiguration = entityBuilder(Vehicle.class, LONG_TYPE)
					.add(Vehicle::getId).identifier(ALREADY_ASSIGNED)
					.embed(Vehicle::getColor, embeddableBuilder(Color.class)
							.add(Color::getRgb))
					.getConfiguration();
			
			IEntityPersister<Car, Identifier<Long>> carPersister = entityBuilder(Car.class, LONG_TYPE)
					.add(Car::getModel)
					.mapInheritance(inheritanceConfiguration)
					.build(persistenceContext);
			
			// DML tests
			DDLDeployer ddlDeployer = new DDLDeployer(persistenceContext);
			ddlDeployer.deployDDL();
			
			Car dummyCar = new Car(1L);
			dummyCar.setModel("Renault");
			dummyCar.setColor(new Color(666));
			
			// insert test
			carPersister.insert(dummyCar);
			
			// select test
			Car loadedCar = carPersister.select(new PersistedIdentifier<>(1L));
			assertEquals(dummyCar, loadedCar);
			
			// checking with query to understand what's under the hood : rgb column is created instead of color
			List<Car> allCars = persistenceContext.newQuery("select id, model, rgb from Car", Car.class)
					.mapKey(Car::new, "id", (Class<Identifier<Long>>) (Class) Identifier.class)
					.map("model", Car::setModel)
					.map("rgb", Car::setColor, int.class, Color::new)
					.execute();
			assertEquals(Arrays.asList(dummyCar), allCars);
		}
	}
	
	/**
	 * Data and method for common tests of building a strategy from a mapped super class
	 */
	static class MappedSuperClassData {
		
		abstract class AbstractVehicleTable<SELF extends AbstractVehicleTable<SELF>> extends Table<SELF> {
			final Column<SELF, Identifier<Long>> idColumn = addColumn("id", (Class<Identifier<Long>>) (Class) Identifier.class).primaryKey();
			final Column<SELF, Color> colorColumn = addColumn("color", Color.class);
			
			public AbstractVehicleTable(String name) {
				super(name);
			}
		}
		
		class VehicleTable extends AbstractVehicleTable<VehicleTable> {
			
			public VehicleTable(String name) {
				super(name);
			}
		}
		
		class CarTable extends AbstractVehicleTable<CarTable> {
			final Column<CarTable, String> modelColumn = addColumn("model", String.class);
			
			public CarTable(String name) {
				super(name);
			}
		}
		
		private final VehicleTable vehicleTable = new VehicleTable("vehicle");
		
		private final CarTable carTable = new CarTable("car");
	}
	
}
