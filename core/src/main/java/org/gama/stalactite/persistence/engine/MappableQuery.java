package org.gama.stalactite.persistence.engine;

import java.sql.ResultSet;
import java.util.function.BiConsumer;

import org.danekja.java.util.function.serializable.SerializableBiConsumer;
import org.danekja.java.util.function.serializable.SerializableBiFunction;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.danekja.java.util.function.serializable.SerializableSupplier;
import org.gama.lang.function.Converter;
import org.gama.lang.function.SerializableTriFunction;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.sql.result.ResultSetRowAssembler;
import org.gama.stalactite.sql.result.ResultSetRowTransformer;
import org.gama.stalactite.sql.result.WholeResultSetTransformer.AssemblyPolicy;

/**
 * Contract to define mapping and execution of some SQL select
 * 
 * @author Guillaume Mary
 */
public interface MappableQuery<C> {
	
	/**
	 * Declares root bean constructor and key of {@link java.sql.ResultSet}. Column type will be deduced from given constructor.
	 *
	 * @param javaBeanCtor bean constructor that will take column value as parameter
	 * @param columnName column containing identifier value
	 * @param <I> identifier type
	 * @return an instance that allows method chaining
	 */
	<I> MappableQuery<C> mapKey(SerializableFunction<I, C> javaBeanCtor, String columnName);
	
	/**
	 * Declares root bean constructor and key of {@link java.sql.ResultSet}. Column type will be deduced from given constructor.
	 *
	 * @param javaBeanCtor bean constructor that will take column value as parameter
	 * @param columnName1 column containing first identifier value
	 * @param columnName2 column containing second identifier value
	 * @param <I> identifier type
	 * @return an instance that allows method chaining
	 */
	<I, J> MappableQuery<C> mapKey(SerializableBiFunction<I, J, C> javaBeanCtor, String columnName1, String columnName2);
	
	/**
	 * Declares root bean constructor and key of {@link java.sql.ResultSet}. Column type will be deduced from given constructor.
	 *
	 * @param javaBeanCtor bean constructor that will take column value as parameter
	 * @param columnName1 column containing first identifier value
	 * @param columnName2 column containing second identifier value
	 * @param columnName3 column containing third identifier value
	 * @param <I> identifier type
	 * @return an instance that allows method chaining
	 */
	<I, J, K> MappableQuery<C> mapKey(SerializableTriFunction<I, J, K, C> javaBeanCtor, String columnName1, String columnName2, String columnName3);
	
	/**
	 * Declares root bean constructor and key of {@link java.sql.ResultSet}.
	 * Doesn't have same name as other key definer due to conflict with {@link #mapKey(SerializableFunction, String, Class)} and entities that have
	 * both a no-arg constructor and a one-arg constructor.
	 *
	 * @param javaBeanCtor no-arg bean constructor
	 * @param columnName column containing identifier value
	 * @param columnType identifier type
	 * @param <I> identifier type
	 * @return an instance that allows method chaining
	 */
	<I> MappableQuery<C> mapKeyNoArg(SerializableSupplier<C> javaBeanCtor, String columnName, Class<I> columnType);
	
	/**
	 * Declares root bean constructor and key of {@link java.sql.ResultSet}.
	 * 
	 * @param javaBeanCtor bean constructor that will take column value as parameter
	 * @param columnName column containing identifier value
	 * @param columnType identifier type
	 * @param <I> identifier type
	 * @return an instance that allows method chaining
	 */
	<I> MappableQuery<C> mapKey(SerializableFunction<I, C> javaBeanCtor, String columnName, Class<I> columnType);
	
	/**
	 * Equivalent of {@link #mapKey(SerializableFunction, String, Class)} with a 2-args constructor
	 * Declares root bean constructor and a composed-key of {@link java.sql.ResultSet}
	 *
	 * @param javaBeanCtor bean constructor that will take column values as parameters
	 * @param column1Name column containing first identifier value
	 * @param column1Type first identifier type
	 * @param column2Name column containing second identifier value
	 * @param column2Type second identifier type
	 * @param <I> first identifier type
	 * @param <J> second identifier type
	 * @return an instance that allows method chaining
	 */
	<I, J> MappableQuery<C> mapKey(SerializableBiFunction<I, J, C> javaBeanCtor,
								   String column1Name, Class<I> column1Type,
								   String column2Name, Class<J> column2Type);
	
