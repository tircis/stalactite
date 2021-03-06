package org.gama.stalactite.persistence.engine.configurer;

import java.util.HashMap;
import java.util.Map;

import org.gama.lang.Reflections;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.lang.exception.NotImplementedException;
import org.gama.reflection.IReversibleAccessor;
import org.gama.reflection.ValueAccessPointSet;
import org.gama.stalactite.persistence.engine.AssociationTableNamingStrategy;
import org.gama.stalactite.persistence.engine.ColumnNamingStrategy;
import org.gama.stalactite.persistence.engine.ElementCollectionTableNamingStrategy;
import org.gama.stalactite.persistence.engine.ForeignKeyNamingStrategy;
import org.gama.stalactite.persistence.engine.PersisterRegistry;
import org.gama.stalactite.persistence.engine.PolymorphismPolicy;
import org.gama.stalactite.persistence.engine.PolymorphismPolicy.JoinedTablesPolymorphism;
import org.gama.stalactite.persistence.engine.SubEntityMappingConfiguration;
import org.gama.stalactite.persistence.engine.TableNamingStrategy;
import org.gama.stalactite.persistence.engine.configurer.BeanMappingBuilder.ColumnNameProvider;
import org.gama.stalactite.persistence.engine.configurer.PersisterBuilderImpl.Identification;
import org.gama.stalactite.persistence.engine.configurer.PersisterBuilderImpl.MappingPerTable.Mapping;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredJoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.JoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.JoinedTablesPolymorphicPersister;
import org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree;
import org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree.EntityMerger.EntityMergerAdapter;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.sql.IConnectionConfiguration;
import org.gama.stalactite.persistence.sql.dml.binder.ColumnBinderRegistry;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.PrimaryKey;
import org.gama.stalactite.persistence.structure.Table;

import static org.gama.lang.Nullable.nullable;

/**
 * @author Guillaume Mary
 */
class JoinedTablesPolymorphismBuilder<C, I, T extends Table> extends AbstractPolymorphicPersisterBuilder<C, I, T> {
	
	private final JoinedTablesPolymorphism<C> joinedTablesPolymorphism;
	private final PrimaryKey mainTablePrimaryKey;
	
	JoinedTablesPolymorphismBuilder(JoinedTablesPolymorphism<C> polymorphismPolicy,
									Identification identification,
									IEntityConfiguredJoinedTablesPersister<C, I> mainPersister,
									ColumnBinderRegistry columnBinderRegistry,
									ColumnNameProvider columnNameProvider,
									TableNamingStrategy tableNamingStrategy,
									ColumnNamingStrategy columnNamingStrategy,
									ForeignKeyNamingStrategy foreignKeyNamingStrategy,
									ElementCollectionTableNamingStrategy elementCollectionTableNamingStrategy,
									ColumnNamingStrategy joinColumnNamingStrategy,
									ColumnNamingStrategy indexColumnNamingStrategy,
									AssociationTableNamingStrategy associationTableNamingStrategy) {
		super(polymorphismPolicy, identification, mainPersister, columnBinderRegistry, columnNameProvider, columnNamingStrategy, foreignKeyNamingStrategy,
				elementCollectionTableNamingStrategy, joinColumnNamingStrategy, indexColumnNamingStrategy, associationTableNamingStrategy, tableNamingStrategy);
		this.joinedTablesPolymorphism = polymorphismPolicy;
		this.mainTablePrimaryKey = this.mainPersister.getMappingStrategy().getTargetTable().getPrimaryKey();
	}
	
