package org.gama.stalactite.persistence.engine.configurer;

import javax.annotation.Nullable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.danekja.java.util.function.serializable.SerializableBiConsumer;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.lang.Reflections;
import org.gama.lang.StringAppender;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.reflection.AccessorByMember;
import org.gama.reflection.AccessorByMethod;
import org.gama.reflection.AccessorDefinition;
import org.gama.reflection.Accessors;
import org.gama.reflection.IMutator;
import org.gama.reflection.IReversibleAccessor;
import org.gama.reflection.MethodReferenceCapturer;
import org.gama.reflection.MutatorByMethod;
import org.gama.reflection.PropertyAccessor;
import org.gama.reflection.ValueAccessPointMap;
import org.gama.stalactite.persistence.engine.AssociationTableNamingStrategy;
import org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode;
import org.gama.stalactite.persistence.engine.ColumnNamingStrategy;
import org.gama.stalactite.persistence.engine.ForeignKeyNamingStrategy;
import org.gama.stalactite.persistence.engine.MappingConfigurationException;
import org.gama.stalactite.persistence.engine.NotYetSupportedOperationException;
import org.gama.stalactite.persistence.engine.PersisterRegistry;
import org.gama.stalactite.persistence.engine.runtime.AbstractOneToManyWithAssociationTableEngine;
import org.gama.stalactite.persistence.engine.runtime.AssociationRecord;
import org.gama.stalactite.persistence.engine.runtime.AssociationRecordPersister;
import org.gama.stalactite.persistence.engine.runtime.AssociationTable;
import org.gama.stalactite.persistence.engine.runtime.BeanRelationFixer;
import org.gama.stalactite.persistence.engine.runtime.IConfiguredJoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredJoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.IndexedAssociationRecord;
import org.gama.stalactite.persistence.engine.runtime.IndexedAssociationTable;
import org.gama.stalactite.persistence.engine.runtime.IndexedMappedManyRelationDescriptor;
import org.gama.stalactite.persistence.engine.runtime.ManyRelationDescriptor;
import org.gama.stalactite.persistence.engine.runtime.MappedManyRelationDescriptor;
import org.gama.stalactite.persistence.engine.runtime.OneToManyWithAssociationTableEngine;
import org.gama.stalactite.persistence.engine.runtime.OneToManyWithIndexedAssociationTableEngine;
import org.gama.stalactite.persistence.engine.runtime.OneToManyWithIndexedMappedAssociationEngine;
import org.gama.stalactite.persistence.engine.runtime.OneToManyWithMappedAssociationEngine;
import org.gama.stalactite.persistence.mapping.IEntityMappingStrategy;
import org.gama.stalactite.persistence.mapping.IMappingStrategy.ShadowColumnValueProvider;
import org.gama.stalactite.persistence.mapping.IdAccessor;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.sql.IConnectionConfiguration;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.PrimaryKey;
import org.gama.stalactite.persistence.structure.Table;

import static org.gama.lang.Nullable.nullable;
import static org.gama.lang.collection.Iterables.first;
import static org.gama.reflection.Accessors.accessor;
import static org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode.ALL_ORPHAN_REMOVAL;
import static org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode.ASSOCIATION_ONLY;
import static org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode.READ_ONLY;

/**
 * @param <SRC> type of input (left/source entities)
 * @param <TRGT> type of output (right/target entities)
 * @param <SRCID> identifier type of source entities
 * @param <TRGTID> identifier type of target entities
 * @param <C> collection type of the relation
 * @author Guillaume Mary
 */
public class CascadeManyConfigurer<SRC, TRGT, SRCID, TRGTID, C extends Collection<TRGT>> {
	
	private final Dialect dialect;
	private final IConnectionConfiguration connectionConfiguration;
	private final PersisterRegistry persisterRegistry;
	private Column<?, SRCID> sourcePrimaryKey;
	private final ConfigurerTemplate<SRC, TRGT, SRCID, TRGTID, C> configurer;
	
