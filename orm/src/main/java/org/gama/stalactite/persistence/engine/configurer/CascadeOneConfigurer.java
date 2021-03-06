package org.gama.stalactite.persistence.engine.configurer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.gama.lang.Duo;
import org.gama.lang.ThreadLocals;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.lang.function.Functions.NullProofFunction;
import org.gama.lang.function.Predicates;
import org.gama.reflection.AccessorByMethodReference;
import org.gama.reflection.AccessorDefinition;
import org.gama.reflection.Accessors;
import org.gama.reflection.IAccessor;
import org.gama.reflection.IMutator;
import org.gama.reflection.ValueAccessPoint;
import org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode;
import org.gama.stalactite.persistence.engine.ColumnNamingStrategy;
import org.gama.stalactite.persistence.engine.ForeignKeyNamingStrategy;
import org.gama.stalactite.persistence.engine.MappingConfigurationException;
import org.gama.stalactite.persistence.engine.NotYetSupportedOperationException;
import org.gama.stalactite.persistence.engine.PersisterRegistry;
import org.gama.stalactite.persistence.engine.RuntimeMappingException;
import org.gama.stalactite.persistence.engine.cascade.AfterDeleteByIdSupport;
import org.gama.stalactite.persistence.engine.cascade.AfterDeleteSupport;
import org.gama.stalactite.persistence.engine.cascade.BeforeDeleteByIdSupport;
import org.gama.stalactite.persistence.engine.cascade.BeforeDeleteSupport;
import org.gama.stalactite.persistence.engine.cascade.BeforeInsertSupport;
import org.gama.stalactite.persistence.engine.cascade.BeforeUpdateSupport;
import org.gama.stalactite.persistence.engine.listening.DeleteListener;
import org.gama.stalactite.persistence.engine.listening.InsertListener;
import org.gama.stalactite.persistence.engine.listening.SelectListener;
import org.gama.stalactite.persistence.engine.listening.UpdateListener;
import org.gama.stalactite.persistence.engine.runtime.BeanRelationFixer;
import org.gama.stalactite.persistence.engine.runtime.IConfiguredJoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredJoinedTablesPersister;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredPersister;
import org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree;
import org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree.JoinType;
import org.gama.stalactite.persistence.engine.runtime.load.PassiveJoinNode;
import org.gama.stalactite.persistence.engine.runtime.load.RelationJoinNode;
import org.gama.stalactite.persistence.mapping.IEntityMappingStrategy;
import org.gama.stalactite.persistence.mapping.IMappingStrategy.ShadowColumnValueProvider;
import org.gama.stalactite.persistence.mapping.IMappingStrategy.UpwhereColumn;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.sql.IConnectionConfiguration;
import org.gama.stalactite.persistence.sql.dml.PreparedUpdate;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.sql.dml.WriteOperation;

import static org.gama.lang.Nullable.nullable;
import static org.gama.lang.function.Predicates.not;
import static org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode.ALL_ORPHAN_REMOVAL;
import static org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode.ASSOCIATION_ONLY;
import static org.gama.stalactite.persistence.engine.CascadeOptions.RelationMode.READ_ONLY;
import static org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree.ROOT_STRATEGY_NAME;

/**
 * @param <SRC> type of input (left/source entities)
 * @param <TRGT> type of output (right/target entities)
 * @param <SRCID> identifier type of target entities
 * @author Guillaume Mary
 */
public class CascadeOneConfigurer<SRC, TRGT, SRCID, TRGTID> {
	
	private final Dialect dialect;
	private final IConnectionConfiguration connectionConfiguration;
	private final PersisterRegistry persisterRegistry;
	private final CascadeOne<SRC, TRGT, TRGTID> cascadeOne;
	private final ForeignKeyNamingStrategy foreignKeyNamingStrategy;
	private final ColumnNamingStrategy joinColumnNamingStrategy;
	private final ConfigurerTemplate configurer;
	
	public CascadeOneConfigurer(CascadeOne<SRC, TRGT, TRGTID> cascadeOne,
								IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister,
								Dialect dialect,
								IConnectionConfiguration connectionConfiguration,
								PersisterRegistry persisterRegistry,
								ForeignKeyNamingStrategy foreignKeyNamingStrategy,
								ColumnNamingStrategy joinColumnNamingStrategy) {
		this.cascadeOne = cascadeOne;
		this.dialect = dialect;
		this.connectionConfiguration = connectionConfiguration;
		this.persisterRegistry = persisterRegistry;
		this.foreignKeyNamingStrategy = foreignKeyNamingStrategy;
		this.joinColumnNamingStrategy = joinColumnNamingStrategy;
		if (cascadeOne.isRelationOwnedByTarget()) {
			this.configurer = new RelationOwnedByTargetConfigurer(sourcePersister);
		} else {
			this.configurer = new RelationOwnedBySourceConfigurer(sourcePersister);
		}
	}
	
	public void appendCascades(String tableAlias,
							   PersisterBuilderImpl<TRGT, TRGTID> targetPersisterBuilder) {
		IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister = targetPersisterBuilder
				// please note that even if no table is found in configuration, build(..) will create one
				.build(dialect, connectionConfiguration, persisterRegistry,
						nullable(cascadeOne.getTargetTable()).getOr(nullable(cascadeOne.getReverseColumn()).map(Column::getTable).get()));
		this.configurer.appendCascades(tableAlias, targetPersister);
	}
	
