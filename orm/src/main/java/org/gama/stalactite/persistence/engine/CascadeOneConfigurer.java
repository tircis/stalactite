package org.gama.stalactite.persistence.engine;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Function;

import org.gama.lang.Duo;
import org.gama.lang.Reflections;
import org.gama.lang.collection.Iterables;
import org.gama.reflection.Accessors;
import org.gama.reflection.IMutator;
import org.gama.reflection.PropertyAccessor;
import org.gama.stalactite.persistence.engine.CascadeOptions.RelationshipMode;
import org.gama.stalactite.persistence.engine.cascade.AfterDeleteByIdCascader;
import org.gama.stalactite.persistence.engine.cascade.AfterDeleteCascader;
import org.gama.stalactite.persistence.engine.cascade.AfterUpdateCascader;
import org.gama.stalactite.persistence.engine.cascade.BeforeInsertCascader;
import org.gama.stalactite.persistence.engine.cascade.JoinedStrategiesSelect;
import org.gama.stalactite.persistence.engine.cascade.JoinedTablesPersister;
import org.gama.stalactite.persistence.engine.listening.InsertListener;
import org.gama.stalactite.persistence.engine.listening.PersisterListener;
import org.gama.stalactite.persistence.engine.listening.UpdateListener;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * @param <SRC> type of input (left/source entities)
 * @param <TRGT> type of output (right/target entities)
 * @param <TRGTID> identifier type of target entities
 * @author Guillaume Mary
 */
public class CascadeOneConfigurer<SRC, TRGT, TRGTID> {
	
	public <SRCID, T extends Table<T>> void appendCascade(
			CascadeOne<SRC, TRGT, TRGTID> cascadeOne,
			Persister<SRC, SRCID, T> srcPersister,
			ClassMappingStrategy<SRC, SRCID, T> mappingStrategy,
			JoinedTablesPersister<SRC, SRCID, T> joinedTablesPersister,
			ForeignKeyNamingStrategy foreignKeyNamingStrategy) {
		Persister<TRGT, TRGTID, Table> targetPersister = (Persister<TRGT, TRGTID, Table>) cascadeOne.getTargetPersister();
		
		// adding persistence flag setters on other side : this could be done by Persiter itself,
		// but we would loose the reason why it does it : the cascade functionnality
		targetPersister.getPersisterListener().addInsertListener(
				targetPersister.getMappingStrategy().getIdMappingStrategy().getIdentifierInsertionManager().getInsertListener());
		
		PropertyAccessor<SRC, TRGT> targetAccessor = Accessors.of(cascadeOne.getMember());
		// Finding joined columns:
		// - left one is given by current mapping strategy through the property accessor.
		// - Right one is target primary key because we don't yet support "not owner of the property"
		if (mappingStrategy.getTargetTable().getPrimaryKey().getColumns().size() > 1) {
			throw new NotYetSupportedOperationException("Joining tables on a composed primary key is not (yet) supported");
		}
		Column leftColumn = mappingStrategy.getMainMappingStrategy().getPropertyToColumn().get(targetAccessor);
		// According to the nullable option, we specify the ddl schema option
		leftColumn.nullable(cascadeOne.isNullable());
		Column rightColumn = Iterables.first((Set<Column<?, Object>>) targetPersister.getMainTable().getPrimaryKey().getColumns());
		
		// adding foerign key constraint
		leftColumn.getTable().addForeignKey(foreignKeyNamingStrategy.giveName(leftColumn, rightColumn), leftColumn, rightColumn);
		
		PersisterListener<SRC, SRCID> srcPersisterListener = srcPersister.getPersisterListener();
		RelationshipMode maintenanceMode = cascadeOne.getRelationshipMode();
		// selection is always present (else configuration is nonsense !)
		addSelectCascade(cascadeOne, joinedTablesPersister, targetPersister, targetAccessor, leftColumn, rightColumn);
		// additionnal cascade
		switch (maintenanceMode) {
			case ALL:
			case ALL_ORPHAN_REMOVAL:
				// NB: "delete removed" will be treated internally by updateCascade() and deleteCascade()
				addInsertCascade(cascadeOne, targetPersister, srcPersisterListener);
				addUpdateCascade(cascadeOne, targetPersister, srcPersisterListener);
				addDeleteCascade(cascadeOne, targetPersister, srcPersisterListener);
				break;
			case ASSOCIATION_ONLY:
				throw new MappingConfigurationException(RelationshipMode.ASSOCIATION_ONLY + " is only relevent for one-to-many association");
		}
	}
	
	public <SRCID, T extends Table<T>> void addSelectCascade(CascadeOne<SRC, TRGT, TRGTID> cascadeOne,
													  JoinedTablesPersister<SRC, SRCID, T> joinedTablesPersister,
													  Persister<TRGT, TRGTID, Table> targetPersister,
													  PropertyAccessor<SRC, TRGT> targetAccessor,
													  Column leftColumn,
													  Column rightColumn) {
		// configuring select for fetching relation
		IMutator<SRC, TRGT> targetSetter = targetAccessor.getMutator();
		joinedTablesPersister.addPersister(JoinedStrategiesSelect.FIRST_STRATEGY_NAME, targetPersister,
				BeanRelationFixer.of(targetSetter::set),
				leftColumn, rightColumn, cascadeOne.isNullable());
	}
	
