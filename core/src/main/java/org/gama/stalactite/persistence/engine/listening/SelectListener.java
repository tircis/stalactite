package org.gama.stalactite.persistence.engine.listening;

/**
 * @author Guillaume Mary
 */
public interface SelectListener<C, I> {
	
	default void beforeSelect(Iterable<I> ids) {
		
	}
	
	default void afterSelect(Iterable<? extends C> result) {
		
	}
	
	default void onError(Iterable<I> ids, RuntimeException exception) {
		
	}
	
}
