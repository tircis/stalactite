package org.gama.stalactite.persistence.engine.cascade;

import org.gama.stalactite.persistence.engine.BeanRelationFixer;
import org.gama.stalactite.persistence.engine.IConfiguredPersister;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * @author Guillaume Mary
 */
public interface IJoinedTablesPersister<C, I> {
	
	<U, J, Z> String addPersister(String ownerStrategyName, IConfiguredPersister<U, J> persister, BeanRelationFixer<Z, U> beanRelationFixer,
								  Column leftJoinColumn,
								  Column rightJoinColumn,
								  boolean isOuterJoin);
	
	/**
	 * Called to join this instance with given persister. For this method, current instance is considered as the "right part" of the relation.
	 * Made as such because polymorphic cases (which are instance of this interface) are the only one to know hom to join themselves with a caller.
	 * 
	 * @param sourcePersister source that needs this instance joins
	 * @param leftColumn left part of the join, expected to be one of source table 
	 * @param rightColumn right part of the join, expected to be one of current instance table
	 * @param beanRelationFixer setter that fix relation ofthis instance onto source persister instance
	 * @param nullable true for optional relation, makes an outer join, else should create a inner join
	 */
	<SRC> void joinAsOne(IJoinedTablesPersister<SRC, I> sourcePersister,
						 Column leftColumn, Column rightColumn, BeanRelationFixer<SRC, C> beanRelationFixer, boolean nullable);
	
	JoinedStrategiesSelect<C, I, ?> getJoinedStrategiesSelect();
	
	/**
	 * Copies current instance joins root to given select
	 * 
	 * @param joinedStrategiesSelect target of the copy
	 * @param joinName name of target select join on which joins of thisinstance must be copied
	 * @param <E> target select entity type
	 * @param <ID> identifier tyoe
	 * @param <T> table type
	 */
	<E, ID, T extends Table> void copyJoinsRootTo(JoinedStrategiesSelect<E, ID, T> joinedStrategiesSelect, String joinName);
	
}