	public CascadeManyConfigurer(CascadeMany<SRC, TRGT, TRGTID, C> cascadeMany,
								 IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister,
								 Dialect dialect,
								 IConnectionConfiguration connectionConfiguration,
								 PersisterRegistry persisterRegistry,
								 ForeignKeyNamingStrategy foreignKeyNamingStrategy,
								 ColumnNamingStrategy joinColumnNamingStrategy,
								 AssociationTableNamingStrategy associationTableNamingStrategy,
								 ColumnNamingStrategy indexColumnNamingStrategy) {
		this.dialect = dialect;
		this.connectionConfiguration = connectionConfiguration;
		this.persisterRegistry = persisterRegistry;
		
		Column leftPrimaryKey = nullable(sourcePrimaryKey).getOr(() -> lookupSourcePrimaryKey(sourcePersister));
		
		RelationMode maintenanceMode = cascadeMany.getRelationMode();
		// selection is always present (else configuration is nonsense !)
		boolean orphanRemoval = maintenanceMode == ALL_ORPHAN_REMOVAL;
		boolean writeAuthorized = maintenanceMode != READ_ONLY;
		
		ManyAssociationConfiguration<SRC, TRGT, SRCID, TRGTID, C> manyAssociationConfiguration = new ManyAssociationConfiguration<>(cascadeMany,
				sourcePersister, leftPrimaryKey,
				foreignKeyNamingStrategy, joinColumnNamingStrategy, indexColumnNamingStrategy,
				orphanRemoval, writeAuthorized);
		if (cascadeMany.isOwnedByReverseSide()) {
			// case : reverse property is defined through one of the setter, getter or column on the reverse side
			if (maintenanceMode == ASSOCIATION_ONLY) {
				throw new MappingConfigurationException(RelationMode.ASSOCIATION_ONLY + " is only relevent with an association table");
			}
			configurer = new CascadeManyWithMappedAssociationConfigurer<>(manyAssociationConfiguration, orphanRemoval);
		} else {
			configurer = new CascadeManyWithAssociationTableConfigurer<>(manyAssociationConfiguration,
					associationTableNamingStrategy,
					dialect,
					maintenanceMode == ASSOCIATION_ONLY,
					connectionConfiguration);
		}
	}
	
	/**
	 * Sets source primary key. Necessary for foreign key creation in case of inheritance and many relation defined for each subclass :
	 * by default a foreign key will be created from target entity table to subclass source primary key which will create several one pointing
	 * to different table, hence when inserting target entity the column owning relation should point to each subclass table, which is not possible,
	 * throwing a foreign key violation.
	 * 
	 * @param sourcePrimaryKey column to which the foreign key from column that owns relation must point to
	 * @return this
	 */
	public CascadeManyConfigurer<SRC, TRGT, SRCID, TRGTID, C> setSourcePrimaryKey(Column<?, SRCID> sourcePrimaryKey) {
		this.sourcePrimaryKey = sourcePrimaryKey;
		return this;
	}
	
	public <T extends Table<T>> void appendCascade(CascadeMany<SRC, TRGT, TRGTID, C> cascadeMany,
												   IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister,
												   ForeignKeyNamingStrategy foreignKeyNamingStrategy,
												   ColumnNamingStrategy joinColumnNamingStrategy,
												   ColumnNamingStrategy indexColumnNamingStrategy,
												   AssociationTableNamingStrategy associationTableNamingStrategy,
												   PersisterBuilderImpl<TRGT, TRGTID> targetPersisterBuilder) {
		Table targetTable = determineTargetTable(cascadeMany);
		IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister = targetPersisterBuilder
				.build(dialect, connectionConfiguration, persisterRegistry, targetTable);
		
		appendCascade(cascadeMany, sourcePersister, foreignKeyNamingStrategy, joinColumnNamingStrategy, indexColumnNamingStrategy,
				associationTableNamingStrategy, targetPersister);
	}
	
	public ConfigurationResult<SRC, TRGT> appendCascadesWith2PhasesSelect(String tableAlias,
																		  IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
																		  FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener) {
		return this.configurer.appendCascadesWithSelectIn2Phases(tableAlias, targetPersister, firstPhaseCycleLoadListener);
	}
	
	void appendCascade(CascadeMany<SRC, TRGT, TRGTID, C> cascadeMany,
							  IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister,
							  ForeignKeyNamingStrategy foreignKeyNamingStrategy,
							  ColumnNamingStrategy joinColumnNamingStrategy,
							  ColumnNamingStrategy indexColumnNamingStrategy,
							  AssociationTableNamingStrategy associationTableNamingStrategy,
							  IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
		Column leftPrimaryKey = nullable(sourcePrimaryKey).getOr(() -> lookupSourcePrimaryKey(sourcePersister));
		
		RelationMode maintenanceMode = cascadeMany.getRelationMode();
		// selection is always present (else configuration is nonsense !)
		boolean orphanRemoval = maintenanceMode == ALL_ORPHAN_REMOVAL;
		boolean writeAuthorized = maintenanceMode != READ_ONLY;
		
		ManyAssociationConfiguration<SRC, TRGT, SRCID, TRGTID, C> manyAssociationConfiguration = new ManyAssociationConfiguration<>(cascadeMany,
				sourcePersister, leftPrimaryKey,
				foreignKeyNamingStrategy, joinColumnNamingStrategy, indexColumnNamingStrategy,
				orphanRemoval, writeAuthorized);
		ConfigurerTemplate<SRC, TRGT, SRCID, TRGTID, C> configurer;
		if (cascadeMany.isOwnedByReverseSide()) {
			// case : reverse property is defined through one of the setter, getter or column on the reverse side
			if (maintenanceMode == ASSOCIATION_ONLY) {
				throw new MappingConfigurationException(RelationMode.ASSOCIATION_ONLY + " is only relevent with an association table");
			}
			configurer = new CascadeManyWithMappedAssociationConfigurer<>(manyAssociationConfiguration, orphanRemoval);
		} else {
			configurer = new CascadeManyWithAssociationTableConfigurer<>(manyAssociationConfiguration,
					associationTableNamingStrategy,
					dialect,
					maintenanceMode == ASSOCIATION_ONLY,
					connectionConfiguration);
		}
		configurer.configure(targetPersister);
	}
	
