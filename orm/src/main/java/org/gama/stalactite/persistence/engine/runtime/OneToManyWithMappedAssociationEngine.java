package org.gama.stalactite.persistence.engine.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gama.lang.Duo;
import org.gama.lang.bean.Objects;
import org.gama.lang.collection.Iterables;
import org.gama.stalactite.persistence.engine.BeanRelationFixer;
import org.gama.stalactite.persistence.engine.NotYetSupportedOperationException;
import org.gama.stalactite.persistence.engine.Persister;
import org.gama.stalactite.persistence.engine.cascade.AfterInsertCollectionCascader;
import org.gama.stalactite.persistence.engine.cascade.AfterUpdateCollectionCascader;
import org.gama.stalactite.persistence.engine.cascade.BeforeDeleteByIdCollectionCascader;
import org.gama.stalactite.persistence.engine.cascade.BeforeDeleteCollectionCascader;
import org.gama.stalactite.persistence.engine.cascade.JoinedStrategiesSelect;
import org.gama.stalactite.persistence.engine.cascade.JoinedStrategiesSelect.StrategyJoins;
import org.gama.stalactite.persistence.engine.cascade.JoinedTablesPersister;
import org.gama.stalactite.persistence.engine.listening.SelectListener;
import org.gama.stalactite.persistence.engine.listening.UpdateListener.UpdatePayload;
import org.gama.stalactite.persistence.structure.Column;

import static org.gama.lang.bean.Objects.not;
import static org.gama.lang.collection.Iterables.stream;
import static org.gama.stalactite.persistence.engine.cascade.JoinedStrategiesSelect.FIRST_STRATEGY_NAME;

/**
 * @author Guillaume Mary
 */
public class OneToManyWithMappedAssociationEngine<SRC, TRGT, SRCID, TRGTID, C extends Collection<TRGT>> {
	
	/** Empty setter for applying source entity to target entity (reverse side) */
	protected static final BiConsumer NOOP_REVERSE_SETTER = (o, i) -> {
		/* Having a reverse setter in one to many relation with intermediary table isn't possible (cascadeMany.getReverseSetter() is null)
		 * because as soon as "mappedBy" is used (which fills reverseSetter), an intermediary table is not possible
		 */
	};
	
	protected final JoinedTablesPersister<SRC, SRCID, ?> sourcePersister;
	
	protected final Persister<TRGT, TRGTID, ?> targetPersister;
	
	protected final MappedManyRelationDescriptor<SRC, TRGT, C> manyRelationDefinition;
	
	public OneToManyWithMappedAssociationEngine(Persister<TRGT, TRGTID, ?> targetPersister,
												MappedManyRelationDescriptor<SRC, TRGT, C> manyRelationDefinition,
												JoinedTablesPersister<SRC, SRCID, ?> sourcePersister) {
		this.targetPersister = targetPersister;
		this.manyRelationDefinition = manyRelationDefinition;
		this.sourcePersister = sourcePersister;
	}
	
	public void addSelectCascade(Column sourcePrimaryKey,
								 Column relationshipOwner    // foreign key on target table
	) {
		// configuring select for fetching relation
		BeanRelationFixer<SRC, TRGT> relationFixer = BeanRelationFixer.of(
				manyRelationDefinition.getCollectionSetter(),
				manyRelationDefinition.getCollectionGetter(),
				manyRelationDefinition.getCollectionClass(),
				Objects.preventNull(manyRelationDefinition.getReverseSetter(), NOOP_REVERSE_SETTER));
		
		String createdJoinNodeName = sourcePersister.addPersister(FIRST_STRATEGY_NAME,
				targetPersister,
				relationFixer,
				sourcePrimaryKey,
				relationshipOwner,
				relationshipOwner.isNullable()); // outer join for empty relation cases
		if (targetPersister instanceof JoinedTablesPersister) {
			addSubgraphSelect(createdJoinNodeName, sourcePersister, (JoinedTablesPersister<TRGT, TRGTID, ?>) targetPersister, manyRelationDefinition.getCollectionGetter());
		}
	}
	