	public ConfigurationResult<SRC, TRGT> appendCascadesWith2PhasesSelect(
			String tableAlias,
			IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
			FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener) {
		return this.configurer.appendCascadesWithSelectIn2Phases(tableAlias, targetPersister, firstPhaseCycleLoadListener);
	}
	
	private abstract class ConfigurerTemplate {
		protected final IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister;
		/** Left table column for join, may be left table primary key or column pointing to right table primary key */
		protected Column leftColumn;
		/** Right table column for join, may be right table primary key or column pointing to left table primary key  */
		protected Column rightColumn;
		protected BeanRelationFixer<SRC, TRGT> beanRelationFixer;
		
		protected ConfigurerTemplate(IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister) {
			this.sourcePersister = sourcePersister;
		}
		
		private void prepare(IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			
			RelationMode maintenanceMode = cascadeOne.getRelationMode();
			if (maintenanceMode == ASSOCIATION_ONLY) {
				throw new MappingConfigurationException(ASSOCIATION_ONLY + " is only relevent for one-to-many association");
			}
			IEntityMappingStrategy<SRC, SRCID, ?> mappingStrategy = sourcePersister.getMappingStrategy();
			if (mappingStrategy.getTargetTable().getPrimaryKey().getColumns().size() > 1) {
				throw new NotYetSupportedOperationException("Joining tables on a composed primary key is not (yet) supported");
			}
			
			// Finding joined columns
			IEntityMappingStrategy<TRGT, TRGTID, ?> targetMappingStrategy = targetPersister.getMappingStrategy();
			determineForeignKeyColumns(mappingStrategy, targetMappingStrategy);
			
			determineRelationFixer();
		}
		
		public void appendCascades(String tableAlias,
								   IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister) {
			prepare(targetPersister);
			addSelectCascade(tableAlias, targetPersister, beanRelationFixer);
			addWriteCascades(targetPersister);
		}
		
		public ConfigurationResult<SRC, TRGT> appendCascadesWithSelectIn2Phases(String tableAlias,
																				IEntityConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
																				FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener) {
			prepare(targetPersister);
			addSelectCascadeIn2Phases(tableAlias, targetPersister, firstPhaseCycleLoadListener);
			addWriteCascades(targetPersister);
			return new ConfigurationResult<>(beanRelationFixer, sourcePersister);
		}
		
		protected void addWriteCascades(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister) {
			boolean orphanRemoval = cascadeOne.getRelationMode() == ALL_ORPHAN_REMOVAL;
			boolean writeAuthorized = cascadeOne.getRelationMode() != READ_ONLY;
			if (writeAuthorized) {
				// NB: "delete removed" will be treated internally by updateCascade() and deleteCascade()
				addInsertCascade(targetPersister);
				addUpdateCascade(targetPersister, orphanRemoval);
				addDeleteCascade(targetPersister, orphanRemoval);
			}
		}
		
		protected abstract void determineRelationFixer();
		
		protected abstract void determineForeignKeyColumns(IEntityMappingStrategy<SRC, SRCID, ?> mappingStrategy,
																		  IEntityMappingStrategy<TRGT, TRGTID, ?> targetMappingStrategy);
		
		@SuppressWarnings("squid:S1172")	// argument targetPersister is used by subclasses
		protected void addInsertCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister) {
			// if cascade is mandatory, then adding nullability checking before insert
			if (!cascadeOne.isNullable()) {
				sourcePersister.addInsertListener(new MandatoryRelationCheckingBeforeInsertListener<>(cascadeOne.getTargetProvider()));
			}
		}
		
