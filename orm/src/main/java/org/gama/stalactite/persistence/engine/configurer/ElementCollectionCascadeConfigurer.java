package org.gama.stalactite.persistence.engine.configurer;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.gama.lang.Duo;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.Maps;
import org.gama.reflection.AccessorChain;
import org.gama.reflection.AccessorChain.ValueInitializerOnNullValue;
import org.gama.reflection.AccessorDefinition;
import org.gama.reflection.IAccessor;
import org.gama.reflection.IReversibleAccessor;
import org.gama.reflection.PropertyAccessor;
import org.gama.stalactite.persistence.engine.runtime.BeanRelationFixer;
import org.gama.stalactite.persistence.engine.ColumnNamingStrategy;
import org.gama.stalactite.persistence.engine.ElementCollectionTableNamingStrategy;
import org.gama.stalactite.persistence.engine.EmbeddableMappingConfiguration;
import org.gama.stalactite.persistence.engine.EmbeddableMappingConfiguration.Linkage;
import org.gama.stalactite.persistence.engine.EmbeddableMappingConfigurationProvider;
import org.gama.stalactite.persistence.engine.ForeignKeyNamingStrategy;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredJoinedTablesPersister;
import org.gama.stalactite.persistence.engine.IEntityPersister;
import org.gama.stalactite.persistence.engine.runtime.IJoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.JoinedTablesPersister;
import org.gama.stalactite.persistence.engine.configurer.BeanMappingBuilder.ColumnNameProvider;
import org.gama.stalactite.persistence.engine.runtime.CollectionUpdater;
import org.gama.stalactite.persistence.engine.runtime.OneToManyWithMappedAssociationEngine.DeleteTargetEntitiesBeforeDeleteCascader;
import org.gama.stalactite.persistence.engine.runtime.OneToManyWithMappedAssociationEngine.TargetInstancesInsertCascader;
import org.gama.stalactite.persistence.engine.runtime.OneToManyWithMappedAssociationEngine.TargetInstancesUpdateCascader;
import org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree;
import org.gama.stalactite.persistence.id.PersistableIdentifier;
import org.gama.stalactite.persistence.id.PersistedIdentifier;
import org.gama.stalactite.persistence.id.assembly.ComposedIdentifierAssembler;
import org.gama.stalactite.persistence.id.diff.AbstractDiff;
import org.gama.stalactite.persistence.id.manager.AlreadyAssignedIdentifierManager;
import org.gama.stalactite.persistence.id.manager.StatefullIdentifier;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.mapping.ColumnedRow;
import org.gama.stalactite.persistence.mapping.ComposedIdMappingStrategy;
import org.gama.stalactite.persistence.mapping.EmbeddedBeanMappingStrategy;
import org.gama.stalactite.persistence.mapping.IdAccessor;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.sql.IConnectionConfiguration;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.sql.result.Row;

import static org.gama.lang.Nullable.nullable;
import static org.gama.lang.bean.Objects.preventNull;

/**
 * Class that configures element-collection mapping
 * 
 * @author Guillaume Mary
 */
public class ElementCollectionCascadeConfigurer<SRC, TRGT, ID, C extends Collection<TRGT>> {
	
	private final Dialect dialect;
	private final IConnectionConfiguration connectionConfiguration;
	
	public ElementCollectionCascadeConfigurer(Dialect dialect, IConnectionConfiguration connectionConfiguration) {
		this.dialect = dialect;
		this.connectionConfiguration = connectionConfiguration;
	}
	
