package org.gama.stalactite.persistence.engine.cascade;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.gama.lang.Duo;
import org.gama.lang.collection.Iterables;
import org.gama.lang.function.Predicates;
import org.gama.stalactite.persistence.engine.listening.UpdateListener;

/**
 * @author Guillaume Mary
 */
public class BeforeUpdateSupport<TRIGGER, TARGET> implements UpdateListener<TRIGGER> {
	
	private final BiConsumer<Iterable<Duo<TARGET, TARGET>>, Boolean> beforeUpdateAction;
	
	private final Function<TRIGGER, TARGET> targetProvider;
	
	private final Predicate<Duo<TARGET, TARGET>> targetFilter;
	
	public BeforeUpdateSupport(BiConsumer<Iterable<Duo<TARGET, TARGET>>, Boolean> beforeUpdateAction, Function<TRIGGER, TARGET> targetProvider) {
		this(beforeUpdateAction, targetProvider, Predicates.acceptAll());
	}
	
	public BeforeUpdateSupport(BiConsumer<Iterable<Duo<TARGET, TARGET>>, Boolean> beforeUpdateAction, Function<TRIGGER, TARGET> targetProvider, Predicate<Duo<TARGET, TARGET>> targetFilter) {
		this.beforeUpdateAction = beforeUpdateAction;
		this.targetProvider = targetProvider;
		this.targetFilter = targetFilter;
	}
	
	@Override
	public void beforeUpdate(Iterable<? extends Duo<? extends TRIGGER, ? extends TRIGGER>> entities, boolean allColumnsStatement) {
		beforeUpdateAction.accept(Iterables.stream(entities)
						.map(e -> new Duo<>(targetProvider.apply(e.getLeft()), targetProvider.apply(e.getRight())))
						.filter(targetFilter).collect(Collectors.toList()),
				allColumnsStatement);
	}
}
