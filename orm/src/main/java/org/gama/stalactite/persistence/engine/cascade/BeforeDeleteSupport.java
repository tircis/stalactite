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
public class BeforeDeleteSupport<TRIGGER, TARGET> implements DeleteListener<TRIGGER> {
	
	private final Consumer<Iterable<TARGET>> beforeDeleteAction;
	
	private final Function<TRIGGER, TARGET> targetProvider;
	
	private final Predicate<TARGET> targetFilter;
	
	public BeforeDeleteSupport(Consumer<Iterable<TARGET>> beforeDeleteAction, Function<TRIGGER, TARGET> targetProvider) {
		this(beforeDeleteAction, targetProvider, Predicates.acceptAll());
	}
	
	public BeforeDeleteSupport(Consumer<Iterable<TARGET>> beforeDeleteAction, Function<TRIGGER, TARGET> targetProvider, Predicate<TARGET> targetFilter) {
		this.beforeDeleteAction = beforeDeleteAction;
		this.targetProvider = targetProvider;
		this.targetFilter = targetFilter;
	}

	@Override
	public void beforeDelete(Iterable<TRIGGER> entities) {
		beforeDeleteAction.accept(Iterables.stream(entities).map(targetProvider).filter(targetFilter).collect(Collectors.toList()));
	}
}
