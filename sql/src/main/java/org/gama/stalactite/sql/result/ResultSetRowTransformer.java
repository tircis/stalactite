package org.gama.stalactite.sql.result;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.lang.bean.Factory;
import org.gama.stalactite.sql.binder.ResultSetReader;

import static org.gama.lang.Nullable.nullable;

/**
 * A class aimed at creating flat (no graph) beans from a {@link ResultSet} row.
 * Will read a main/root column that determines if a bean can be created from it (is null ?), then applies some more "bean filler" that are
 * defined with {@link ColumnConsumer}.
 * Instances of this class can be reused over multiple {@link ResultSet} (supposed to have same columns).
 * They can also be adapted to other {@link ResultSet}s that haven't the exact same column names by duplicating them with {@link #copyWithAliases(Function)}.
 * Moreover they can also be cloned to another type of bean which uses the same column names with {@link #copyFor(Class, Function)}.
 * 
 * @param <I> the type of the bean key (Input)
 * @param <C> the type of the bean
 *     
 * @author Guillaume Mary
 */
public class ResultSetRowTransformer<I, C> implements ResultSetTransformer<I, C>, ResultSetRowAssembler<C> {
	
	private final BeanFactory<I, C> beanFactory;
	
	private final Class<C> beanType;
	
	private final Set<ColumnConsumer<C, Object>> consumers = new HashSet<>();
	
	private final Map<BiConsumer<C, Object>, ResultSetRowTransformer<Object, Object>> relations = new HashMap<>();
	
	/**
	 * Constructor focused on simple cases where beans are built only from one column key.
	 * Prefer {@link #ResultSetRowTransformer(Class, ColumnReader, SerializableFunction)} for more general purpose cases (multiple columns key)
	 * 
	 * @param columnName the name of the column that contains bean key
	 * @param reader object to ease column reading, indicates column type
	 * @param beanFactory the bean creator, bean key will be passed as argument. Not called if bean key is null (no instanciation needed)
	 */
	public ResultSetRowTransformer(Class<C> beanType, String columnName, ResultSetReader<I> reader, SerializableFunction<I, C> beanFactory) {
		this.beanType = beanType;
		this.beanFactory = new BeanFactory<>(new SingleColumnReader<>(columnName, reader), beanFactory);
	}
	
	/**
	 * Constructor with main and mandatory arguments
	 *
	 * @param beanType type of built instances
	 * @param reader object to ease column reading, indicates column type
	 * @param beanFactory the bean creator, bean key will be passed as argument. Not called if bean key is null (no instanciation needed)
	 */
	public ResultSetRowTransformer(Class<C> beanType, ColumnReader<I> reader, SerializableFunction<I, C> beanFactory) {
		this.beanType = beanType;
		this.beanFactory = new BeanFactory<>(reader, beanFactory);
	}
	
	public ResultSetRowTransformer(Class<C> beanType, BeanFactory<I, C> beanFactory) {
		this.beanType = beanType;
		this.beanFactory = beanFactory;
	}
	
	public Class<C> getBeanType() {
		return beanType;
	}
	
	public BeanFactory<I, C> getBeanFactory() {
		return beanFactory;
	}
	
	/**
	 * Gives {@link ColumnConsumer}s of this instances
	 * 
	 * @return not null
	 */
	public Set<ColumnConsumer<C, Object>> getConsumers() {
		return consumers;
	}
	
	public Map<BiConsumer<C, Object>, ResultSetRowTransformer<Object, Object>> getRelations() {
		return relations;
	}
	
	/**
	 * Defines a complementary column that will be mapped on a bean property.
	 * Null values will be passed to the consumer, hence the property mapper must be "null-value proof".
	 * 
	 * @param columnConsumer the object that will do reading and mapping
	 * @return this
	 */
	@Override
	public <O> ResultSetRowTransformer<I, C> add(ColumnConsumer<C, O> columnConsumer) {
		this.consumers.add((ColumnConsumer<C, Object>) columnConsumer);
		return this;
	}
	
	@Override
	public <K, V> ResultSetRowTransformer<I, C> add(BiConsumer<C, V> combiner, ResultSetRowTransformer<K, V> relatedBeanCreator) {
		this.relations.put((BiConsumer) combiner, (ResultSetRowTransformer) relatedBeanCreator);
		return this;
	}
	
