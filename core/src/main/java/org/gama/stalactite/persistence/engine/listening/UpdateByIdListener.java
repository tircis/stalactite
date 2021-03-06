package org.gama.stalactite.persistence.engine.listening;

/**
 * @author Guillaume Mary
 */
public interface UpdateByIdListener<T> {
	
	default void beforeUpdateById(Iterable<T> entities) {
		
	}
	
	default void afterUpdateById(Iterable<T> entities) {
		
	}
	
	default void onError(Iterable<T> entities, RuntimeException runtimeException) {
		
	}
}