	static <SRC, TRGT, SRCID, TRGTID, C extends Collection<TRGT>> void addSubgraphSelect(String joinName,
								   JoinedTablesPersister<SRC, SRCID, ?> sourcePersister,
								   JoinedTablesPersister<TRGT, TRGTID, ?> targetPersister,
								   final Function<SRC, C> targetProvider) {
		// we add target subgraph joins to the one that was created 
		StrategyJoins targetJoinsSubgraphRoot = targetPersister.getJoinedStrategiesSelectExecutor().getJoinedStrategiesSelect().getJoinsRoot();
		JoinedStrategiesSelect sourceSelector = sourcePersister.getJoinedStrategiesSelectExecutor().getJoinedStrategiesSelect();
		targetJoinsSubgraphRoot.copyTo(sourceSelector, joinName);
		
		// we must trigger subgraph event on loading of our own graph, this is mainly for event that initializes thngs because given ids
		// are not those of their entity
		SelectListener targetSelectListener = targetPersister.getPersisterListener().getSelectListener();
		sourcePersister.getPersisterListener().addSelectListener(new SelectListener<SRC, SRCID>() {
			@Override
			public void beforeSelect(Iterable<SRCID> ids) {
				// since ids are not those of its entities, we should not pass them as argument, this will only initialize things if needed
				targetSelectListener.beforeSelect(Collections.emptyList());
			}

			@Override
			public void afterSelect(Iterable<? extends SRC> result) {
				Iterable collect = Iterables.stream(result).flatMap(src -> targetProvider.apply(src).stream()).collect(Collectors.toSet());
				targetSelectListener.afterSelect(collect);
			}

			@Override
			public void onError(Iterable<SRCID> ids, RuntimeException exception) {
				// since ids are not those of its entities, we should not pass them as argument
				targetSelectListener.onError(Collections.emptyList(), exception);
			}
		});
	}
	
	public void addInsertCascade() {
		sourcePersister.getPersisterListener().addInsertListener(
				new OneToManyWithMappedAssociationEngine.TargetInstancesInsertCascader<>(targetPersister, manyRelationDefinition.getCollectionGetter()));
	}
	
	public void addUpdateCascade(boolean shouldDeleteRemoved) {
		BiConsumer<UpdatePayload<? extends SRC, ?>, Boolean> updateListener = new CollectionUpdater<>(
				manyRelationDefinition.getCollectionGetter(),
				targetPersister,
				manyRelationDefinition.getReverseSetter(),
				shouldDeleteRemoved);
		sourcePersister.getPersisterListener().addUpdateListener(
				new OneToManyWithMappedAssociationEngine.TargetInstancesUpdateCascader<>(targetPersister, updateListener));
	}
	
	public void addDeleteCascade(boolean deleteTargetEntities) {
		if (deleteTargetEntities) {
			// adding deletion of many-side entities
			sourcePersister.getPersisterListener().addDeleteListener(
					new DeleteTargetEntitiesBeforeDeleteCascader<>(targetPersister, manyRelationDefinition.getCollectionGetter()));
			// we add the deleteById event since we suppose that if delete is required then there's no reason that rough delete is not
			sourcePersister.getPersisterListener().addDeleteByIdListener(
					new DeleteByIdTargetEntitiesBeforeDeleteByIdCascader<>(targetPersister, manyRelationDefinition.getCollectionGetter()));
		} else // entity shouldn't be deleted, so we may have to update it
			if (manyRelationDefinition.getReverseSetter() != null) {
				// we cut the link between target and source
				// NB : we don't take versioning into account overall because we can't : how to do it since we miss the unmodified version ?
				sourcePersister.getPersisterListener().addDeleteListener(new BeforeDeleteCollectionCascader<SRC, TRGT>(targetPersister) {
					
					@Override
					protected void postTargetDelete(Iterable<TRGT> entities) {
						// nothing to do after deletion
					}
					
					@Override
					public void beforeDelete(Iterable<SRC> entities) {
						List<TRGT> targets = stream(entities).flatMap(c -> getTargets(c).stream()).collect(Collectors.toList());
						targets.forEach(e -> manyRelationDefinition.getReverseSetter().accept(e, null));
						targetPersister.updateById(targets);
					}
					
					@Override
					protected Collection<TRGT> getTargets(SRC src) {
						Collection<TRGT> targets = manyRelationDefinition.getCollectionGetter().apply(src);
						// We only delete persisted instances (for logic and to prevent from non matching row count exception)
						return stream(targets)
								.filter(not(targetPersister.getMappingStrategy()::isNew))
								.collect(Collectors.toList());
					}
				});
			}
	}
	