	@Override
	public IEntityConfiguredJoinedTablesPersister<C, I> build(Dialect dialect, IConnectionConfiguration connectionConfiguration, PersisterRegistry persisterRegistry) {
		Map<Class<? extends C>, IEntityConfiguredJoinedTablesPersister<C, I>> persisterPerSubclass = new HashMap<>();
		
		BeanMappingBuilder beanMappingBuilder = new BeanMappingBuilder();
		for (SubEntityMappingConfiguration<? extends C> subConfiguration : joinedTablesPolymorphism.getSubClasses()) {
			IEntityConfiguredJoinedTablesPersister<? extends C, I> subclassPersister;
			
			// first we'll use table of columns defined in embedded override
			// then the one defined by inheritance
			// if both are null we'll create a new one
			Table tableDefinedByColumnOverride = BeanMappingBuilder.giveTargetTable(subConfiguration.getPropertiesMapping());
			Table tableDefinedByInheritanceConfiguration = joinedTablesPolymorphism.giveTable(subConfiguration);
			
			assertNullOrEqual(tableDefinedByColumnOverride, tableDefinedByInheritanceConfiguration);
			
			Table subTable = nullable(tableDefinedByColumnOverride)
					.elseSet(tableDefinedByInheritanceConfiguration)
					.getOr(() -> new Table(tableNamingStrategy.giveName(subConfiguration.getEntityType())));
			
			Map<IReversibleAccessor, Column> subEntityPropertiesMapping = beanMappingBuilder.build(subConfiguration.getPropertiesMapping(), subTable,
					this.columnBinderRegistry, this.columnNameProvider);
			addPrimarykey(subTable);
			addForeignKey(subTable);
			Mapping subEntityMapping = new Mapping(subConfiguration, subTable, subEntityPropertiesMapping, false);
			addIdentificationToMapping(identification, subEntityMapping);
			ClassMappingStrategy<? extends C, I, Table> classMappingStrategy = PersisterBuilderImpl.createClassMappingStrategy(
					false,
					subTable,
					subEntityPropertiesMapping,
					new ValueAccessPointSet(),	// TODO: implement properties set by constructor feature in joined-tables polymorphism
					identification,
					subConfiguration.getPropertiesMapping().getBeanType(),
					null);
			
			// NB: persisters are not registered into PersistenceContext because it may break implicit polymorphism principle (persisters are then
			// available by PersistenceContext.getPersister(..)) and it is not sure that they are perfect ones (all their features should be tested)
			subclassPersister = new JoinedTablesPersister(classMappingStrategy, dialect, connectionConfiguration);
			
			// Adding join with parent table to select
			Column subEntityPrimaryKey = (Column) Iterables.first(subclassPersister.getMappingStrategy().getTargetTable().getPrimaryKey().getColumns());
			Column entityPrimaryKey = (Column) Iterables.first(this.mainTablePrimaryKey.getColumns());
			subclassPersister.getEntityJoinTree().addMergeJoin(EntityJoinTree.ROOT_STRATEGY_NAME,
					new EntityMergerAdapter<C, Table>(mainPersister.getMappingStrategy()),
					subEntityPrimaryKey, entityPrimaryKey);
			
			persisterPerSubclass.put(subConfiguration.getEntityType(), (IEntityConfiguredJoinedTablesPersister<C, I>) subclassPersister);
		}
		
		registerCascades(persisterPerSubclass, dialect, connectionConfiguration, persisterRegistry);
		
		JoinedTablesPolymorphicPersister<C, I> surrogate = new JoinedTablesPolymorphicPersister<>(
				mainPersister, persisterPerSubclass, connectionConfiguration.getConnectionProvider(),
				dialect);
		return surrogate;
	}
	
	@Override
	protected void assertSubPolymorphismIsSupported(PolymorphismPolicy<? extends C> subPolymorphismPolicy) {
		// Everything else than joined-tables and single-table is not implemented (meaning table-per-class)
		// Written as a negative condition to explicitly say what we support
		if (!(subPolymorphismPolicy instanceof JoinedTablesPolymorphism
				|| subPolymorphismPolicy instanceof PolymorphismPolicy.SingleTablePolymorphism)) {
			throw new NotImplementedException("Combining joined-tables polymorphism policy with " + Reflections.toString(subPolymorphismPolicy.getClass()));
		}
	}
	
	private void addPrimarykey(Table table) {
		PersisterBuilderImpl.propagatePrimarykey(this.mainTablePrimaryKey, Arrays.asSet(table));
	}
	
	private void addForeignKey(Table table) {
		PersisterBuilderImpl.applyForeignKeys(this.mainTablePrimaryKey, this.foreignKeyNamingStrategy, Arrays.asSet(table));
	}
	
	private void addIdentificationToMapping(Identification identification, Mapping mapping) {
		PersisterBuilderImpl.addIdentificationToMapping(identification, Arrays.asSet(mapping));
	}
}
