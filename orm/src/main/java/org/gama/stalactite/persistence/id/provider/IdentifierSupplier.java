package org.gama.stalactite.persistence.id.provider;

import java.util.function.Supplier;

/**
 * Implementation based on {@link Supplier}
 * 
 * @author Guillaume Mary
 */
public class IdentifierSupplier<T> implements IdentifierProvider<T> {
	
	private final Supplier<T> surrogateSupplier;
	
	public IdentifierSupplier(Supplier<T> surrogateSupplier) {
		this.surrogateSupplier = surrogateSupplier;
	}
	
	@Override
	public final T giveNewIdentifier() {
		return surrogateSupplier.get();
	}
}