	public <T extends Table, TARGET_TABLE extends Table<?>> void appendCascade(ElementCollectionLinkage<SRC, TRGT, C> linkage,
												   IEntityConfiguredJoinedTablesPersister<SRC, ID> sourcePersister,
												   ForeignKeyNamingStrategy foreignKeyNamingStrategy,
												   ColumnNamingStrategy columnNamingStrategy,
												   ElementCollectionTableNamingStrategy tableNamingStrategy) {
		
		AccessorDefinition collectionProviderDefinition = AccessorDefinition.giveDefinition(linkage.getCollectionProvider());
		// schema configuration
		Column<T, ID> sourcePK = Iterables.first((Set<Column>) sourcePersister.getMappingStrategy().getTargetTable().getPrimaryKey().getColumns());
		
		String tableName = nullable(linkage.getTargetTableName()).getOr(() -> tableNamingStrategy.giveName(collectionProviderDefinition));
		TARGET_TABLE targetTable = (TARGET_TABLE) nullable(linkage.getTargetTable()).getOr(() -> new Table(tableName));
		
		String reverseColumnName = nullable(linkage.getReverseColumnName()).getOr(() ->
				columnNamingStrategy.giveName(new AccessorDefinition(ElementRecord.class, "getId", StatefullIdentifier.class)));
		Column<TARGET_TABLE, ID> reverseColumn = (Column<TARGET_TABLE, ID>) nullable(linkage.getReverseColumn())
				.getOr(() -> (Column) targetTable.addColumn(reverseColumnName, sourcePK.getJavaType()));
		registerColumnBinder(reverseColumn, sourcePK);	// because sourcePk binder might have been overloaded by column so we need to adjust to it
		
		reverseColumn.primaryKey();
		
		EmbeddableMappingConfiguration<TRGT> embeddableConfiguration =
				nullable(linkage.getEmbeddableConfigurationProvider()).map(EmbeddableMappingConfigurationProvider::getConfiguration).get();
		ClassMappingStrategy<ElementRecord, ElementRecord, Table> elementRecordStrategy;
		if (embeddableConfiguration == null) {
			String columnName = nullable(linkage.getElementColumnName())
					.getOr(() -> columnNamingStrategy.giveName(collectionProviderDefinition));
			Column<Table, TRGT> elementColumn = (Column<Table, TRGT>) targetTable.addColumn(columnName, linkage.getComponentType());
			elementColumn.primaryKey();
			targetTable.addForeignKey(foreignKeyNamingStrategy::giveName, (Column) reverseColumn, (Column) sourcePK);
			
			elementRecordStrategy = new ElementRecordMappingStrategy(targetTable, reverseColumn, elementColumn);
		} else {
			BeanMappingBuilder elementCollectionMappingBuilder = new BeanMappingBuilder();
			Map<IReversibleAccessor, Column> columnMap = elementCollectionMappingBuilder.build(embeddableConfiguration, targetTable,
					dialect.getColumnBinderRegistry(), new ColumnNameProvider(columnNamingStrategy) {
						@Override
						protected String giveColumnName(Linkage pawn) {
							return nullable(linkage.getOverridenColumnNames().get(pawn.getAccessor()))
									.getOr(() -> super.giveColumnName(pawn));
						}
					});
			
			Map<IReversibleAccessor, Column> projectedColumnMap = new HashMap<>();
			columnMap.forEach((k, v) -> {
				
				AccessorChain accessorChain = AccessorChain.forModel(Arrays.asList(ElementRecord.ELEMENT_ACCESSOR, k), (accessor, valueType) -> {
					if (accessor == ElementRecord.ELEMENT_ACCESSOR) {
						// on getElement(), bean type can't be dedueced by reflection due to generic type erasure : default mecanism returns Object
						// so we have to specify our bean type, else a simple Object is instanciated which throws a ClassCastException further
						return embeddableConfiguration.getBeanType();
					} else {
						// default mecanism
						return ValueInitializerOnNullValue.giveValueType(accessor, valueType);
					}
				});
				
				projectedColumnMap.put(accessorChain, v);
				v.primaryKey();
			});
			
			EmbeddedBeanMappingStrategy<ElementRecord, Table> dd = new EmbeddedBeanMappingStrategy<>(ElementRecord.class,
					targetTable, (Map) projectedColumnMap);
			elementRecordStrategy = new ElementRecordMappingStrategy(targetTable, reverseColumn, dd);
		}
			
		// Note that table will be added to schema thanks to select cascade because join is added to source persister
		JoinedTablesPersister<ElementRecord, ElementRecord, Table> elementRecordPersister = new JoinedTablesPersister<>(elementRecordStrategy, dialect, connectionConfiguration);
		
		// insert management
		IAccessor<SRC, C> collectionAccessor = linkage.getCollectionProvider();
		addInsertCascade(sourcePersister, elementRecordPersister, collectionAccessor);
		
		// update management
		addUpdateCascade(sourcePersister, elementRecordPersister, collectionAccessor);
		
		// delete management (we provided persisted instances so they are perceived as deletable)
		addDeleteCascade(sourcePersister, elementRecordPersister, collectionAccessor);
		
		// select management
		Supplier<C> collectionFactory = preventNull(
				linkage.getCollectionFactory(),
				BeanRelationFixer.giveCollectionFactory((Class<C>) collectionProviderDefinition.getMemberType()));
		addSelectCascade(sourcePersister, elementRecordPersister, sourcePK, reverseColumn,
				linkage.getCollectionProvider().toMutator()::set, collectionAccessor::get,
				collectionFactory);
	}
	
