package org.gama.stalactite.persistence.engine;

import java.util.Collection;
import java.util.function.Supplier;

import org.danekja.java.util.function.serializable.SerializableBiConsumer;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * @author Guillaume Mary
 */
public interface OneToManyOptions<C, I, O, S extends Collection<O>>
	extends CascadeOptions<OneToManyOptions<C, I, O, S>> {
	
	/**
	 * Defines the bidirectional relationship.
	 * No need to additionally call {@link #mappedBy(SerializableFunction)} or {@link #mappedBy(Column)}.
	 * 
	 * If the relationship is already defined throught {@link #mappedBy(Column)} or {@link #mappedBy(SerializableFunction)} then there's no
	 * guaranty about which one will be taken first. Algorithm is defined in {@link CascadeManyConfigurer}.
	 * 
	 * @param reverseLink opposite owner of the relation (setter)
	 * @return the global mapping configurer
	 */
	OneToManyOptions<C, I, O, S> mappedBy(SerializableBiConsumer<O, C> reverseLink);
	
	/**
	 * Defines the bidirectional relationship.
	 * No need to additionally call {@link #mappedBy(SerializableBiConsumer)} or {@link #mappedBy(Column)}.
	 *
	 * If the relationship is already defined throught {@link #mappedBy(Column)} or {@link #mappedBy(SerializableBiConsumer)} then there's no
	 * guaranty about which one will be taken first. Algorithm is defined in {@link CascadeManyConfigurer}.
	 * 
	 * @param reverseLink opposite owner of the relation (getter)
	 * @return the global mapping configurer
	 */
	OneToManyOptions<C, I, O, S> mappedBy(SerializableFunction<O, C> reverseLink);
	
	/**
	 * Defines reverse side owner.
	 * Note that defining it this way will not allow relation to be fixed in memory (after select in database), prefer {@link #mappedBy(SerializableBiConsumer)}.
	 * Use this method to define unidirectional relationship.
	 *
	 * If the relationship is already defined throught {@link #mappedBy(SerializableFunction)} or {@link #mappedBy(SerializableBiConsumer)} then there's no
	 * guaranty about which one will be taken first. Algorithm is defined in {@link CascadeManyConfigurer}.
	 * 
	 * @param reverseLink opposite owner of the relation
	 * @return the global mapping configurer
	 */
	OneToManyOptions<C, I, O, S> mappedBy(Column<Table, ?> reverseLink);
	
	/**
	 * Defines setter of current entity on target entity, for bidirectionality fix in memory (it has no consequence on database mapping).
	 * Has only interest for mapping with association table because in such case {@link #mappedBy(SerializableFunction)} methods are not used hence
	 * reverse setter can't be deduced. If used with owned association it would have no consequence and is not taken into account.
	 * 
	 * @param reverseLink opposite owner of the relation
	 * @return the global mapping configurer
	 */
	OneToManyOptions<C, I, O, S> reverselySetBy(SerializableBiConsumer<O, C> reverseLink);
	
	/**
	 * Defines the collection factory to be used at load time to initialize property if it is null.
	 * Usefull for cases where property is lazily initialized in bean.
	 * 
	 * @param collectionFactory a collection factory
	 * @return the global mapping configurer
	 */
	OneToManyOptions<C, I, O, S> initializeWith(Supplier<S> collectionFactory);
	
}