	private Table determineTargetTable(CascadeMany<SRC, TRGT, TRGTID, C> cascadeMany) {
		Table reverseTable = nullable(cascadeMany.getReverseColumn()).map(Column::getTable).get();
		Table indexingTable = cascadeMany instanceof CascadeManyList
				? nullable(((CascadeManyList<?, ?, ?, ?>) cascadeMany).getIndexingColumn()).map(Column::getTable).get()
				: null;
		Set<Table> availableTables = Arrays.asHashSet(cascadeMany.getTargetTable(), reverseTable, indexingTable);
		availableTables.remove(null);
		if (availableTables.size() > 1) {
			class TableAppender extends StringAppender {
				@Override
				public StringAppender cat(Object o) {
					if (o instanceof Table) {
						return super.cat(((Table) o).getName());
					} else {
						return super.cat(o);
					}
				}
			}
			throw new MappingConfigurationException("Different tables used for configuring mapping : " + new TableAppender().ccat(availableTables, ", "));
		}
		
		// NB: even if no table is found in configuration, build(..) will create one
		return nullable(cascadeMany.getTargetTable()).elseSet(reverseTable).elseSet(indexingTable).get();
	}
	
	protected Column lookupSourcePrimaryKey(IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister) {
		// finding joined columns: left one is primary key. Right one is given by the target strategy through the property accessor
		if (sourcePersister.getMappingStrategy().getTargetTable().getPrimaryKey().getColumns().size() > 1) {
			throw new NotYetSupportedOperationException("Joining tables on a composed primary key is not (yet) supported");
		}
		return (Column) first(sourcePersister.getMappingStrategy().getTargetTable().getPrimaryKey().getColumns());
	}
	
	/**
	 * Class that stores elements necessary to one-to-many association configuration
	 */
	private static class ManyAssociationConfiguration<SRC, TRGT, SRCID, TRGTID, C extends Collection<TRGT>> {
		
		private final CascadeMany<SRC, TRGT, TRGTID, C> cascadeMany;
		private final IEntityConfiguredJoinedTablesPersister<SRC, SRCID> srcPersister;
		private final Column leftPrimaryKey;
		private final ForeignKeyNamingStrategy foreignKeyNamingStrategy;
		private final ColumnNamingStrategy joinColumnNamingStrategy;
		private final ColumnNamingStrategy indexColumnNamingStrategy;
		private final IReversibleAccessor<SRC, C> collectionGetter;
		private final IMutator<SRC, C> setter;
		private final boolean orphanRemoval;
		private final boolean writeAuthorized;
		
		private ManyAssociationConfiguration(CascadeMany<SRC, TRGT, TRGTID, C> cascadeMany,
											 IEntityConfiguredJoinedTablesPersister<SRC, SRCID> srcPersister,
											 Column leftPrimaryKey,
											 ForeignKeyNamingStrategy foreignKeyNamingStrategy,
											 ColumnNamingStrategy joinColumnNamingStrategy,
											 ColumnNamingStrategy indexColumnNamingStrategy,
											 boolean orphanRemoval,
											 boolean writeAuthorized) {
			this.cascadeMany = cascadeMany;
			this.srcPersister = srcPersister;
			this.leftPrimaryKey = leftPrimaryKey;
			this.foreignKeyNamingStrategy = foreignKeyNamingStrategy;
			this.joinColumnNamingStrategy = joinColumnNamingStrategy;
			this.indexColumnNamingStrategy = indexColumnNamingStrategy;
			this.collectionGetter = cascadeMany.getCollectionProvider();
			this.setter = collectionGetter.toMutator();
			// we don't use AccessorDefinition.giveMemberDefinition(..) because it gives a cross-member definition, loosing get/set for example,
			// whereas we need this information to build better association table name
			this.orphanRemoval = orphanRemoval;
			this.writeAuthorized = writeAuthorized;
		}
		
		/**
		 * Gives the collection factory used to instanciate relation field.
		 * 
		 * @return the one given by {@link CascadeMany#getCollectionFactory()} or one deduced from member signature
		 */
		protected Supplier<C> giveCollectionFactory() {
			Supplier<C> collectionFactory = cascadeMany.getCollectionFactory();
			if (collectionFactory == null) {
				collectionFactory = BeanRelationFixer.giveCollectionFactory((Class<C>) cascadeMany.getMethodReference().getPropertyType());
			}
			return collectionFactory;
		}
	}
	