	public void addDeleteCascade(CascadeOne<SRC, TRGT, TRGTID> cascadeOne,
								 Persister<TRGT, TRGTID, Table> targetPersister,
								 PersisterListener<SRC, ?> srcPersisterListener) {
		// adding cascade treatment: before delete target is deleted (done before because of foreign key constraint)
		srcPersisterListener.addDeleteListener(new AfterDeleteCascader<SRC, TRGT>(targetPersister) {
			
			@Override
			protected void postTargetDelete(Iterable<TRGT> entities) {
				// no post treatment to do
			}
			
			@Override
			protected TRGT getTarget(SRC src) {
				TRGT target = cascadeOne.getTargetProvider().apply(src);
				// We only delete persisted instances (for logic and to prevent from non matching row count error)
				return target != null && !targetPersister.getMappingStrategy().getIdMappingStrategy().isNew(target) ? target : null;
			}
		});
		// we add the deleteById event since we suppose that if delete is required then there's no reason that rough delete is not
		srcPersisterListener.addDeleteByIdListener(new AfterDeleteByIdCascader<SRC, TRGT>(targetPersister) {
			
			@Override
			protected void postTargetDelete(Iterable<TRGT> entities) {
				// no post treatment to do
			}
			
			@Override
			protected TRGT getTarget(SRC src) {
				TRGT target = cascadeOne.getTargetProvider().apply(src);
				// We only delete persisted instances (for logic and to prevent from non matching row count error)
				return target != null && !targetPersister.getMappingStrategy().getIdMappingStrategy().isNew(target) ? target : null;
			}
		});
	}
	
	public void addUpdateCascade(CascadeOne<SRC, TRGT, TRGTID> cascadeOne,
								 Persister<TRGT, TRGTID, Table> targetPersister,
								 PersisterListener<SRC, ?> srcPersisterListener) {
		// if cascade is mandatory, then adding nullability checking before insert
		if (!cascadeOne.isNullable()) {
			srcPersisterListener.addUpdateListener(
					new MandatoryRelationCheckingBeforeUpdateListener<>(cascadeOne.getMember(), cascadeOne.getTargetProvider()));
		}
		// adding cascade treatment: after update target is updated too
		srcPersisterListener.addUpdateListener(new AfterUpdateCascader<SRC, TRGT>(targetPersister) {
			
			@Override
			protected void postTargetUpdate(Iterable<UpdatePayload<? extends TRGT, ?>> entities) {
				// Nothing to do
			}
			
			@Override
			protected Duo<TRGT, TRGT> getTarget(SRC modifiedTrigger, SRC unmodifiedTrigger) {
				return new Duo<>(cascadeOne.getTargetProvider().apply(modifiedTrigger), cascadeOne.getTargetProvider().apply(unmodifiedTrigger));
			}
		});
	}
	
	public void addInsertCascade(CascadeOne<SRC, TRGT, TRGTID> cascadeOne,
								 Persister<TRGT, TRGTID, Table> targetPersister,
								 PersisterListener<SRC, ?> srcPersisterListener) {
		// if cascade is mandatory, then adding nullability checking before insert
		if (!cascadeOne.isNullable()) {
			srcPersisterListener.addInsertListener(
					new MandatoryRelationCheckingBeforeInsertListener<>(cascadeOne.getTargetProvider(), cascadeOne.getMember()));
		}
		// adding cascade treatment: after insert target is inserted too
		srcPersisterListener.addInsertListener(new BeforeInsertCascader<SRC, TRGT>(targetPersister) {
			
			@Override
			protected void postTargetInsert(Iterable<? extends TRGT> entities) {
				// Nothing to do. Identified#isPersisted flag should be fixed by target persister
			}
			
			@Override
			protected TRGT getTarget(SRC o) {
				TRGT target = cascadeOne.getTargetProvider().apply(o);
				// We only insert non-persisted instances (for logic and to prevent duplicate primary key error)
				return target != null && targetPersister.getMappingStrategy().getIdMappingStrategy().isNew(target) ? target : null;
			}
		});
	}
	
	public static class MandatoryRelationCheckingBeforeInsertListener<T> implements InsertListener<T> {
		
		private final Function<T, ?> targetProvider;
		private final Method member;
		
		public MandatoryRelationCheckingBeforeInsertListener(Function<T, ?> targetProvider, Method member) {
			this.targetProvider = targetProvider;
			this.member = member;
		}
		
		@Override
		public void beforeInsert(Iterable<? extends T> entities) {
			for (T pawn : entities) {
				Object modifiedTarget = targetProvider.apply(pawn);
				if (modifiedTarget == null) {
					throw newRuntimeMappingException(pawn, member);
				}
			}
		}
	}
	
	public static class MandatoryRelationCheckingBeforeUpdateListener<C> implements UpdateListener<C> {
		
		private final Method member;
		private final Function<C, ?> targetProvider;
		
		public MandatoryRelationCheckingBeforeUpdateListener(Method member, Function<C, ?> targetProvider) {
			this.member = member;
			this.targetProvider = targetProvider;
		}
		
		@Override
		public void beforeUpdate(Iterable<UpdatePayload<? extends C, ?>> payloads, boolean allColumnsStatement) {
			for (UpdatePayload<? extends C, ?> payload : payloads) {
				C modifiedEntity = payload.getEntities().getLeft();
				Object modifiedTarget = targetProvider.apply(modifiedEntity);
				if (modifiedTarget == null) {
					throw newRuntimeMappingException(modifiedEntity, member);
				}
			}
		}
	}
	
	public static RuntimeMappingException newRuntimeMappingException(Object pawn, Method member) {
		return new RuntimeMappingException("Non null value expected for relation "
				+ Reflections.toString(member) + " on object " + pawn);
	}
}
