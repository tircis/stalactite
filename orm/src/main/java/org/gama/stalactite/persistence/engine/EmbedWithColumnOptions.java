package org.gama.stalactite.persistence.engine;

import org.danekja.java.util.function.serializable.SerializableBiConsumer;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * Adds the possibility to override a property throught its {@link Column} to an {@link EmbedOptions}.
 * Only available on entity configurations (those inheriting from {@link IFluentEntityMappingBuilder}), not on embeddable ones (those inherting from
 * {@link IFluentEmbeddableMappingBuilder}) because the latter can be reused on different tables, hence letting the possibilty to override a
 * {@link Column} doesn't make sense.
 * 
 * @author Guillaume Mary
 */
public interface EmbedWithColumnOptions<C> extends EmbedOptions<C> {
	
	/**
	 * Overrides embedding with an existing target column
	 * 
	 * @param function the getter as a method reference
	 * @param targetColumn a column that's the target of the getter
	 * @param <IN> input of the function (type of the embedded element)
	 * @return a mapping configurer, specialized for embedded elements
	 */
	<IN> EmbedWithColumnOptions<C> override(SerializableFunction<C, IN> function, Column<? extends Table, IN> targetColumn);
	
	@Override
	<IN> EmbedWithColumnOptions<C> overrideName(SerializableFunction<C, IN> getter, String columnName);
	
	@Override
	<IN> EmbedWithColumnOptions<C> overrideName(SerializableBiConsumer<C, IN> setter, String columnName);
	
	@Override
	<IN> EmbedWithColumnOptions<C> exclude(SerializableFunction<C, IN> getter);
	
	@Override
	<IN> EmbedWithColumnOptions<C> exclude(SerializableBiConsumer<C, IN> setter);
	
}