	private static abstract class ConfigurerTemplate<SRC, TRGT, SRCID, TRGTID, C extends Collection<TRGT>> {
		
		protected final ManyAssociationConfiguration<SRC, TRGT, SRCID, TRGTID, C> manyAssociationConfiguration;
		
		/**
		 * Equivalent as cascadeMany.getMethodReference() but used for table and colum naming only.
		 * Collection access will be done through {@link ManyAssociationConfiguration#collectionGetter} and {@link ManyAssociationConfiguration#giveCollectionFactory()}
		 */
		protected AccessorDefinition accessorDefinition;
		
		protected ConfigurerTemplate(ManyAssociationConfiguration<SRC, TRGT, SRCID, TRGTID, C> manyAssociationConfiguration) {
			this.manyAssociationConfiguration = manyAssociationConfiguration;
		}
		
		void determineAccessorDefinition(IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			// we don't use AccessorDefinition.giveMemberDefinition(..) because it gives a cross-member definition, loosing get/set for example,
			// whereas we need this information to build better association table name
			this.accessorDefinition = new AccessorDefinition(
					manyAssociationConfiguration.cascadeMany.getMethodReference().getDeclaringClass(),
					AccessorDefinition.giveDefinition(manyAssociationConfiguration.cascadeMany.getMethodReference()).getName(),
					// we prefer target persister type to method reference member type because the latter only get's collection type which is not
					// an interesting information for table / column naming
					targetPersister.getClassToPersist());
		}
		
		abstract void configure(IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister);
		
		public abstract ConfigurationResult<SRC,TRGT> appendCascadesWithSelectIn2Phases(String tableAlias, IEntityConfiguredJoinedTablesPersister<TRGT,
				TRGTID> targetPersister, FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener);
	}
	
	/**
	 * Configurer dedicated to association that needs an intermediary table between source entities and these of the relation
	 */
	private static class CascadeManyWithAssociationTableConfigurer<SRC, TRGT, SRCID, TRGTID, C extends Collection<TRGT>> extends ConfigurerTemplate<SRC, TRGT, SRCID, TRGTID, C> {
		
		private final AssociationTableNamingStrategy associationTableNamingStrategy;
		private final Dialect dialect;
		private final boolean maintainAssociationOnly;
		private final IConnectionConfiguration connectionConfiguration;
		
		private AbstractOneToManyWithAssociationTableEngine<SRC, TRGT, SRCID, TRGTID, C, ? extends AssociationRecord, ? extends AssociationTable> associationTableEngine;
		
		private CascadeManyWithAssociationTableConfigurer(ManyAssociationConfiguration<SRC, TRGT, SRCID, TRGTID, C> manyAssociationConfiguration,
														  AssociationTableNamingStrategy associationTableNamingStrategy,
														  Dialect dialect,
														  boolean maintainAssociationOnly,
														  IConnectionConfiguration connectionConfiguration) {
			super(manyAssociationConfiguration);
			this.associationTableNamingStrategy = associationTableNamingStrategy;
			this.dialect = dialect;
			this.maintainAssociationOnly = maintainAssociationOnly;
			this.connectionConfiguration = connectionConfiguration;
		}
		
		private void prepare(IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			// case : Collection mapping without reverse property : an association table is needed
			Table<?> rightTable = targetPersister.getMappingStrategy().getTargetTable();
			Column rightPrimaryKey = first(rightTable.getPrimaryKey().getColumns());
			
			determineAccessorDefinition(targetPersister);
			String associationTableName = associationTableNamingStrategy.giveName(accessorDefinition,
					manyAssociationConfiguration.leftPrimaryKey, rightPrimaryKey);
			ManyRelationDescriptor<SRC, TRGT, C> manyRelationDescriptor = new ManyRelationDescriptor<>(
					manyAssociationConfiguration.collectionGetter::get, manyAssociationConfiguration.setter::set,
					manyAssociationConfiguration.giveCollectionFactory(),
					manyAssociationConfiguration.cascadeMany.getReverseLink());
			if (manyAssociationConfiguration.cascadeMany instanceof CascadeManyList) {
				assignEngineForIndexedAssociation(rightPrimaryKey, associationTableName, manyRelationDescriptor, targetPersister);
			} else {
				assignEngineForNonIndexedAssociation(rightPrimaryKey, associationTableName, manyRelationDescriptor, targetPersister);
			}
		}
		
		@Override
		void configure(IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			prepare(targetPersister);
			associationTableEngine.addSelectCascade(manyAssociationConfiguration.srcPersister);
			addWriteCascades(associationTableEngine);
		}
		
