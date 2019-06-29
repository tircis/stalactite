package org.gama.stalactite.persistence.engine.builder;

import javax.annotation.Nullable;
import java.util.Collection;

import org.danekja.java.util.function.serializable.SerializableBiConsumer;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.reflection.IReversibleAccessor;
import org.gama.reflection.ValueAccessPointByMethodReference;
import org.gama.stalactite.persistence.engine.CascadeOptions.RelationshipMode;
import org.gama.stalactite.persistence.engine.EntityMappingConfiguration;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * 
 * @param <SRC> the "one" type
 * @param <TRGT> the "many" type
 * @param <TRGTID> identifier type of TRGT
 * @param <C> the "many" collection type
 */
public class CascadeMany<SRC, TRGT, TRGTID, C extends Collection<TRGT>> {
	
	/** The method that gives the "many" entities from the "one" entity */
	private final IReversibleAccessor<SRC, C> collectionProvider;
	
	private final ValueAccessPointByMethodReference methodReference;
	/** Configuration used for "many" side beans persistence */
	private final EntityMappingConfiguration<TRGT, TRGTID> targetMappingConfiguration;
	
	private final Table targetTable;
	
	/** The method that gets the "one" entity from the "many" entities, may be null */
	private SerializableFunction<TRGT, SRC> reverseGetter;
	
	/** The method that sets the "one" entity onto the "many" entities, may be null */
	private SerializableBiConsumer<TRGT, SRC> reverseSetter;
	
	/**
	 * The column that stores relation, may be null.
	 * Its type is undetermined (not forced at SRC) because it can only be a reference, such as an id.
	 */
	private Column<Table, ?> reverseColumn;
	
	/** Default relationship mode is readonly */
	private RelationshipMode relationshipMode = RelationshipMode.READ_ONLY;
	
	public <T extends Table> CascadeMany(IReversibleAccessor<SRC, C> collectionProvider, ValueAccessPointByMethodReference methodReference, EntityMappingConfiguration<TRGT, TRGTID> targetMappingConfiguration, T targetTable) {
		this.collectionProvider = collectionProvider;
		this.methodReference = methodReference;
		this.targetMappingConfiguration = targetMappingConfiguration;
		this.targetTable = targetTable;
	}
	
	public IReversibleAccessor<SRC, C> getCollectionProvider() {
		return collectionProvider;
	}
	
	public ValueAccessPointByMethodReference getMethodReference() {
		return methodReference;
	}
	
	/** @return the configuration used for "many" side beans persistence */
	public EntityMappingConfiguration<TRGT, TRGTID> getTargetMappingConfiguration() {
		return targetMappingConfiguration;
	}
	
	@Nullable
	public Table getTargetTable() {
		return targetTable;
	}
	
	@Nullable
	public SerializableFunction<TRGT, SRC> getReverseGetter() {
		return reverseGetter;
	}
	
	public void setReverseGetter(SerializableFunction<TRGT, SRC> reverseGetter) {
		this.reverseGetter = reverseGetter;
	}
	
	@Nullable
	public SerializableBiConsumer<TRGT, SRC> getReverseSetter() {
		return reverseSetter;
	}
	
	public void setReverseSetter(SerializableBiConsumer<TRGT, SRC> reverseSetter) {
		this.reverseSetter = reverseSetter;
	}
	
	@Nullable
	public Column<Table, ?> getReverseColumn() {
		return reverseColumn;
	}
	
	public void setReverseColumn(Column<Table, ?> reverseColumn) {
		this.reverseColumn = reverseColumn;
	}
	
	public RelationshipMode getRelationshipMode() {
		return relationshipMode;
	}
	
	public void setRelationshipMode(RelationshipMode relationshipMode) {
		this.relationshipMode = relationshipMode;
	}
	
	/**
	 * Indicates if relation is owned by target entities table
	 * @return true if one of {@link #getReverseSetter()}, {@link #getReverseGetter()}, {@link #getReverseColumn()} is not null
	 */
	public boolean isOwnedByReverseSide() {
		return getReverseSetter() != null || getReverseGetter() != null || getReverseColumn() != null;
	}
	
}