	private void registerColumnBinder(Column reverseColumn, Column sourcePK) {
		dialect.getColumnBinderRegistry().register(reverseColumn, dialect.getColumnBinderRegistry().getBinder(sourcePK));
		dialect.getJavaTypeToSqlTypeMapping().put(reverseColumn, dialect.getJavaTypeToSqlTypeMapping().getTypeName(sourcePK));
	}
	
	private void addInsertCascade(IEntityConfiguredJoinedTablesPersister<SRC, ID> sourcePersister,
								  IEntityPersister<ElementRecord, ElementRecord> wrapperPersister,
								  IAccessor<SRC, C> collectionAccessor) {
		Function<SRC, Collection<ElementRecord>> collectionProviderForInsert = collectionProvider(
				collectionAccessor,
				sourcePersister.getMappingStrategy(),
				PersistableIdentifier::new);
		
		sourcePersister.addInsertListener(new TargetInstancesInsertCascader<>(wrapperPersister, collectionProviderForInsert));
	}
	
	private void addUpdateCascade(IEntityConfiguredJoinedTablesPersister<SRC, ID> sourcePersister,
								  IEntityPersister<ElementRecord, ElementRecord> wrapperPersister,
								  IAccessor<SRC, C> collectionAccessor) {
		Function<SRC, Collection<ElementRecord>> collectionProviderAsPersistedInstances = collectionProvider(
				collectionAccessor,
				sourcePersister.getMappingStrategy(),
				PersistedIdentifier::new);
		
		BiConsumer<Duo<SRC, SRC>, Boolean> updateListener = new CollectionUpdater<SRC, ElementRecord, Collection<ElementRecord>>(
				collectionProviderAsPersistedInstances,
				wrapperPersister,
				(o, i) -> { /* no reverse setter because we store only raw values */ },
				true,
				// we base our id policy on a particular identifier because Id is all the same for ElementCollection (it is source bean id)
				ElementRecord::footprint) {
			
			/**
			 * Overriden to avoid no insertion of persisted instances (isNew() = true) : we want insert of not-new instance because we declared
			 * collection provider as a {@link PersistedIdentifier} hence added instance are not considered new, which is wanted as such for
			 * collection removal (because they are persisted they can be removed from database)
			 */
			@Override
			protected void onAddedTarget(UpdateContext updateContext, AbstractDiff<ElementRecord> diff) {
				updateContext.getEntitiesToBeInserted().add(diff.getReplacingInstance());
			}
		};
		
		sourcePersister.addUpdateListener(new TargetInstancesUpdateCascader<>(wrapperPersister, updateListener));
	}
	
	private void addDeleteCascade(IEntityConfiguredJoinedTablesPersister<SRC, ID> sourcePersister,
								  IEntityPersister<ElementRecord, ElementRecord> wrapperPersister,
								  IAccessor<SRC, C> collectionAccessor) {
		Function<SRC, Collection<ElementRecord>> collectionProviderAsPersistedInstances = collectionProvider(
				collectionAccessor,
				sourcePersister.getMappingStrategy(),
				PersistedIdentifier::new);
		sourcePersister.addDeleteListener(new DeleteTargetEntitiesBeforeDeleteCascader<>(wrapperPersister, collectionProviderAsPersistedInstances));
	}
	
	private <T extends Table> void addSelectCascade(IEntityConfiguredJoinedTablesPersister<SRC, ID> sourcePersister,
								  IJoinedTablesPersister<ElementRecord, ElementRecord> elementRecordPersister,
								  Column sourcePK,
								  Column elementRecordToSourceForeignKey,
								  BiConsumer<SRC, C> collectionSetter,
								  Function<SRC, C> collectionGetter,
								  Supplier<C> collectionFactory) {
		// a particular collection fixer that gets raw values (elements) from ElementRecord
		// because elementRecordPersister manages ElementRecord, so it gives them as input of the relation,
		// hence an adaption is needed to "convert" it
		BeanRelationFixer<SRC, ElementRecord> relationFixer = BeanRelationFixer.ofAdapter(
				collectionSetter,
				collectionGetter,
				collectionFactory,
				(bean, input, collection) -> collection.add((TRGT) input.getElement()));	// element value is taken from ElementRecord
		
		elementRecordPersister.joinAsMany(sourcePersister, sourcePK, elementRecordToSourceForeignKey, relationFixer, null, EntityJoinTree.ROOT_STRATEGY_NAME, true);
	}
	
