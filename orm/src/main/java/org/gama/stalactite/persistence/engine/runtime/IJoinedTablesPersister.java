package org.gama.stalactite.persistence.engine.runtime;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;

import org.danekja.java.util.function.serializable.SerializableBiConsumer;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.stalactite.persistence.engine.ExecutableQuery;
import org.gama.stalactite.persistence.engine.IEntityPersister.EntityCriteria;
import org.gama.stalactite.persistence.engine.IEntityPersister.ExecutableEntityQuery;
import org.gama.stalactite.persistence.engine.runtime.JoinedTablesPersister.CriteriaProvider;
import org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree;
import org.gama.stalactite.persistence.mapping.ColumnedRow;
import org.gama.stalactite.persistence.query.RelationalEntityCriteria;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.query.model.AbstractRelationalOperator;
import org.gama.stalactite.sql.result.Row;

/**
 * @author Guillaume Mary
 */
public interface IJoinedTablesPersister<C, I> {
	
	/**
	 * Called to join this instance with given persister. For this method, current instance is considered as the "right part" of the relation.
	 * Made as such because polymorphic cases (which are instance of this interface) are the only one who knows how to join themselves with another persister.
	 * 
	 * @param <SRC> source entity type
	 * @param <T1> left table type
	 * @param <T2> right table type
	 * @param <JID> join columns type, which can either be source persister identifier type (SRCID), or current instance identifier type (I)
	 * @param sourcePersister source that needs this instance joins
	 * @param leftColumn left part of the join, expected to be one of source table 
	 * @param rightColumn right part of the join, expected to be one of current instance table
	 * @param rightTableAlias optional alias for right table, if null table name will be used
	 * @param beanRelationFixer setter that fix relation of this instance onto source persister instance
	 * @param optional true for optional relation, makes an outer join, else should create a inner join
	 * @return the created join name, then it could be found in sourcePersister#getEntityJoinTree
	 */
	<SRC, T1 extends Table, T2 extends Table, SRCID, JID> String joinAsOne(IJoinedTablesPersister<SRC, SRCID> sourcePersister,
																		   Column<T1, JID> leftColumn,
																		   Column<T2, JID> rightColumn,
																		   String rightTableAlias,
																		   BeanRelationFixer<SRC, C> beanRelationFixer,
																		   boolean optional);
	
	/**
	 * Called to join this instance with given persister. For this method, current instance is considered as the "right part" of the relation.
	 * Made as such because polymorphic cases (which are instance of this interface) are the only one who knows how to join themselves with another persister.
	 * 
	 * @param <SRC> source entity type
	 * @param <T1> left table type
	 * @param <T2> right table type
	 * @param sourcePersister source that needs this instance joins
	 * @param leftColumn left part of the join, expected to be one of source table 
	 * @param rightColumn right part of the join, expected to be one of current instance table
	 * @param beanRelationFixer setter that fix relation of this instance onto source persister instance, expected to manage collection instanciation
	 * @param duplicateIdentifierProvider a function that computes the relation identifier
	 * @param joinName parent join node name on which join must be added,
	 * 					not always {@link EntityJoinTree#ROOT_STRATEGY_NAME} in particular in one-to-many with association table
	 * @param optional true for optional relation, makes an outer join, else should create a inner join
	 */
	default <SRC, T1 extends Table, T2 extends Table, SRCID> String joinAsMany(IJoinedTablesPersister<SRC, SRCID> sourcePersister,
																	 Column<T1, ?> leftColumn,
																	 Column<T2, ?> rightColumn,
																	 BeanRelationFixer<SRC, C> beanRelationFixer,
																	 @Nullable BiFunction<Row, ColumnedRow, ?> duplicateIdentifierProvider,
																	 String joinName,
																	 boolean optional) {
		return joinAsMany(sourcePersister, (Column) leftColumn, rightColumn, beanRelationFixer, duplicateIdentifierProvider, joinName, optional, Collections.emptySet());
	}
	