		@Override
		public ConfigurationResult<SRC,TRGT> appendCascadesWithSelectIn2Phases(String tableAlias,
																			   IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
																			   FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener) {
			prepare(targetPersister);
			associationTableEngine.addSelectCascadeIn2Phases(firstPhaseCycleLoadListener);
			addWriteCascades(associationTableEngine);
			return new ConfigurationResult<>(associationTableEngine.getManyRelationDescriptor().getRelationFixer(), manyAssociationConfiguration.srcPersister);
		}
		
		private void addWriteCascades(AbstractOneToManyWithAssociationTableEngine<SRC, TRGT, SRCID, TRGTID, C, ? extends AssociationRecord, ? extends AssociationTable> oneToManyWithAssociationTableEngine) {
			if (manyAssociationConfiguration.writeAuthorized) {
				oneToManyWithAssociationTableEngine.addInsertCascade(maintainAssociationOnly);
				oneToManyWithAssociationTableEngine.addUpdateCascade(manyAssociationConfiguration.orphanRemoval, maintainAssociationOnly);
				oneToManyWithAssociationTableEngine.addDeleteCascade(manyAssociationConfiguration.orphanRemoval, dialect.getColumnBinderRegistry());
			}
		}
		
		private void assignEngineForNonIndexedAssociation(Column rightPrimaryKey,
														  String associationTableName,
														  ManyRelationDescriptor<SRC, TRGT, C> manyRelationDescriptor,
														  IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			
			AssociationTable intermediaryTable = new AssociationTable<>(manyAssociationConfiguration.leftPrimaryKey.getTable().getSchema(),
					associationTableName,
					manyAssociationConfiguration.leftPrimaryKey,
					rightPrimaryKey,
					accessorDefinition,
					associationTableNamingStrategy,
					manyAssociationConfiguration.foreignKeyNamingStrategy
			);
			
			intermediaryTable.addForeignKey((BiFunction<Column, Column, String>) manyAssociationConfiguration.foreignKeyNamingStrategy::giveName,
					intermediaryTable.getOneSideKeyColumn(), manyAssociationConfiguration.leftPrimaryKey);
			if (!(manyAssociationConfiguration.cascadeMany.isTargetTablePerClassPolymorphic())) {
				intermediaryTable.addForeignKey(manyAssociationConfiguration.foreignKeyNamingStrategy.giveName(intermediaryTable.getManySideKeyColumn(), rightPrimaryKey),
						intermediaryTable.getManySideKeyColumn(), rightPrimaryKey);
			}
			
			AssociationRecordPersister<AssociationRecord, AssociationTable> associationPersister = new AssociationRecordPersister<>(
					new AssociationRecordMappingStrategy(intermediaryTable),
					dialect,
					connectionConfiguration);
			associationTableEngine = new OneToManyWithAssociationTableEngine<>(
					manyAssociationConfiguration.srcPersister,
					targetPersister,
					manyRelationDescriptor,
					associationPersister);
		}
		
		private void assignEngineForIndexedAssociation(Column rightPrimaryKey,
													   String associationTableName,
													   ManyRelationDescriptor manyRelationDescriptor,
													   IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			
			if (((CascadeManyList) manyAssociationConfiguration.cascadeMany).getIndexingColumn() != null) {
				throw new UnsupportedOperationException("Indexing column is defined without owner : relation is only declared by "
						+ AccessorDefinition.toString(manyAssociationConfiguration.collectionGetter));
			}
			
			// NB: index column is part of the primary key
			IndexedAssociationTable intermediaryTable = new IndexedAssociationTable(manyAssociationConfiguration.leftPrimaryKey.getTable().getSchema(),
					associationTableName,
					manyAssociationConfiguration.leftPrimaryKey,
					rightPrimaryKey,
					accessorDefinition,
					associationTableNamingStrategy,
					manyAssociationConfiguration.foreignKeyNamingStrategy);
			
			intermediaryTable.addForeignKey(manyAssociationConfiguration.foreignKeyNamingStrategy::giveName,
					intermediaryTable.getOneSideKeyColumn(), manyAssociationConfiguration.leftPrimaryKey);
			if (!(manyAssociationConfiguration.cascadeMany.isTargetTablePerClassPolymorphic())) {
				intermediaryTable.addForeignKey(manyAssociationConfiguration.foreignKeyNamingStrategy.giveName(intermediaryTable.getManySideKeyColumn(), rightPrimaryKey),
						intermediaryTable.getManySideKeyColumn(), rightPrimaryKey);
			}
			
			AssociationRecordPersister<IndexedAssociationRecord, IndexedAssociationTable> indexedAssociationPersister =
					new AssociationRecordPersister<>(
							new IndexedAssociationRecordMappingStrategy(intermediaryTable),
							dialect,
							connectionConfiguration);
			associationTableEngine = new OneToManyWithIndexedAssociationTableEngine<>(
					manyAssociationConfiguration.srcPersister,
					targetPersister,
					manyRelationDescriptor,
					indexedAssociationPersister,
					intermediaryTable.getIndexColumn());
		}
	}
	
