package org.gama.stalactite.persistence.engine;

import org.gama.stalactite.persistence.engine.configurer.FluentEmbeddableMappingConfigurationSupport;
import org.gama.stalactite.persistence.engine.configurer.FluentEntityMappingConfigurationSupport;
import org.gama.stalactite.persistence.engine.configurer.FluentSubEntityMappingConfigurationSupport;
import org.gama.stalactite.persistence.engine.runtime.JoinedTablesPersister;
import org.gama.stalactite.persistence.mapping.EmbeddedBeanMappingStrategy;

/**
 * Declares a simple entry point to start configuring a persistence mapping.
 * 
 * @author Guillaume Mary
 */
public final class MappingEase {
	
	/**
	 * Starts a {@link IFluentEntityMappingBuilder} for a given class.
	 *
	 * @param classToPersist the class to be persisted by the {@link JoinedTablesPersister}
	 * 						 that will be created by {@link IFluentEntityMappingBuilder#build(PersistenceContext)}
	 * @param identifierType entity identifier type
	 * @param <T> any type to be persisted
	 * @param <I> the type of the identifier
	 * @return a new {@link IFluentEntityMappingBuilder}
	 */
	@SuppressWarnings("squid:S1172")	// identifierType is used to sign result
	public static <T, I> IFluentEntityMappingBuilder<T, I> entityBuilder(Class<T> classToPersist, Class<I> identifierType) {
		return new FluentEntityMappingConfigurationSupport<>(classToPersist);
	}
	
	public static <T, I> IFluentSubEntityMappingConfiguration<T, Object> subentityBuilder(Class<T> classToPersist) {
		return new FluentSubEntityMappingConfigurationSupport<>(classToPersist);
	}
	
	public static <T, I> IFluentSubEntityMappingConfiguration<T, I> subentityBuilder(Class<T> classToPersist, Class<I> identifierType) {
		return new FluentSubEntityMappingConfigurationSupport<>(classToPersist);
	}
	
	/**
	 * Starts a {@link IFluentEmbeddableMappingBuilder} for a given class.
	 *
	 * @param persistedClass the class to be persisted by the {@link EmbeddedBeanMappingStrategy}
	 * @param <T> any type to be persisted
	 * @return a new {@link IFluentEmbeddableMappingBuilder}
	 */
	public static <T> IFluentEmbeddableMappingBuilder<T> embeddableBuilder(Class<T> persistedClass) {
		return new FluentEmbeddableMappingConfigurationSupport<>(persistedClass);
	}
	
	private MappingEase() {
		// tool class
	}
}