	private Function<SRC, Collection<ElementRecord>> collectionProvider(IAccessor<SRC, C> collectionAccessor,
																		IdAccessor<SRC, ID> idAccessor,
																		Function<ID, StatefullIdentifier> idWrapper) {
		return src -> Iterables.collect(collectionAccessor.get(src),
						trgt -> new ElementRecord<>(idWrapper.apply(idAccessor.getId(src)), trgt), HashSet::new);
	}
	
	/**
	 * Mapping strategy dedicated to {@link ElementRecord}. Very close to {@link org.gama.stalactite.persistence.engine.AssociationRecordMappingStrategy}
	 * in its principle.
	 * 
	 */
	private static class ElementRecordMappingStrategy extends ClassMappingStrategy<ElementRecord, ElementRecord, Table> {
		private ElementRecordMappingStrategy(Table targetTable, Column idColumn, Column elementColumn) {
			super(ElementRecord.class, targetTable, (Map) Maps
							.forHashMap(IReversibleAccessor.class, Column.class)
							.add(ElementRecord.IDENTIFIER_ACCESSOR, idColumn)
							.add(ElementRecord.ELEMENT_ACCESSOR, elementColumn),
					new ElementRecordIdMappingStrategy(targetTable, idColumn, elementColumn));
		}
		
		private ElementRecordMappingStrategy(Table targetTable, Column idColumn, EmbeddedBeanMappingStrategy<ElementRecord, Table> embeddableMapping) {
			super(ElementRecord.class, targetTable, (Map) Maps.putAll(Maps
							.forHashMap(IReversibleAccessor.class, Column.class)
							.add(ElementRecord.IDENTIFIER_ACCESSOR, idColumn),
							embeddableMapping.getPropertyToColumn()),
					new ElementRecordIdMappingStrategy(targetTable, idColumn, embeddableMapping));
		}
		
		/**
		 * {@link org.gama.stalactite.persistence.mapping.IdMappingStrategy} for {@link ElementRecord} : a composed id made of
		 * {@link ElementRecord#getIdentifier()} and {@link ElementRecord#getElement()}
		 */
		private static class ElementRecordIdMappingStrategy extends ComposedIdMappingStrategy<ElementRecord, ElementRecord> {
			public ElementRecordIdMappingStrategy(Table targetTable, Column idColumn, Column elementColumn) {
				super(new ElementRecordIdAccessor(),
						new AlreadyAssignedIdentifierManager<>(ElementRecord.class, c -> {}, c -> false),
						new ElementRecordIdentifierAssembler(targetTable, idColumn, elementColumn));
			}
			
			public ElementRecordIdMappingStrategy(Table targetTable, Column idColumn, EmbeddedBeanMappingStrategy<ElementRecord, Table> elementColumn) {
				super(new ElementRecordIdAccessor(),
						new AlreadyAssignedIdentifierManager<>(ElementRecord.class, c -> {}, c -> false),
						new ElementRecordIdentifierAssembler2(targetTable, idColumn, elementColumn));
			}
			
			/**
			 * Overriden because {@link ComposedIdMappingStrategy} doest not support {@link StatefullIdentifier} : super implementation is based
			 * on {@link ElementRecord#getIdentifier()} == null which is always false on {@link ElementRecord}  
			 * 
			 * @param entity any non null entity
			 * @return true or false based on {@link ElementRecord#isNew()}
			 */
			@Override
			public boolean isNew(@Nonnull ElementRecord entity) {
				return entity.isNew();
			}
			
			private static class ElementRecordIdAccessor implements IdAccessor<ElementRecord, ElementRecord> {
					@Override
					public ElementRecord getId(ElementRecord associationRecord) {
						return associationRecord;
					}
					
					@Override
					public void setId(ElementRecord associationRecord, ElementRecord identifier) {
						associationRecord.setIdentifier(identifier.getIdentifier());
						associationRecord.setElement(identifier.getElement());
					}
			}
			
			private static class ElementRecordIdentifierAssembler extends ComposedIdentifierAssembler<ElementRecord> {
				
				private final Column idColumn;
				private final Column elementColumn;
				