	/**
	 * Configurer dedicated to association that are mapped on reverse side by a property and a column on table's target entities
	 */
	private static class CascadeManyWithMappedAssociationConfigurer<SRC, TRGT, SRCID, TRGTID, C extends Collection<TRGT>> extends ConfigurerTemplate<SRC, TRGT, SRCID, TRGTID, C> {
		
		private final ManyAssociationConfiguration<SRC, TRGT, SRCID, TRGTID, C> manyAssociationConfiguration;
		private final boolean allowOrphanRemoval;
		
		private OneToManyWithMappedAssociationEngine<SRC, TRGT, SRCID, TRGTID, C> mappedAssociationEngine;
		
		private CascadeManyWithMappedAssociationConfigurer(ManyAssociationConfiguration<SRC, TRGT, SRCID, TRGTID, C> manyAssociationConfiguration,
														   boolean allowOrphanRemoval) {
			super(manyAssociationConfiguration);
			this.manyAssociationConfiguration = manyAssociationConfiguration;
			this.allowOrphanRemoval = allowOrphanRemoval;
		}
		
		void prepare(IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			determineAccessorDefinition(targetPersister);
			// We're looking for the foreign key (for necessary join) and for getter/setter required to manage the relation 
			Column<Table, SRCID> reverseColumn = manyAssociationConfiguration.cascadeMany.getReverseColumn();
			Method reverseMethod = null;
			String getterSignature = null;
			// Setter for applying source entity to reverse side (target entities)
			SerializableBiConsumer<TRGT, SRC> reverseSetter;
			SerializableFunction<TRGT, SRC> reverseGetter = null;
			PropertyAccessor<TRGT, SRC> reversePropertyAccessor = null;
			if (reverseColumn == null) {
				// Reverse side is surely defined by reverse method (because CascadeManyWithMappedAssociationConfigurer is invoked only
				// when association is mapped by reverse side and reverse column is null),
				// we look for the matching column by looking for any reversed mapped property (bidirectional relation)
				MethodReferenceCapturer methodReferenceCapturer = new MethodReferenceCapturer();
				if (manyAssociationConfiguration.cascadeMany.getReverseSetter() != null) {
					reverseSetter = manyAssociationConfiguration.cascadeMany.getReverseSetter();
					reverseMethod = methodReferenceCapturer.findMethod(reverseSetter);
					AccessorByMember<TRGT, SRC, ? extends Member> accessor = new MutatorByMethod<TRGT, SRC>(reverseMethod).toAccessor();
					reverseGetter = accessor::get;
					getterSignature = accessor.toString();
				} else {
					reverseGetter = manyAssociationConfiguration.cascadeMany.getReverseGetter();
					reverseMethod = methodReferenceCapturer.findMethod(reverseGetter);
					getterSignature = Reflections.toString(reverseMethod);
				}
				reversePropertyAccessor = accessor(reverseMethod);
				// Since reverse property accessor may not be declared the same way that it is present in ClassMappingStrategy
				// we must use a ValueAccessPointMap which allows to compare different ValueAccessPoints
				IEntityMappingStrategy<TRGT, TRGTID, Table> targetMappingStrategy = targetPersister.getMappingStrategy();
				ValueAccessPointMap<? extends Column<?, Object>> accessPointMap = new ValueAccessPointMap<>(targetMappingStrategy.getPropertyToColumn());
				reverseColumn = (Column<Table, SRCID>) accessPointMap.get(reversePropertyAccessor);
				// we didn't find an existing matching column by its property (relation is not bidirectional), so we create it
				if (reverseColumn == null) {
					IEntityMappingStrategy<SRC, SRCID, ?> sourceMappingStrategy = manyAssociationConfiguration.srcPersister.getMappingStrategy();
					// no column found for reverse side owner, we create it
					PrimaryKey<?> primaryKey = sourceMappingStrategy.getTargetTable().getPrimaryKey();
					reverseColumn = targetMappingStrategy.getTargetTable().addColumn(
							manyAssociationConfiguration.joinColumnNamingStrategy.giveName(AccessorDefinition.giveDefinition(reversePropertyAccessor)),
							Iterables.first(primaryKey.getColumns()).getJavaType());
					// column can be null if we don't remove orphans
					reverseColumn.setNullable(!allowOrphanRemoval);
					
					SerializableFunction<TRGT, SRC> finalReverseGetter = reverseGetter;
					IdAccessor<SRC, SRCID> idAccessor = sourceMappingStrategy.getIdMappingStrategy().getIdAccessor();
					Function<TRGT, SRCID> targetIdSupplier = trgt -> nullable(finalReverseGetter.apply(trgt)).map(idAccessor::getId).getOr((SRCID) null);
					ShadowColumnValueProvider<TRGT, SRCID, Table> targetIdValueProvider = new ShadowColumnValueProvider<>(reverseColumn, targetIdSupplier);
					targetMappingStrategy.addShadowColumnInsert(targetIdValueProvider);
					targetMappingStrategy.addShadowColumnUpdate(targetIdValueProvider);
				} // else = bidirectional relation with matching column, property and column will be maintained through it so we have nothing to do here
				  // (user code is expected to maintain bidirectionality aka when adding an entity to its collection also set parent value)
			} else {
				// Since reverse property accessor may not be declared the same way that it is present in ClassMappingStrategy
				// we must use a ValueAccessPointMap which allows to compare different ValueAccessPoints
				IEntityMappingStrategy<TRGT, TRGTID, Table> targetMappingStrategy = targetPersister.getMappingStrategy();
				IEntityMappingStrategy<SRC, SRCID, ?> sourceMappingStrategy = manyAssociationConfiguration.srcPersister.getMappingStrategy();
				
				// Reverse side is surely defined by reverse method (because CascadeManyWithMappedAssociationConfigurer is invoked only
				// when association is mapped by reverse side and reverse column is null),
				// we look for the matching column
				SerializableFunction<TRGT, SRC> finalReverseGetter;
				if (manyAssociationConfiguration.cascadeMany.getReverseGetter() != null) {
					finalReverseGetter = manyAssociationConfiguration.cascadeMany.getReverseGetter();
				} else {
					AccessorByMethod<TRGT, SRC> accessor = Accessors.accessorByMethod(
							targetMappingStrategy.getClassToPersist(),
							sourceMappingStrategy.getClassToPersist().getSimpleName().toLowerCase()
					);
					reversePropertyAccessor = new PropertyAccessor<>(accessor);
					reverseGetter = accessor::get;
					getterSignature = accessor.toString();
					finalReverseGetter = reverseGetter;
				}
				
				IdAccessor<SRC, SRCID> idAccessor = sourceMappingStrategy.getIdMappingStrategy().getIdAccessor();
				Function<TRGT, SRCID> targetIdSupplier = trgt -> nullable(finalReverseGetter.apply(trgt)).map(idAccessor::getId).getOr((SRCID) null);
				ShadowColumnValueProvider<TRGT, SRCID, Table> targetIdValueProvider = new ShadowColumnValueProvider<>(reverseColumn, targetIdSupplier);
				targetMappingStrategy.addShadowColumnInsert(targetIdValueProvider);
				targetMappingStrategy.addShadowColumnUpdate(targetIdValueProvider);
			}
			
			if (manyAssociationConfiguration.cascadeMany instanceof CascadeManyList) {
				if (reverseGetter == null) {
					throw new UnsupportedOperationException("Indexed collection without getter is not supported : relation is mapped by "
							+ (reverseMethod != null ? Reflections.toString(reverseMethod) : manyAssociationConfiguration.cascadeMany.getReverseColumn())
							+ " but no indexing property is defined");
				}
			}
			
			// adding foreign key constraint
			// NB: we ask it to targetPersister because it may be polymorphic or complex (ie contains several tables) so it knows better how to do it
			if (!(manyAssociationConfiguration.cascadeMany.isTargetTablePerClassPolymorphic())) {
				reverseColumn.getTable().addForeignKey((BiFunction<Column, Column, String>) manyAssociationConfiguration.foreignKeyNamingStrategy::giveName,
						reverseColumn, manyAssociationConfiguration.leftPrimaryKey);
			} else {
				// table-per-class case : we add a foreign key between each table of subentity and source primary key
				Column finalReverseColumn = reverseColumn;
				targetPersister.giveImpliedTables().forEach(table -> {
					Column projectedColumn = table.addColumn(finalReverseColumn.getName(), finalReverseColumn.getJavaType(), finalReverseColumn.getSize());
					table.addForeignKey((BiFunction<Column, Column, String>) manyAssociationConfiguration.foreignKeyNamingStrategy::giveName,
							projectedColumn, manyAssociationConfiguration.leftPrimaryKey);
				});
			}
			
			// we have a direct relation : relation is owned by target table as a foreign key
			BiConsumer<TRGT, SRC> reverseSetterAsConsumer = reversePropertyAccessor == null ? null : reversePropertyAccessor::set;
			if (manyAssociationConfiguration.cascadeMany instanceof CascadeManyList) {
				assignEngineForIndexedAssociation(getterSignature, reverseSetterAsConsumer, reverseGetter, reverseColumn,
						((CascadeManyList) manyAssociationConfiguration.cascadeMany).getIndexingColumn(), targetPersister);
			} else {
				assignEngineForNonIndexedAssociation(reverseSetterAsConsumer, reverseColumn, targetPersister);
			}
		}
		