	/**
	 * Called to join this instance with given persister. For this method, current instance is considered as the "right part" of the relation.
	 * Made as such because polymorphic cases (which are instance of this interface) are the only one who knows how to join themselves with another persister.
	 *
	 * @param <SRC> source entity type
	 * @param <T1> left table type
	 * @param <T2> right table type
	 * @param sourcePersister source that needs this instance joins
	 * @param leftColumn left part of the join, expected to be one of source table 
	 * @param rightColumn right part of the join, expected to be one of current instance table
	 * @param beanRelationFixer setter that fix relation of this instance onto source persister instance, expected to manage collection instanciation
	 * @param duplicateIdentifierProvider a function that computes the relation identifier
	 * @param joinName parent join node name on which join must be added,
	 * 					not always {@link EntityJoinTree#ROOT_STRATEGY_NAME} in particular in one-to-many with association table
	 * @param optional true for optional relation, makes an outer join, else should create a inner join
	 * @param selectableColumns columns to be added to SQL select clause
	 */
	<SRC, T1 extends Table, T2 extends Table, SRCID, ID> String joinAsMany(IJoinedTablesPersister<SRC, SRCID> sourcePersister,
																	   Column<T1, ID> leftColumn,
																	   Column<T2, ID> rightColumn,
																	   BeanRelationFixer<SRC, C> beanRelationFixer,
																	   @Nullable BiFunction<Row, ColumnedRow, ?> duplicateIdentifierProvider,
																	   String joinName,
																	   boolean optional,
																	   Set<Column<T2, ?>> selectableColumns);
	
	EntityJoinTree<C, I> getEntityJoinTree();
	
	/**
	 * Copies current instance joins root to given select
	 * 
	 * @param entityJoinTree target of the copy
	 * @param joinName name of target select join on which joins of thisinstance must be copied
	 * @param <E> target select entity type
	 * @param <ID> identifier tyoe
	 */
	<E, ID> void copyRootJoinsTo(EntityJoinTree<E, ID> entityJoinTree, String joinName);
	
	/**
	 * Creates a query which criteria target mapped properties.
	 * <strong>As for now aggregate result is truncated to entities returned by SQL selection : for example, if criteria on collection is used,
	 * only entities returned by SQL criteria will be loaded. This does not respect aggregate principle and should be enhanced in future.</strong>
	 *
	 * @param <O> value type returned by property accessor
	 * @param getter a property accessor
	 * @param operator criteria for the property
	 * @return a {@link EntityCriteria} enhance to be executed through {@link ExecutableQuery#execute()}
	 */
	<O> RelationalExecutableEntityQuery<C> selectWhere(SerializableFunction<C, O> getter, AbstractRelationalOperator<O> operator);
	
	/**
	 * Mashup between {@link EntityCriteria} and {@link ExecutableQuery} to make an {@link EntityCriteria} executable
	 * @param <C> type of object returned by query execution
	 */
	interface RelationalExecutableEntityQuery<C> extends ExecutableEntityQuery<C>, CriteriaProvider, RelationalEntityCriteria<C> {
		
		<O> RelationalExecutableEntityQuery<C> and(SerializableFunction<C, O> getter, AbstractRelationalOperator<O> operator);
		
		<O> RelationalExecutableEntityQuery<C> and(SerializableBiConsumer<C, O> setter, AbstractRelationalOperator<O> operator);
		
		<O> RelationalExecutableEntityQuery<C> or(SerializableFunction<C, O> getter, AbstractRelationalOperator<O> operator);
		
		<O> RelationalExecutableEntityQuery<C> or(SerializableBiConsumer<C, O> setter, AbstractRelationalOperator<O> operator);
		
		<A, B> RelationalExecutableEntityQuery<C> and(SerializableFunction<C, A> getter1, SerializableFunction<A, B> getter2, AbstractRelationalOperator<B> operator);
		
		<S extends Collection<A>, A, B> RelationalExecutableEntityQuery<C> andMany(SerializableFunction<C, S> getter1, SerializableFunction<A, B> getter2, AbstractRelationalOperator<B> operator);
		
	}
}