				private ElementRecordIdentifierAssembler(Table targetTable, Column idColumn, Column elementColumn) {
					super(targetTable);
					this.idColumn = idColumn;
					this.elementColumn = elementColumn;
				}
				
				@Override
				protected ElementRecord assemble(Map<Column, Object> primaryKeyElements) {
					Object leftValue = primaryKeyElements.get(idColumn);
					Object rightValue = primaryKeyElements.get(elementColumn);
					// we should not return an id if any (both expected in fact) value is null
					if (leftValue == null || rightValue == null) {
						return null;
					} else {
						return new ElementRecord(new PersistedIdentifier(leftValue), rightValue);
					}
				}
				
				@Override
				public Map<Column, Object> getColumnValues(@Nonnull ElementRecord id) {
					return Maps.asMap(idColumn, id.getIdentifier())
							.add(elementColumn, id.getElement());
				}
			}
			
			private static class ElementRecordIdentifierAssembler2 extends ComposedIdentifierAssembler<ElementRecord> {
				
				private final Column idColumn;
				private final EmbeddedBeanMappingStrategy<ElementRecord, Table> elementColumn;
				
				private ElementRecordIdentifierAssembler2(Table targetTable, Column idColumn, EmbeddedBeanMappingStrategy<ElementRecord, Table> elementColumn) {
					super(targetTable);
					this.idColumn = idColumn;
					this.elementColumn = elementColumn;
				}
				
				@Override
				public ElementRecord assemble(@Nonnull Row row, @Nonnull ColumnedRow rowAliaser) {
					Object leftValue = rowAliaser.getValue(idColumn, row);
					Object rightValue = elementColumn.getRowTransformer().copyWithAliases(rowAliaser).transform(row);
					// we should not return an id if any (both expected in fact) value is null
					if (leftValue == null || rightValue == null) {
						return null;
					} else {
						return new ElementRecord(new PersistedIdentifier(leftValue), rightValue);
					}
				}
				
				@Override
				protected ElementRecord assemble(Map<Column, Object> primaryKeyElements) {
					// never called
					return null;
				}
				
				@Override
				public Map<Column, Object> getColumnValues(@Nonnull ElementRecord id) {
					return Maps.putAll(Maps.asMap(idColumn, id.getIdentifier()), elementColumn.getInsertValues(id));
				}
			}
		}
	}
	
	/**
	 * Represents a line in table storage, acts as a wrapper of element collection with source bean identifier addition.
	 * 
	 * @param <TRGT> raw value type (element collection type)
	 * @param <ID> source bean identifier type
	 */
	private static class ElementRecord<TRGT, ID> {
		
		private static final PropertyAccessor<ElementRecord<Object, Object>, Object> IDENTIFIER_ACCESSOR = PropertyAccessor.fromMethodReference(
				ElementRecord::getIdentifier,
				ElementRecord::setIdentifier);
		
		private static final PropertyAccessor<ElementRecord<Object, Object>, Object> ELEMENT_ACCESSOR = PropertyAccessor.fromMethodReference(
				ElementRecord::getElement,
				ElementRecord::setElement);
		
		
		private StatefullIdentifier<ID> identifier;
		private TRGT element;
		
		/**
		 * Default constructor for select instanciation
		 */
		public ElementRecord() {
		}
		
		public ElementRecord(StatefullIdentifier<ID> identifier, TRGT element) {
			this.identifier = identifier;
			this.element = element;
		}
		
		public boolean isNew() {
			return !this.identifier.isPersisted();
		}
		
		public ID getIdentifier() {
			return identifier.getSurrogate();
		}
		
		public void setIdentifier(ID identifier) {
			this.identifier = new PersistedIdentifier<ID>(identifier);
		}
		
		public TRGT getElement() {
			return element;
		}
		
		public void setElement(TRGT element) {
			this.element = element;
		}
		
		/**
		 * Identifier for {@link org.gama.stalactite.persistence.id.diff.CollectionDiffer} support (update use case), because it compares beans
		 * through their "foot print" which is their id in default/entity case, but since we are value type, we must provide a dedicated foot print.
		 * Could be hashCode() if it was implemented on identifier + element, but implementing it would require to implement equals() (to comply
		 * with best pratices) which is not our case nor required by {@link org.gama.stalactite.persistence.id.diff.CollectionDiffer}.
		 * Note : name of this method is not important
		 */
		public int footprint() {
			int result = identifier.getSurrogate().hashCode();
			result = 31 * result + element.hashCode();
			return result;
		}
	}
}