		@Override
		void configure(IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			prepare(targetPersister);
			mappedAssociationEngine.addSelectCascade(manyAssociationConfiguration.leftPrimaryKey, mappedAssociationEngine.getManyRelationDescriptor().getReverseColumn());
			addWriteCascades(mappedAssociationEngine);
		}
		
		@Override
		public ConfigurationResult<SRC, TRGT> appendCascadesWithSelectIn2Phases(String tableAlias, IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
																				FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener) {
			prepare(targetPersister);
			mappedAssociationEngine.addSelectCascadeIn2Phases(manyAssociationConfiguration.leftPrimaryKey,
					mappedAssociationEngine.getManyRelationDescriptor().getReverseColumn(),
					manyAssociationConfiguration.collectionGetter,
					firstPhaseCycleLoadListener);
			addWriteCascades(mappedAssociationEngine);
			return new ConfigurationResult<>(mappedAssociationEngine.getManyRelationDescriptor().getRelationFixer(), manyAssociationConfiguration.srcPersister);
		}
		
		private void addWriteCascades(OneToManyWithMappedAssociationEngine<SRC, TRGT, SRCID, TRGTID, C> mappedAssociationEngine) {
			if (manyAssociationConfiguration.writeAuthorized) {
				mappedAssociationEngine.addInsertCascade();
				mappedAssociationEngine.addUpdateCascade(manyAssociationConfiguration.orphanRemoval);
				mappedAssociationEngine.addDeleteCascade(manyAssociationConfiguration.orphanRemoval);
			}
		}
		