	@Override
	public <T extends C> ResultSetRowTransformer<I, T> copyFor(Class<T> beanType, SerializableFunction<I, T> beanFactory) {
		ResultSetRowTransformer<I, T> result = new ResultSetRowTransformer<>(beanType, this.beanFactory.copyFor(beanFactory));
		result.consumers.addAll((Set) this.consumers);
		this.relations.forEach((consumer, transformer) ->
				result.relations.put((BiConsumer) consumer, transformer.copyFor(transformer.beanType, transformer.beanFactory.factory)));
		return result;
	}
	
	@Override
	public ResultSetRowTransformer<I, C> copyWithAliases(Function<String, String> columnMapping) {
		ResultSetRowTransformer<I, C> result = new ResultSetRowTransformer<>(this.beanType, this.beanFactory.copyWithAliases(columnMapping));
		this.consumers.forEach(c -> result.add(c.copyWithAliases(columnMapping)));
		this.relations.forEach((consumer, transformer) -> result.add(consumer, transformer.copyWithAliases(columnMapping)));
		return result;
	}
	
	@Override	// for adhoc return type
	public ResultSetRowTransformer<I, C> copyWithAliases(Map<String, String> columnMapping) {
		// equivalent to super.copyWithAlias(..) but because inteface default method can't be invoked we have to copy/paste its code ;(
		return copyWithAliases(columnMapping::get);
	}
	
	/**
	 * Converts the current {@link ResultSet} row into a bean.
	 * Depending on implementation of factory, it may return a brand new instance or a cached one (if the bean key is already known for instance).
	 * Consumers will be applied to instance returned by the factory, as a consequence if bean comes from a cache it will be completed again, this may
	 * do some extra work in case of simple property.
	 *
	 * @param resultSet not null
	 * @return an instance of T, newly created or not according to implementation
	 */
	@Override
	public C transform(ResultSet resultSet) {
		return nullable(this.beanFactory.createInstance(resultSet))
				.invoke(b -> assemble(b, resultSet))
				.get();
	}
	
	/**
	 * Implementation that applies all {@link ColumnConsumer} to the given {@link ResultSet}
	 *
	 * @param rootBean the bean built for the row
	 * @param input any {@link ResultSet} positioned at row that must be read
	 */
	@Override
	public void assemble(C rootBean, ResultSet input) {
		// we consume simple properties
		for (ColumnConsumer<C, ?> consumer : consumers) {
			consumer.assemble(rootBean, input);
		}
		
		// we set related beans
		for (Entry<BiConsumer<C, Object>, ResultSetRowTransformer<Object, Object>> entry : relations.entrySet()) {
			Object relatedBean = entry.getValue().transform(input);
			entry.getKey().accept(rootBean, relatedBean);
		}
	}
	
	/**
	 * An abstraction of a one-arg constructor that can instanciate a bean from a {@link ResultSet}
	 * 
	 * @param <I> constructor input type (also column type)
	 * @param <C> bean type
	 */
	public static class BeanFactory<I, C> implements Factory<ResultSet, C>, CopiableForAnotherQuery<C> {
		
		/** {@link ResultSet} reader */
		private final ColumnReader<I> reader;
		
		/** one-arg bean constructor */
		private final SerializableFunction<I, C> factory;
		
		
		public BeanFactory(ColumnReader<I> reader, SerializableFunction<I, C> factory) {
			this.reader = reader;
			this.factory = factory;
		}
		
		public SerializableFunction<I, C> getFactory() {
			return factory;
		}
		
		@Override
		public C createInstance(ResultSet resultSet) {
			return nullable(readBeanKey(resultSet)).map(this::newInstance).get();
		}
		
		protected I readBeanKey(ResultSet resultSet) {
			return reader.read(resultSet);
		}
		
		protected C newInstance(I beanKey) {
			return factory.apply(beanKey);
		}
		
		@Override
		public BeanFactory<I, C> copyWithAliases(Function<String, String> columnMapping) {
			return new BeanFactory<>(reader.copyWithAliases(columnMapping), this.factory);
		}
		
		/**
		 * Used to copy this instance for a subclass of its bean type, required for inheritance implementation.
		 * @param beanFactory subclass one-arg constructor
		 * @param <T> subclass type
		 * @return a new {@link BeanFactory} for a subclass bean which read same column as this instance
		 */
		public <T extends C> BeanFactory<I, T> copyFor(SerializableFunction<I, T> beanFactory) {
			return new BeanFactory<>(this.reader, beanFactory);
		}
	}
}
