package org.gama.stalactite.persistence.engine;

import org.danekja.java.util.function.serializable.SerializableBiConsumer;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.stalactite.persistence.engine.configurer.CascadeManyConfigurer;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * @author Guillaume Mary
 */
public interface OneToOneOptions<C, I, T extends Table> extends CascadeOptions {
	
	/** Marks the relation as mandatory. Hence joins will be inner ones and a checking for non null value will be done before insert and update */
	OneToOneOptions<C, I, T> mandatory();
	
	/**
	 * Defines the bidirectional relationship.
	 * No need to additionally call {@link #mappedBy(SerializableFunction)} or {@link #mappedBy(Column)}.
	 *
	 * If the relationship is already defined throught {@link #mappedBy(Column)} or {@link #mappedBy(SerializableFunction)} then there's no
	 * guaranty about which one will be taken first. Algorithm is defined in {@link CascadeManyConfigurer}.
	 *
	 * @param reverseLink opposite owner of the relation (setter)
	 * @param <O> owner type
	 * @return the global mapping configurer
	 */
	<O> OneToOneOptions<C, I, T> mappedBy(SerializableBiConsumer<O, C> reverseLink);
	
	/**
	 * Defines the bidirectional relationship.
	 * No need to additionally call {@link #mappedBy(SerializableBiConsumer)} or {@link #mappedBy(Column)}.
	 *
	 * If the relationship is already defined throught {@link #mappedBy(Column)} or {@link #mappedBy(SerializableBiConsumer)} then there's no
	 * guaranty about which one will be taken first. Algorithm is defined in {@link CascadeManyConfigurer}.
	 *
	 * @param reverseLink opposite owner of the relation (getter)
	 * @param <O> owner type
	 * @return the global mapping configurer
	 */
	<O> OneToOneOptions<C, I, T> mappedBy(SerializableFunction<O, C> reverseLink);
	
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
	OneToOneOptions<C, I, T> mappedBy(Column<T, I> reverseLink);
}