		private void assignEngineForNonIndexedAssociation(@Nullable BiConsumer<TRGT, SRC> reverseSetter,
														  Column reverseColumn,
														  IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			MappedManyRelationDescriptor<SRC, TRGT, C> manyRelationDefinition = new MappedManyRelationDescriptor<>(
					manyAssociationConfiguration.collectionGetter::get, manyAssociationConfiguration.setter::set,
					manyAssociationConfiguration.giveCollectionFactory(), reverseSetter, reverseColumn);
			mappedAssociationEngine = new OneToManyWithMappedAssociationEngine<>(
					targetPersister,
					manyRelationDefinition,
					manyAssociationConfiguration.srcPersister);
		}
		
		private void assignEngineForIndexedAssociation(String getterSignature,
													   @Nullable BiConsumer<TRGT, SRC> reverseSetter,
													   SerializableFunction<TRGT, SRC> reverseGetter,
													   Column reverseColumn,
													   @Nullable Column<? extends Table, Integer> indexingColumn,
													   IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			if (indexingColumn == null) {
				String indexingColumnName = manyAssociationConfiguration.indexColumnNamingStrategy.giveName(accessorDefinition);
				indexingColumn = targetPersister.getMappingStrategy().getTargetTable().addColumn(indexingColumnName, int.class);
			}
			IndexedMappedManyRelationDescriptor<SRC, TRGT, C> manyRelationDefinition = new IndexedMappedManyRelationDescriptor<>(
					manyAssociationConfiguration.collectionGetter::get, manyAssociationConfiguration.setter::set,
					manyAssociationConfiguration.giveCollectionFactory(), reverseSetter, reverseColumn, reverseGetter, getterSignature);
			mappedAssociationEngine = (OneToManyWithMappedAssociationEngine) new OneToManyWithIndexedMappedAssociationEngine<>(
					targetPersister,
					(IndexedMappedManyRelationDescriptor) manyRelationDefinition,
					manyAssociationConfiguration.srcPersister,
					indexingColumn
			);
		}
	}
	
	/**
	 * Object invoked on row read
	 * @param <SRC>
	 * @param <TRGTID>
	 */
	@FunctionalInterface
	public interface FirstPhaseCycleLoadListener<SRC, TRGTID> {
		
		void onFirstPhaseRowRead(SRC src, TRGTID targetId);
		
	}
	
	public static class ConfigurationResult<SRC, TRGT> {
		
		private BeanRelationFixer<SRC, TRGT> beanRelationFixer;
		private IConfiguredJoinedTablesPersister<SRC, ?> sourcePersister;
		
		public ConfigurationResult(BeanRelationFixer<SRC, TRGT> beanRelationFixer,
								   IConfiguredJoinedTablesPersister<SRC, ?> sourcePersister) {
			this.beanRelationFixer = beanRelationFixer;
			this.sourcePersister = sourcePersister;
		}
		
		public <SRCID> IConfiguredJoinedTablesPersister<SRC, SRCID> getSourcePersister() {
			return (IConfiguredJoinedTablesPersister<SRC, SRCID>) sourcePersister;
		}
		
		public BeanRelationFixer<SRC, TRGT> getBeanRelationFixer() {
			return beanRelationFixer;
		}
	}
}