		protected abstract void addUpdateCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister, boolean orphanRemoval);
		
		protected abstract void addDeleteCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister, boolean orphanRemoval);
		
		protected void addSelectCascade(
				String tableAlias,
				IConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
				BeanRelationFixer<SRC, TRGT> beanRelationFixer) {
			// we add target subgraph joins to the one that was created
			targetPersister.joinAsOne(sourcePersister, leftColumn, rightColumn, tableAlias, beanRelationFixer, cascadeOne.isNullable());
			
			// We trigger subgraph load event (via targetSelectListener) on loading of our graph.
			// Done for instance for event consumers that initialize some things, because given ids of methods are those of source entity
			SelectListener targetSelectListener = targetPersister.getPersisterListener().getSelectListener();
			sourcePersister.addSelectListener(new SelectListener<SRC, SRCID>() {
				@Override
				public void beforeSelect(Iterable<SRCID> ids) {
					// since ids are not those of its entities, we should not pass them as argument, this will only initialize things if needed
					targetSelectListener.beforeSelect(Collections.emptyList());
				}

				@Override
				public void afterSelect(Iterable<? extends SRC> result) {
					List collect = Iterables.collectToList(result, cascadeOne.getTargetProvider()::get);
					// NB: entity can be null when loading relation, we skip nulls to prevent a NPE
					collect.removeIf(Objects::isNull);
					targetSelectListener.afterSelect(collect);
				}

				@Override
				public void onError(Iterable<SRCID> ids, RuntimeException exception) {
					// since ids are not those of its entities, we should not pass them as argument
					targetSelectListener.onError(Collections.emptyList(), exception);
				}
			});
		}
		
		abstract protected void addSelectCascadeIn2Phases(
				String tableAlias,
				IConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
				FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener);
		
	}
	
	private class RelationOwnedBySourceConfigurer extends ConfigurerTemplate {
		
		private RelationOwnedBySourceConfigurer(IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister) {
			super(sourcePersister);
		}
		
		@Override
		protected void determineRelationFixer() {
			IMutator<SRC, TRGT> targetSetter = cascadeOne.getTargetProvider().toMutator();
			this.beanRelationFixer = BeanRelationFixer.of(targetSetter::set);
		}
		
		@Override
		protected void determineForeignKeyColumns(IEntityMappingStrategy<SRC, SRCID, ?> srcMappingStrategy,
												  IEntityMappingStrategy<TRGT, TRGTID, ?> targetMappingStrategy) {
			rightColumn = Iterables.first((Set<Column<?, Object>>) targetMappingStrategy.getTargetTable().getPrimaryKey().getColumns());
			leftColumn = sourcePersister.getMappingStrategy().getTargetTable().addColumn(
					joinColumnNamingStrategy.giveName(AccessorDefinition.giveDefinition(cascadeOne.getTargetProvider())),
					rightColumn.getJavaType()
			);
			
			// According to the nullable option, we specify the ddl schema option
			leftColumn.nullable(cascadeOne.isNullable());
			
			// adding foreign key constraint
			String foreignKeyName = foreignKeyNamingStrategy.giveName(leftColumn, rightColumn);
			leftColumn.getTable().addForeignKey(foreignKeyName, leftColumn, rightColumn);
		}
		
		@Override
		protected void addSelectCascadeIn2Phases(
				String tableAlias,
				IConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
				FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener) {
			
			Table targetTableClone = new Table(targetPersister.getMappingStrategy().getTargetTable().getName());
			Column targetTableClonePK = targetTableClone.addColumn(rightColumn.getName(), rightColumn.getJavaType());
			String tableCloneAlias = AccessorDefinition.giveDefinition(cascadeOne.getTargetProvider()).getName();
			// This can't be done directly on root persister (took via persisterRegistry and targetPersister.getClassToPersist()) because
			// TransfomerListener would get root instance as source (aggregate root), not current source
			String joinName = sourcePersister.getEntityJoinTree().addPassiveJoin(ROOT_STRATEGY_NAME,
					leftColumn,
					targetTableClonePK,
					tableAlias,
					cascadeOne.isNullable() ? JoinType.OUTER : JoinType.INNER, (Set) Arrays.asSet(targetTableClonePK),
					(src, rowValueProvider) -> firstPhaseCycleLoadListener.onFirstPhaseRowRead(src, (TRGTID) rowValueProvider.apply(targetTableClonePK)),
					false);
			
			// Propagating 2-phases load to all nodes that use cycling type
			PassiveJoinNode passiveJoin = (PassiveJoinNode) sourcePersister.getEntityJoinTree().getJoin(joinName);
			targetPersister.getEntityJoinTree().foreachJoin(joinNode -> {
				if (joinNode instanceof RelationJoinNode
						&& ((RelationJoinNode<?, ?, ?, ?>) joinNode).getEntityInflater().getEntityType() == sourcePersister.getClassToPersist()) {
					Column localLeftColumn = joinNode.getTable().getColumn(leftColumn.getName());
					EntityJoinTree.copyNodeToParent(passiveJoin, joinNode, localLeftColumn);
				}
			});
		}
		
		@Override
		protected void addWriteCascades(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister) {
			// whatever kind of relation maintenance mode asked, we have to insert and update source-to-target link, because we are in relation-owned-by-source
			Function<SRC, TRGTID> targetIdProvider = src -> {
				TRGT trgt = cascadeOne.getTargetProvider().get(src);
				return trgt == null ? null : targetPersister.getMappingStrategy().getId(trgt);
			};
			sourcePersister.getMappingStrategy().addShadowColumnInsert(new ShadowColumnValueProvider<>(leftColumn, targetIdProvider));
			sourcePersister.getMappingStrategy().addShadowColumnUpdate(new ShadowColumnValueProvider<>(leftColumn, targetIdProvider));
			
			super.addWriteCascades(targetPersister);
		}
		
		@Override
		protected void addInsertCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister) {
			super.addInsertCascade(targetPersister);
			// adding cascade treatment: before source insert, target is inserted to comply with foreign key constraint
			sourcePersister.addInsertListener(new BeforeInsertSupport<>(targetPersister::persist, cascadeOne.getTargetProvider()::get, Objects::nonNull));
		}
		
		@Override
		protected void addUpdateCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister, boolean orphanRemoval) {
			// if cascade is mandatory, then adding nullability checking before insert
			if (!cascadeOne.isNullable()) {
				sourcePersister.addUpdateListener(new MandatoryRelationCheckingBeforeUpdateListener<>(cascadeOne.getTargetProvider()));
			}
			// adding cascade treatment
			// - insert non-persisted target instances to fulfill foreign key requirement
			Function<SRC, TRGT> targetProviderAsFunction = new NullProofFunction<>(cascadeOne.getTargetProvider()::get);
			sourcePersister.addUpdateListener(new BeforeUpdateSupport<>(
					// we insert new instances
					(it, b) -> targetPersister.insert(Iterables.collectToList(it, Duo::getLeft)),
					targetProviderAsFunction,
					// we only keep targets of modified instances, non null and not yet persisted
					Predicates.predicate(Duo::getLeft, Predicates.<TRGT>predicate(Objects::nonNull).and(targetPersister.getMappingStrategy()::isNew))
			));
			// - after source update, target is updated too
			sourcePersister.addUpdateListener(new UpdateListener<SRC>() {
				
				@Override
				public void afterUpdate(Iterable<? extends Duo<? extends SRC, ? extends SRC>> payloads, boolean allColumnsStatement) {
					List<Duo<TRGT, TRGT>> targetsToUpdate = Iterables.collect(payloads,
							// targets of nullified relations don't need to be updated 
							e -> getTarget(e.getLeft()) != null,
							e -> getTargets(e.getLeft(), e.getRight()),
							ArrayList::new);
					targetPersister.update(targetsToUpdate, allColumnsStatement);
				}
				
				private Duo<TRGT, TRGT> getTargets(SRC modifiedTrigger, SRC unmodifiedTrigger) {
					return new Duo<>(getTarget(modifiedTrigger), getTarget(unmodifiedTrigger));
				}
				
				private TRGT getTarget(SRC src) {
					return cascadeOne.getTargetProvider().get(src);
				}
			});
			if (orphanRemoval) {
				sourcePersister.addUpdateListener(new OrphanRemovalOnUpdate<>(targetPersister, cascadeOne.getTargetProvider()));
			}
		}
		
		@Override
		protected void addDeleteCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister, boolean orphanRemoval) {
			if (orphanRemoval) {
				// adding cascade treatment: target is deleted after source deletion (because of foreign key constraint)
				Predicate<TRGT> deletionPredicate = ((Predicate<TRGT>) Objects::nonNull).and(not(targetPersister.getMappingStrategy().getIdMappingStrategy()::isNew));
				sourcePersister.addDeleteListener(new AfterDeleteSupport<>(targetPersister::delete, cascadeOne.getTargetProvider()::get, deletionPredicate));
				// we add the deleteById event since we suppose that if delete is required then there's no reason that rough delete is not
				sourcePersister.addDeleteByIdListener(new AfterDeleteByIdSupport<>(targetPersister::delete, cascadeOne.getTargetProvider()::get, deletionPredicate));
			} // else : no target entities deletion asked (no delete orphan) : nothing more to do than deleting the source entity
		}
	}
	
	
	private class RelationOwnedByTargetConfigurer extends ConfigurerTemplate {
		
		/**
		 * Foreign key column value store, for update and delete cases : stores column value per bean,
		 * can be a nullifying function, or an id provider to the referenced source entity.
		 * Implemented as a ThreadLocal because we can hardly cross layers and methods to pass such a value.
		 * Cleaned after update and delete.
		 */
		private final ThreadLocal<ForeignKeyValueProvider> currentForeignKeyValueProvider = new ThreadLocal<>();
		
		private class ForeignKeyValueProvider {
			private final Set<ForeignKeyHolder> store = new HashSet<>();
			
			private void add(TRGT target, SRC source) {
				store.add(new ForeignKeyHolder(target, source));
			}
			
			private SRCID giveSourceId(TRGT trgt) {
				return nullable(Iterables.find(store, fk -> fk.modifiedTarget == trgt)).map(x -> x.modifiedSource).map(sourcePersister.getMappingStrategy()::getId).get();
			}
			
			/**
			 * Small class to store relating source and target entities 
			 */
			private class ForeignKeyHolder {
				private final TRGT modifiedTarget;
				private final SRC modifiedSource;
				
				public ForeignKeyHolder(TRGT modifiedTarget, SRC modifiedSource) {
					this.modifiedTarget = modifiedTarget;
					this.modifiedSource = modifiedSource;
				}
			}
		}
		
		private RelationOwnedByTargetConfigurer(IEntityConfiguredJoinedTablesPersister<SRC, SRCID> sourcePersister) {
			super(sourcePersister);
		}
		
		@Override
		protected void determineForeignKeyColumns(IEntityMappingStrategy<SRC, SRCID, ?> mappingStrategy,
												  IEntityMappingStrategy<TRGT, TRGTID, ?> targetMappingStrategy) {
			
			// left column is always left table primary key
			leftColumn = (Column<Table, SRCID>) Iterables.first(mappingStrategy.getTargetTable().getPrimaryKey().getColumns());
			// right column depends on relation owner
			if (cascadeOne.getReverseColumn() != null) {
				rightColumn = cascadeOne.getReverseColumn();
			}
			if (cascadeOne.getReverseGetter() != null) {
				AccessorByMethodReference<TRGT, SRC> localReverseGetter = Accessors.accessorByMethodReference(cascadeOne.getReverseGetter());
				AccessorDefinition accessorDefinition = AccessorDefinition.giveDefinition(localReverseGetter);
				// we add a column for reverse mapping if one is not already declared
				rightColumn = createOrUseReverseColumn(targetMappingStrategy, cascadeOne.getReverseColumn(), localReverseGetter, accessorDefinition);
			} else if (cascadeOne.getReverseSetter() != null) {
				ValueAccessPoint reverseSetter = Accessors.mutatorByMethodReference(cascadeOne.getReverseSetter());
				AccessorDefinition accessorDefinition = AccessorDefinition.giveDefinition(reverseSetter);
				// we add a column for reverse mapping if one is not already declared
				rightColumn = createOrUseReverseColumn(targetMappingStrategy, cascadeOne.getReverseColumn(), reverseSetter, accessorDefinition);
			}
			
			// adding foreign key constraint
			String foreignKeyName = foreignKeyNamingStrategy.giveName(rightColumn, leftColumn);
			// Note that rightColumn can't be null because RelationOwnedByTargetConfigurer is used when one of cascadeOne.getReverseColumn(),
			// cascadeOne.getReverseGetter() and cascadeOne.getReverseSetter() is not null
			rightColumn.getTable().addForeignKey(foreignKeyName, rightColumn, leftColumn);
		}
		
		private Column createOrUseReverseColumn(IEntityMappingStrategy<TRGT, TRGTID, ?> targetMappingStrategy,
												Column reverseColumn,
												ValueAccessPoint reverseGetter,
												AccessorDefinition accessorDefinition) {
			if (reverseColumn == null) {
				// no reverse column was given, so we look for the one mapped under the reverse getter
				reverseColumn = targetMappingStrategy.getPropertyToColumn().get(reverseGetter);
				if (reverseColumn == null) {
					// no column is defined under the getter, then we have to create one
					Set<Column> pkColumns = sourcePersister.getMappingStrategy().getTargetTable().getPrimaryKey().getColumns();
					reverseColumn = targetMappingStrategy.getTargetTable().addColumn(
							joinColumnNamingStrategy.giveName(accessorDefinition),
							Iterables.first(pkColumns).getJavaType()
					);
				}
			}
			return reverseColumn;
		}
		
		@Override
		protected void addWriteCascades(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister) {
			boolean writeAuthorized = cascadeOne.getRelationMode() != READ_ONLY;
			if (writeAuthorized) {
				super.addWriteCascades(targetPersister);
			} else {
				// write is not authorized but we must maintain reverse column, so we'll create an update order for it
				
				// small class that helps locally to maintain SQL-update statement and its values
				class ForeignKeyUpdateOrderProvider {
					
					private WriteOperation<UpwhereColumn<Table>> generateOperation() {
						PreparedUpdate<Table> tablePreparedUpdate = dialect.getDmlGenerator().buildUpdate(
								Arrays.asList((Column<Table, Object>) rightColumn),
								targetPersister.getMappingStrategy().getVersionedKeys());
						return new WriteOperation<>(tablePreparedUpdate,
								connectionConfiguration.getConnectionProvider());
					}
					
					private <C> void addValuesToUpdateBatch(Iterable<? extends C> entities,
															Function<C, SRCID> fkValueProvider,
															Function<C, SRC> sourceProvider,
															WriteOperation<UpwhereColumn<Table>> updateOrder) {
						entities.forEach(e -> {
							Map<UpwhereColumn<Table>, Object> values = new HashMap<>();
							values.put(new UpwhereColumn<>(rightColumn, true), fkValueProvider.apply(e));
							targetPersister.getMappingStrategy().getVersionedKeyValues(cascadeOne.getTargetProvider().get(sourceProvider.apply(e)))
									.forEach((c, o) -> values.put(new UpwhereColumn<>(c, false), o));
							updateOrder.addBatch(values);
						});
						
					}
				}
				
				ForeignKeyUpdateOrderProvider foreignKeyUpdateOrderProvider = new ForeignKeyUpdateOrderProvider();
				sourcePersister.getPersisterListener().addInsertListener(new InsertListener<SRC>() {
					
					/**
					 * Implemented to update target owning column after insert. Made AFTER insert to benefit from id when set by database with
					 * IdentifierPolicy is AFTER_INSERT
					 */
					@Override
					public void afterInsert(Iterable<? extends SRC> entities) {
						WriteOperation<UpwhereColumn<Table>> upwhereColumnWriteOperation = foreignKeyUpdateOrderProvider.generateOperation();
						foreignKeyUpdateOrderProvider.<SRC>addValuesToUpdateBatch(entities, sourcePersister::getId, Function.identity(), upwhereColumnWriteOperation);
						upwhereColumnWriteOperation.executeBatch();
					}
				});
				
				sourcePersister.getPersisterListener().addUpdateListener(new UpdateListener<SRC>() {
					
					@Override
					public void afterUpdate(Iterable<? extends Duo<? extends SRC, ? extends SRC>> entities, boolean allColumnsStatement) {
						WriteOperation<UpwhereColumn<Table>> upwhereColumnWriteOperation = foreignKeyUpdateOrderProvider.generateOperation();
						foreignKeyUpdateOrderProvider.<Duo<SRC, SRC>>addValuesToUpdateBatch((Iterable<? extends Duo<SRC, SRC>>) entities,
								duo -> sourcePersister.getId(duo.getLeft()),
								Duo::getLeft,
								upwhereColumnWriteOperation);
						foreignKeyUpdateOrderProvider.<Duo<SRC, SRC>>addValuesToUpdateBatch((Iterable<? extends Duo<SRC, SRC>>) entities,
								duo -> null,
								Duo::getRight,
								upwhereColumnWriteOperation);
						upwhereColumnWriteOperation.executeBatch();
						
					}
				});
				
				sourcePersister.getPersisterListener().addDeleteListener(new DeleteListener<SRC>() {
					
					/**
					 * Implemented to nullify target owning column before insert.
					 */
					@Override
					public void beforeDelete(Iterable<SRC> entities) {
						WriteOperation<UpwhereColumn<Table>> upwhereColumnWriteOperation = foreignKeyUpdateOrderProvider.generateOperation();
						foreignKeyUpdateOrderProvider.<SRC>addValuesToUpdateBatch(entities,
								duo -> null,
								Function.identity(),
								upwhereColumnWriteOperation);
						upwhereColumnWriteOperation.executeBatch();
						
					}
				});
			}
		}
		
		@Override
		protected void determineRelationFixer() {
			IMutator<SRC, TRGT> sourceIntoTargetFixer = cascadeOne.getTargetProvider().toMutator();
			if (cascadeOne.getReverseGetter() != null) {
				AccessorByMethodReference<TRGT, SRC> localReverseGetter = Accessors.accessorByMethodReference(cascadeOne.getReverseGetter());
				AccessorDefinition accessorDefinition = AccessorDefinition.giveDefinition(localReverseGetter);
				
				// we take advantage of foreign key computing and presence of AccessorDefinition to build relation fixer which is needed lately in determineRelationFixer(..) 
				IMutator<TRGT, SRC> targetIntoSourceFixer = Accessors.mutatorByMethod(accessorDefinition.getDeclaringClass(), accessorDefinition.getName());
				this.beanRelationFixer = (src, target) -> {
					// fixing source on target
					if (target != null) {	// prevent NullPointerException, actually means no linked entity (null relation), so nothing to do
						targetIntoSourceFixer.set(target, src);
					}
					// fixing target on source
					sourceIntoTargetFixer.set(src, target);
				};
			} else if (cascadeOne.getReverseSetter() != null) {
				// we take advantage of forign key computing and presence of AccessorDefinition to build relation fixer which is needed lately in determineRelationFixer(..) 
				this.beanRelationFixer = (target, input) -> {
					// fixing target on source side
					cascadeOne.getReverseSetter().accept(input, target);
					// fixing source on target side
					sourceIntoTargetFixer.set(target, input);
				};
			}
			else {
				// non bidirectional relation : relation is owned by target without defining any way to fix it in memory
				// we can only fix target on source side
				this.beanRelationFixer = sourceIntoTargetFixer::set;
			}
		}
		
		@Override
		protected void addSelectCascadeIn2Phases(
				String tableAlias,
				IConfiguredJoinedTablesPersister<TRGT, TRGTID> targetPersister,
				FirstPhaseCycleLoadListener<SRC, TRGTID> firstPhaseCycleLoadListener) {
			
			Table targetTable = targetPersister.getMappingStrategy().getTargetTable();
			Column targetPrimaryKey = (Column) Iterables.first(targetTable.getPrimaryKey().getColumns());
			Table targetTableClone = new Table(targetTable.getName());
			Column relationOwnerForeignKey = targetTableClone.addColumn(rightColumn.getName(), rightColumn.getJavaType());
			Column relationOwnerPrimaryKey = targetTableClone.addColumn(targetPrimaryKey.getName(), targetPrimaryKey.getJavaType());
			String joinName = sourcePersister.getEntityJoinTree().addPassiveJoin(ROOT_STRATEGY_NAME,
					leftColumn,
					relationOwnerForeignKey,
					tableAlias,
					cascadeOne.isNullable() ? JoinType.OUTER : JoinType.INNER, (Set) Arrays.asSet(relationOwnerPrimaryKey),
					(src, rowValueProvider) -> firstPhaseCycleLoadListener.onFirstPhaseRowRead(src, (TRGTID) rowValueProvider.apply(relationOwnerPrimaryKey)), false);
			
			// Propagating 2-phases load to all nodes that use cycling type
			PassiveJoinNode passiveJoin = (PassiveJoinNode) sourcePersister.getEntityJoinTree().getJoin(joinName);
			targetPersister.getEntityJoinTree().foreachJoin(joinNode -> {
				if (joinNode instanceof RelationJoinNode
						&& ((RelationJoinNode<?, ?, ?, ?>) joinNode).getEntityInflater().getEntityType() == sourcePersister.getClassToPersist()) {
					Column localLeftColumn = joinNode.getTable().getColumn(leftColumn.getName());
					EntityJoinTree.copyNodeToParent(passiveJoin, joinNode, localLeftColumn);
				}
			});
		}
		
		@Override
		protected void addInsertCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister) {
			super.addInsertCascade(targetPersister);
			// adding cascade treatment: after source insert, target is persisted
			// Please note that we collect entities in a Set to avoid persisting duplicates twice which may produce constraint exception if some source
			// entities points to same target entity. In details the Set is an identity Set to avoid basing our comparison on implemented
			// equals/hashCode although this could be sufficent, identity seems safer and match our logic.
			Collector<TRGT, ?, Set<TRGT>> identitySetProvider = Collectors.toCollection(org.gama.lang.collection.Collections::newIdentitySet);
			Consumer<Iterable<? extends SRC>> persistTargetCascader = entities -> {
				targetPersister.persist(Iterables.stream(entities).map(cascadeOne.getTargetProvider()::get).filter(Objects::nonNull).collect(identitySetProvider));
			};
			// Please note that 1st implementation was to simply add persistTargetCascader, but it invokes persist() which may invoke update()
			// and because we are in the relation-owned-by-target case targetPersister.update() needs foreign key value provider to be
			// fullfilled (see addUpdateCascade for addShadowColumnUpdate), so we wrap persistTargetCascader with a foreign key value provider.
			// This focuses particular use case when a target is modified and newly assigned to the source
			sourcePersister.addInsertListener(new InsertListener<SRC>() {
				/**
				 * Implemented to persist target instance after insert. Made AFTER insert to benefit from id when set by database with
				 * IdentifierPolicy is AFTER_INSERT
				 */
				@Override
				public void afterInsert(Iterable<? extends SRC> entities) {
					ThreadLocals.doWithThreadLocal(currentForeignKeyValueProvider, ForeignKeyValueProvider::new, (Consumer<ForeignKeyValueProvider>) fkValueProvider -> {
						for (SRC sourceEntity : entities) {
							currentForeignKeyValueProvider.get().add(cascadeOne.getTargetProvider().get(sourceEntity), sourceEntity);
						}
						persistTargetCascader.accept(entities);
					});
				}
			});
			targetPersister.getMappingStrategy().addShadowColumnInsert(new ShadowColumnValueProvider<>((Column<Table, SRCID>) rightColumn,
					// in many cases currentForeignKeyValueProvider is already present through source persister listener (insert or update)
					// but in the corner case of source and target persist same type (in a parent -> child case) then at very first insert of root
					// instance, currentForeignKeyValueProvider is not present, so we prevent this by initializing it 
					trgt -> {
						if (currentForeignKeyValueProvider.get() == null) {
							currentForeignKeyValueProvider.set(new ForeignKeyValueProvider());
						}
						return currentForeignKeyValueProvider.get().giveSourceId(trgt);
					}
			));
		}
		
		@Override
		protected void addUpdateCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister, boolean orphanRemoval) {
			// if cascade is mandatory, then adding nullability checking before insert
			if (!cascadeOne.isNullable()) {
				sourcePersister.addUpdateListener(new MandatoryRelationCheckingBeforeUpdateListener<>(cascadeOne.getTargetProvider()));
			}
			// adding cascade treatment, please note that this will also be used by insert cascade if target is already persisted
			targetPersister.getMappingStrategy().addShadowColumnUpdate(new ShadowColumnValueProvider<>((Column<Table, SRCID>) rightColumn,
					trgt -> {
						if (currentForeignKeyValueProvider.get() == null) {
							currentForeignKeyValueProvider.set(new ForeignKeyValueProvider());
						}
						return currentForeignKeyValueProvider.get().giveSourceId(trgt);
					}));
			
			// - after source update, target is updated too
			sourcePersister.addUpdateListener(new UpdateListener<SRC>() {
				
				@Override
				public void afterUpdate(Iterable<? extends Duo<? extends SRC, ? extends SRC>> payloads, boolean allColumnsStatement) {
					if (currentForeignKeyValueProvider.get() == null) {
						currentForeignKeyValueProvider.set(new ForeignKeyValueProvider());
					}
					List<TRGT> newObjects = new ArrayList<>();
					List<Duo<TRGT, TRGT>> existingEntities = new ArrayList<>();
					
					// very small class to ease regetring entities to be persisted
					class PersisterHelper {
						
						void markToPersist(TRGT targetOfModified, SRC modifiedSource) {
							if (targetPersister.getMappingStrategy().isNew(targetOfModified)) {
								newObjects.add(targetOfModified);
							} else {
								existingEntities.add(new Duo<>(targetOfModified, null));
							}
							currentForeignKeyValueProvider.get().add(targetOfModified, modifiedSource);
						}
					}
					
					List<TRGT> nullifiedRelations = new ArrayList<>();
					PersisterHelper persisterHelper = new PersisterHelper(); 
					for (Duo<? extends SRC, ? extends SRC> payload : payloads) {
						
						TRGT targetOfModified = getTarget(payload.getLeft());
						TRGT targetOfUnmodified = getTarget(payload.getRight());
						if (targetOfModified == null && targetOfUnmodified != null) {
							// "REMOVED"
							// relation is nullified : relation column should be nullified too
							nullifiedRelations.add(targetOfUnmodified);
						} else if (targetOfModified != null && targetOfUnmodified == null) {
							// "ADDED"
							// newly set relation, entities will fully inserted / updated some lines above, nothing to do 
							// relation is set, we fully update modified bean, then its properties will be updated too
							persisterHelper.markToPersist(targetOfModified, payload.getLeft());
						} else if (targetOfModified != null) {
							// "HELD"
							persisterHelper.markToPersist(targetOfModified, payload.getLeft());
							// Was target entity reassigned to another one ? Relation changed to another entity : we must nullify reverse column of detached target
							if (!targetPersister.getMappingStrategy().getId(targetOfUnmodified).equals(targetPersister.getMappingStrategy().getId(targetOfModified))) {
								nullifiedRelations.add(targetOfUnmodified);
							}
						} // else both sides are null => nothing to do
					}
					
					targetPersister.insert(newObjects);
					targetPersister.update(existingEntities, allColumnsStatement);
					targetPersister.updateById(nullifiedRelations);
					
					currentForeignKeyValueProvider.remove();
				}
				
				private TRGT getTarget(SRC src) {
					return src == null ? null : cascadeOne.getTargetProvider().get(src);
				}
				
				@Override
				public void onError(Iterable<? extends SRC> entities, RuntimeException runtimeException) {
					currentForeignKeyValueProvider.remove();
				}
			});
			if (orphanRemoval) {
				sourcePersister.addUpdateListener(new OrphanRemovalOnUpdate<>(targetPersister, cascadeOne.getTargetProvider()));
			}
		}
		
		@Override
		protected void addDeleteCascade(IEntityConfiguredPersister<TRGT, TRGTID> targetPersister, boolean deleteTargetEntities) {
			if (deleteTargetEntities) {
				// adding cascade treatment: target is deleted before source deletion (because of foreign key constraint)
				Predicate<TRGT> deletionPredicate = ((Predicate<TRGT>) Objects::nonNull).and(not(targetPersister.getMappingStrategy().getIdMappingStrategy()::isNew));
				sourcePersister.addDeleteListener(new BeforeDeleteSupport<>(targetPersister::delete, cascadeOne.getTargetProvider()::get, deletionPredicate));
				// we add the deleteById event since we suppose that if delete is required then there's no reason that rough delete is not
				sourcePersister.addDeleteByIdListener(new BeforeDeleteByIdSupport<>(targetPersister::delete, cascadeOne.getTargetProvider()::get, deletionPredicate));
			} else {
				// no target entities deletion asked (no delete orphan) : we only need to nullify the relation
				sourcePersister.addDeleteListener(new NullifyRelationColumnBeforeDelete(cascadeOne, targetPersister));
			}
		}
		
		private class NullifyRelationColumnBeforeDelete implements DeleteListener<SRC> {
			
			private final CascadeOne<SRC, TRGT, TRGTID> cascadeOne;
			private final IEntityConfiguredPersister<TRGT, TRGTID> targetPersister;
			
			private NullifyRelationColumnBeforeDelete(CascadeOne<SRC, TRGT, TRGTID> cascadeOne, IEntityConfiguredPersister<TRGT, TRGTID> targetPersister) {
				this.cascadeOne = cascadeOne;
				this.targetPersister = targetPersister;
			}
			
			@Override
			public void beforeDelete(Iterable<SRC> entities) {
				ThreadLocals.doWithThreadLocal(currentForeignKeyValueProvider, ForeignKeyValueProvider::new, (Consumer<ForeignKeyValueProvider>) fkValueProvider ->
					this.targetPersister.updateById(Iterables.stream(entities).map(this::getTarget).filter(Objects::nonNull).peek(trgt ->
							fkValueProvider.add(trgt, null))
							.collect(Collectors.toList())
					)
				);
			}
			
			private TRGT getTarget(SRC src) {
				TRGT target = cascadeOne.getTargetProvider().get(src);
				// We only delete persisted instances (for logic and to prevent from non matching row count error)
				return target != null && !targetPersister.getMappingStrategy().getIdMappingStrategy().isNew(target) ? target : null;
			}
		}
	}
	
	public static class MandatoryRelationCheckingBeforeInsertListener<C> implements InsertListener<C> {
		
		private final IAccessor<C, ?> targetAccessor;
		
		public MandatoryRelationCheckingBeforeInsertListener(IAccessor<C, ?> targetAccessor) {
			this.targetAccessor = targetAccessor;
		}
		
		@Override
		public void beforeInsert(Iterable<? extends C> entities) {
			for (C pawn : entities) {
				Object modifiedTarget = targetAccessor.get(pawn);
				if (modifiedTarget == null) {
					throw newRuntimeMappingException(pawn, targetAccessor);
				}
			}
		}
	}
	
	public static class MandatoryRelationCheckingBeforeUpdateListener<C> implements UpdateListener<C> {
		
		private final IAccessor<C, ?> targetAccessor;
		
		public MandatoryRelationCheckingBeforeUpdateListener(IAccessor<C, ?> targetAccessor) {
			this.targetAccessor = targetAccessor;
		}
		
		@Override
		public void beforeUpdate(Iterable<? extends Duo<? extends C, ? extends C>> payloads, boolean allColumnsStatement) {
			for (Duo<? extends C, ? extends C> payload : payloads) {
				C modifiedEntity = payload.getLeft();
				Object modifiedTarget = targetAccessor.get(modifiedEntity);
				if (modifiedTarget == null) {
					throw newRuntimeMappingException(modifiedEntity, targetAccessor);
				}
			}
		}
	}
	
	public static RuntimeMappingException newRuntimeMappingException(Object pawn, ValueAccessPoint accessor) {
		return new RuntimeMappingException("Non null value expected for relation "
				+ AccessorDefinition.toString(accessor) + " on object " + pawn);
	}
	
	private static class OrphanRemovalOnUpdate<SRC, TRGT> implements UpdateListener<SRC> {
		
		private final IEntityConfiguredPersister<TRGT, ?> targetPersister;
		private final IAccessor<SRC, TRGT> targetAccessor;
		private final IAccessor<TRGT, ?> targetIdAccessor;
		
		private OrphanRemovalOnUpdate(IEntityConfiguredPersister<TRGT, ?> targetPersister, IAccessor<SRC, TRGT> targetAccessor) {
			this.targetPersister = targetPersister;
			this.targetAccessor = targetAccessor;
			this.targetIdAccessor = targetPersister.getMappingStrategy().getIdMappingStrategy().getIdAccessor()::getId;
		}
		
		@Override
		public void afterUpdate(Iterable<? extends Duo<? extends SRC, ? extends SRC>> payloads, boolean allColumnsStatement) {
				List<TRGT> targetsToDeleteUpdate = new ArrayList<>();
				payloads.forEach(duo -> {
					TRGT newTarget = getTarget(duo.getLeft());
					TRGT oldTarget = getTarget(duo.getRight());
					// nullified relations and changed ones must be removed (orphan removal)
					// TODO : one day we'll have to cover case of reused instance in same graph : one of the relation must handle it, not both,
					//  "else a marked instance" system must be implemented
					if (newTarget == null || (oldTarget != null && !targetIdAccessor.get(newTarget).equals(targetIdAccessor.get(oldTarget)))) {
						targetsToDeleteUpdate.add(oldTarget);
					}
				});
				targetPersister.delete(targetsToDeleteUpdate);
		}
		
		private TRGT getTarget(SRC src) {
			return src == null ? null : targetAccessor.get(src);
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
		private IEntityConfiguredJoinedTablesPersister<SRC, ?> sourcePersister;
		
		public ConfigurationResult(BeanRelationFixer<SRC, TRGT> beanRelationFixer,
								   IEntityConfiguredJoinedTablesPersister<SRC, ?> sourcePersister) {
			this.beanRelationFixer = beanRelationFixer;
			this.sourcePersister = sourcePersister;
		}
		
		public <SRCID> IEntityConfiguredJoinedTablesPersister<SRC, SRCID> getSourcePersister() {
			return (IEntityConfiguredJoinedTablesPersister<SRC, SRCID>) sourcePersister;
		}
		
		public BeanRelationFixer<SRC, TRGT> getBeanRelationFixer() {
			return beanRelationFixer;
		}
	}
}