	/**
	 * Equivalent of {@link #mapKey(SerializableFunction, String, Class)} with a 3-args constructor
	 * Declares root bean constructor and a composed-key of {@link java.sql.ResultSet}
	 *
	 * @param javaBeanCtor bean constructor that will take column values as parameters
	 * @param column1Name column containing first identifier value
	 * @param column1Type first identifier type
	 * @param column2Name column containing second identifier value
	 * @param column2Type second identifier type
	 * @param column3Name column containing third identifier value
	 * @param column3Type third identifier type
	 * @param <I> first identifier type
	 * @param <J> second identifier type
	 * @param <K> third identifier type
	 * @return an instance that allows method chaining
	 */
	<I, J, K> MappableQuery<C> mapKey(SerializableTriFunction<I, J, K, C> javaBeanCtor,
									  String column1Name, Class<I> column1Type,
									  String column2Name, Class<J> column2Type,
									  String column3Name, Class<K> column3Type);
	
	/**
	 * Equivalent of {@link #mapKey(SerializableFunction, String, Class)} with a column argument.
	 * Declares root bean constructor and key of {@link java.sql.ResultSet}
	 *
	 * @param javaBeanCtor bean constructor that will take column values as parameters
	 * @param column column containing identifier value
	 * @param <I> first identifier type
	 * @return an instance that allows method chaining
	 */
	<I> MappableQuery<C> mapKey(SerializableFunction<I, C> javaBeanCtor, Column<? extends Table, I> column);
	
	/**
	 * Equivalent of {@link #mapKey(SerializableFunction, Column)} with 2 columns argument.
	 * Declares root bean constructor and a composed-key of {@link java.sql.ResultSet}
	 *
	 * @param javaBeanCtor bean constructor that will take column values as parameters
	 * @param column1 column containing first identifier value
	 * @param column2 column containing second identifier value
	 * @param <I> first identifier type
	 * @param <J> second identifier type
	 * @return an instance that allows method chaining
	 */
	<I, J> MappableQuery<C> mapKey(SerializableBiFunction<I, J, C> javaBeanCtor, Column<? extends Table, I> column1, Column<? extends Table, J> column2);
	
	/**
	 * Equivalent of {@link #mapKey(SerializableFunction, Column)} with 3 columns argument.
	 * Declares root bean constructor and a composed-key of {@link java.sql.ResultSet}
	 *
	 * @param javaBeanCtor bean constructor that will take column values as parameters
	 * @param column1 column containing first identifier value
	 * @param column2 column containing second identifier value
	 * @param column3 column containing third identifier value
	 * @param <I> first identifier type
	 * @param <J> second identifier type
	 * @param <K> thrid identifier type
	 * @return an instance that allows method chaining
	 */
	<I, J, K> MappableQuery<C> mapKey(SerializableTriFunction<I, J, K, C> javaBeanCtor,
									  Column<? extends Table, I> column1,
									  Column<? extends Table, J> column2,
									  Column<? extends Table, K> column3
	);
	
	/**
	 * Maps a column to a bean property
	 * 
	 * @param columnName column name that will fill the property
	 * @param setter property setter
	 * @param columnType column and value type
	 * @param <I> column and value type
	 * @return an instance that allows method chaining
	 * @see #map(String, SerializableBiConsumer, Converter) 
	 */
	<I> MappableQuery<C> map(String columnName, SerializableBiConsumer<C, I> setter, Class<I> columnType);
	
	/**
	 * Equivalent of {@link #map(String, SerializableBiConsumer, Class)} with an additional converter.
	 * Maps a column to a bean property by converting its value before setting it.
	 *
	 * @param columnName column name that will fill the property
	 * @param setter property setter
	 * @param columnType column and value type
	 * @param converter value converter
	 * @param <I> column and value type
	 * @return an instance that allows method chaining
	 * @see #map(String, SerializableBiConsumer, Converter)
	 */
	<I, J> MappableQuery<C> map(String columnName, SerializableBiConsumer<C, J> setter, Class<I> columnType, Converter<I, J> converter);
	
	/**
	 * Equivalent of {@link #map(String, SerializableBiConsumer, Class)} without ensuring column type argument : it will be deduced from setter.
	 * Prefer {@link #map(String, SerializableBiConsumer, Class)} to ensure value reading from {@link java.sql.ResultSet}
	 * 
	 * @param columnName column name that will fill the property
	 * @param setter property setter
	 * @param <I> column and value type
	 * @return an instance that allows method chaining
	 */
	<I> MappableQuery<C> map(String columnName, SerializableBiConsumer<C, I> setter);
	
