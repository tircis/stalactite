package org.gama.stalactite.persistence.engine.cascade;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.gama.lang.collection.Iterables;
import org.gama.lang.function.Predicates;
import org.gama.stalactite.persistence.engine.listening.DeleteListener;

/**
 * @author Guillaume Mary
 */
public class AfterDeleteSupport<TRIGGER, TARGET> implements DeleteListener<TRIGGER> {
	
	private final Consumer<Iterable<TARGET>> afterDeleteAction;
	
	private final Function<TRIGGER, TARGET> targetProvider;
	
	private final Predicate<TARGET> targetFilter;
	
	public AfterDeleteSupport(Consumer<Iterable<TARGET>> afterDeleteAction, Function<TRIGGER, TARGET> targetProvider) {
		this(afterDeleteAction, targetProvider, Predicates.acceptAll());
	}
	
	public AfterDeleteSupport(Consumer<Iterable<TARGET>> afterDeleteAction, Function<TRIGGER, TARGET> targetProvider, Predicate<TARGET> targetFilter) {
		this.afterDeleteAction = afterDeleteAction;
		this.targetProvider = targetProvider;
		this.targetFilter = targetFilter;
	}

	@Override
	public void afterDelete(Iterable<TRIGGER> entities) {
		afterDeleteAction.accept(Iterables.stream(entities).map(targetProvider).filter(targetFilter).collect(Collectors.toList()));
	}
}
