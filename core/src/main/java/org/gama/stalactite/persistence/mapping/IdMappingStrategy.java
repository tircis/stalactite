package org.gama.stalactite.persistence.mapping;

import org.gama.reflection.IReversibleAccessor;
import org.gama.stalactite.persistence.id.manager.IdentifierInsertionManager;
import org.gama.stalactite.persistence.id.manager.StatefullIdentifier;

/**
 * Small entry point about entity identifier mapping. Will mainly delegate its work to an {@link IIdAccessor} and a {@link IdentifierInsertionManager}
 * 
 * @author Guillaume Mary
 */
public class IdMappingStrategy<T, I> {
	
	public static <T, I> IIdAccessor<T, I> toIdAccessor(IReversibleAccessor<T, I> propertyAccessor) {
		return new IIdAccessor<T, I>() {
			
			@Override
			public I getId(T t) {
				return propertyAccessor.get(t);
			}
			
			@Override
			public void setId(T t, I identifier) {
				propertyAccessor.toMutator().set(t, identifier);
			}
		};
	}
	
	private final IIdAccessor<T, I> idAccessor;
	
	private final IdentifierInsertionManager<T, I> identifierInsertionManager;
	
	private final IsNewDeterminer<T> isNewDeterminer;
	
	public IdMappingStrategy(IIdAccessor<T, I> idAccessor, IdentifierInsertionManager<T, I> identifierInsertionManager) {
		this.idAccessor = idAccessor;
		this.identifierInsertionManager = identifierInsertionManager;
		if (StatefullIdentifier.class.isAssignableFrom(identifierInsertionManager.getIdentifierType())) {
			this.isNewDeterminer = new StatefullIdDeterminer();
		} else if (identifierInsertionManager.getIdentifierType().isPrimitive()) {
			this.isNewDeterminer = new PrimitiveIdDeterminer();
		} else {
			this.isNewDeterminer = new NullableIdDeterminer();
		}
	}
	
	public IdMappingStrategy(IReversibleAccessor<T, I> identifierAccessor, IdentifierInsertionManager<T, I> identifierInsertionManager) {
		this(toIdAccessor(identifierAccessor), identifierInsertionManager);
	}
	
	public IdentifierInsertionManager<T, I> getIdentifierInsertionManager() {
		return identifierInsertionManager;
	}
	
	public I getId(T t) {
		return idAccessor.getId(t);
	}
	
	public void setId(T t, I identifier) {
		idAccessor.setId(t, identifier);
	}
	
	public boolean isNew(T t) {
		return isNewDeterminer.isNew(t);
	}
	
	/**
	 * Small contract to determine if an entity is persisted or not
	 * @param <T>
	 */
	@FunctionalInterface
	private interface IsNewDeterminer<T> {
		/**
		 * @param t an entity
		 * @return true if the entity doesn't exist in database
		 */
		boolean isNew(T t);
	}
	
	/**
	 * For case where the identifier is a basic type (String, Long, ...)
	 */
	private class NullableIdDeterminer implements IsNewDeterminer<T> {
		
		@Override
		public boolean isNew(T t) {
			return getId(t) == null;
		}
	}
	
	/**
	 * For case where the identifier is a primitive type (long, int, ...)
	 */
	private class PrimitiveIdDeterminer implements IsNewDeterminer<T> {
		
		@Override
		public boolean isNew(T t) {
			return ((Number) getId(t)).intValue() == 0;
		}
	}
	
	/**
	 * For case where the identifier is a {@link StatefullIdentifier}
	 */
	private class StatefullIdDeterminer implements IsNewDeterminer<T> {
		
		@Override
		public boolean isNew(T t) {
			return !((StatefullIdentifier) getId(t)).isPersisted();
		}
	}
}