	/**
	 * Equivalent of {@link #map(String, SerializableBiConsumer, Class, Converter)} without ensuring column type argument : it will be deduced from setter.
	 * Prefer {@link #map(String, SerializableBiConsumer, Class, Converter)} to ensure value reading from {@link java.sql.ResultSet}
	 *
	 * @param columnName column name that will fill the property
	 * @param setter property setter
	 * @param converter value converter
	 * @param <I> column and value type
	 * @return an instance that allows method chaining
	 */
	<I, J> MappableQuery<C> map(String columnName, SerializableBiConsumer<C, J> setter, Converter<I, J> converter);
	
	/**
	 * Equivalent of {@link #map(String, SerializableBiConsumer)} with column argument.
	 * 
	 * @param column column name that will fill the property
	 * @param setter property setter
	 * @param <I> column and value type
	 * @return an instance that allows method chaining
	 */
	<I> MappableQuery<C> map(Column<? extends Table, I> column, SerializableBiConsumer<C, I> setter);
	
	/**
	 * Equivalent of {@link #map(String, SerializableBiConsumer, Converter)} with column argument.
	 *
	 * @param column column name that will fill the property
	 * @param setter property setter
	 * @param <I> column and value type
	 * @return an instance that allows method chaining
	 */
	<I, J> MappableQuery<C> map(Column<? extends Table, I> column, SerializableBiConsumer<C, J> setter, Converter<I, J> converter);
	
	/**
	 * Associates beans created by this instance and the ones created by another converter with setter (represented as a {@link BiConsumer}).
	 * This allows to create bean graphs.
	 *
	 * @param combiner setter (on beans created by this instance) to fix beans created by given converter
	 * @param relatedBeanCreator creator of other beans from a {@link java.sql.ResultSet}
	 * @param <V> type of beans created by given converter
	 * @return this
	 */
	<K, V> MappableQuery<C> map(BiConsumer<C, V> combiner, ResultSetRowTransformer<K, V> relatedBeanCreator);
	
	/**
	 * Adds a low level {@link java.sql.ResultSet} transfomer, for cases where mapping methods are unsufficient.
	 * Assembly will occurs on each row ({@link ResultSetRowAssembler#assemble(Object, ResultSet)} will be call for each {@link ResultSet} row)
	 *
	 * @param assembler a low-level {@link java.sql.ResultSet} transformer
	 * @return this
	 */
	default MappableQuery<C> add(ResultSetRowAssembler<C> assembler) {
		return add(assembler, AssemblyPolicy.ON_EACH_ROW);
	}
	
	/**
	 * Adds a low level {@link java.sql.ResultSet} transfomer, for cases where mapping methods are unsufficient.
	 * Be aware that any bean created by given assembler won't participate in cache, if this is required then one should implement
	 * its own cache.
	 *
	 * @param assembler a generic combiner of a root bean and each {@link java.sql.ResultSet} row
	 * @param assemblyPolicy policy to decide if given assemble shall be invoked on each row or not
	 * @return this
	 */
	MappableQuery<C> add(ResultSetRowAssembler<C> assembler, AssemblyPolicy assemblyPolicy);
	
	/**
	 * Sets a value for the given parameter
	 * 
	 * @param paramName name of the parameter in the sql
	 * @param value value for the parameter
	 * @return this
	 */
	MappableQuery<C> set(String paramName, Object value);
	
	/**
	 * Sets a value for the given parameter giving explicit binder type
	 *
	 * @param paramName name of the parameter in the sql
	 * @param value value for the parameter
	 * @param valueType type for value {@link java.sql.PreparedStatement} binder
	 * @return this
	 */
	<O> MappableQuery<C> set(String paramName, O value, Class<? super O> valueType);
	
	/**
	 * Sets a value for the given parameter expected to be a multi-valued one (such as "in") giving values binder type.
	 *
	 * @param paramName name of the parameter in the sql
	 * @param value value for the parameter
	 * @param valueType type for values {@link java.sql.PreparedStatement} binder
	 * @return this
	 */
	<O> MappableQuery<C> set(String paramName, Iterable<O> value, Class<? super O> valueType);
}