	static class TargetInstancesInsertCascader<I, O, J> extends AfterInsertCollectionCascader<I, O> {
		
		private final Function<I, ? extends Collection<O>> collectionGetter;
		
		public TargetInstancesInsertCascader(Persister<O, J, ?> targetPersister, Function<I, ? extends Collection<O>> collectionGetter) {
			super(targetPersister);
			this.collectionGetter = collectionGetter;
		}
		
		@Override
		protected void postTargetInsert(Iterable<? extends O> entities) {
			// Nothing to do. Identified#isPersisted flag should be fixed by target persister
		}
		
		@Override
		protected Collection<O> getTargets(I source) {
			Collection<O> targets = collectionGetter.apply(source);
			// We only insert non-persisted instances (for logic and to prevent duplicate primary key error)
			return Iterables.stream(targets)
					.filter(getPersister().getMappingStrategy()::isNew)
					.collect(Collectors.toList());
		}
	}
	
	static class TargetInstancesUpdateCascader<I, O> extends AfterUpdateCollectionCascader<I, O> {
		
		private final BiConsumer<UpdatePayload<? extends I, ?>, Boolean> updateListener;
		
		public TargetInstancesUpdateCascader(Persister<O, ?, ?> targetPersister, BiConsumer<UpdatePayload<? extends I, ?>, Boolean> updateListener) {
			super(targetPersister);
			this.updateListener = updateListener;
		}
		
		@Override
		public void afterUpdate(Iterable<UpdatePayload<? extends I, ?>> entities, boolean allColumnsStatement) {
			entities.forEach(entry -> updateListener.accept(entry, allColumnsStatement));
		}
		
		@Override
		protected void postTargetUpdate(Iterable<UpdatePayload<? extends O, ?>> entities) {
			// Nothing to do
		}
		
		@Override
		protected Collection<Duo<O, O>> getTargets(I modifiedTrigger, I unmodifiedTrigger) {
			throw new NotYetSupportedOperationException();
		}
	}
	
	static class DeleteTargetEntitiesBeforeDeleteCascader<I, O> extends BeforeDeleteCollectionCascader<I, O> {
		
		private final Function<I, ? extends Collection<O>> collectionGetter;
		
		public DeleteTargetEntitiesBeforeDeleteCascader(Persister<O, ?, ?> targetPersister, Function<I, ? extends Collection<O>> collectionGetter) {
			super(targetPersister);
			this.collectionGetter = collectionGetter;
		}
		
		@Override
		protected void postTargetDelete(Iterable<O> entities) {
			// no post treatment to do
		}
		
		@Override
		protected Collection<O> getTargets(I i) {
			Collection<O> targets = collectionGetter.apply(i);
			// We only delete persisted instances (for logic and to prevent from non matching row count exception)
			return stream(targets)
					.filter(not(getPersister().getMappingStrategy()::isNew))
					.collect(Collectors.toList());
		}
	}
	
	static class DeleteByIdTargetEntitiesBeforeDeleteByIdCascader<I, O> extends BeforeDeleteByIdCollectionCascader<I, O> {
		
		private final Function<I, ? extends Collection<O>> collectionGetter;
		
		public DeleteByIdTargetEntitiesBeforeDeleteByIdCascader(Persister<O, ?, ?> targetPersister,
																Function<I, ? extends Collection<O>> collectionGetter) {
			super(targetPersister);
			this.collectionGetter = collectionGetter;
		}
		
		@Override
		protected void postTargetDelete(Iterable<O> entities) {
			// no post treatment to do
		}
		
		@Override
		protected Collection<O> getTargets(I i) {
			Collection<O> targets = collectionGetter.apply(i);
			// We only delete persisted instances (for logic and to prevent from non matching row count exception)
			return stream(targets)
					.filter(not(getPersister().getMappingStrategy()::isNew))
					.collect(Collectors.toList());
		}
	}
}
