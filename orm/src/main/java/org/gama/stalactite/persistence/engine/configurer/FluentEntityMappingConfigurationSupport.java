package org.gama.stalactite.persistence.engine.configurer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.danekja.java.util.function.serializable.SerializableBiConsumer;
import org.danekja.java.util.function.serializable.SerializableBiFunction;
import org.danekja.java.util.function.serializable.SerializableFunction;
import org.gama.lang.Nullable;
import org.gama.lang.Reflections;
import org.gama.lang.exception.NotImplementedException;
import org.gama.lang.function.SerializableTriFunction;
import org.gama.lang.function.Serie;
import org.gama.lang.function.TriFunction;
import org.gama.lang.reflect.MethodDispatcher;
import org.gama.reflection.AccessorByMethod;
import org.gama.reflection.AccessorByMethodReference;
import org.gama.reflection.AccessorDefinition;
import org.gama.reflection.Accessors;
import org.gama.reflection.IReversibleAccessor;
import org.gama.reflection.MethodReferenceCapturer;
import org.gama.reflection.MethodReferenceDispatcher;
import org.gama.reflection.MutatorByMethod;
import org.gama.reflection.MutatorByMethodReference;
import org.gama.reflection.PropertyAccessor;
import org.gama.reflection.ValueAccessPointByMethodReference;
import org.gama.stalactite.persistence.engine.AssociationTableNamingStrategy;
import org.gama.stalactite.persistence.engine.ColumnNamingStrategy;
import org.gama.stalactite.persistence.engine.ColumnOptions;
import org.gama.stalactite.persistence.engine.ColumnOptions.IdentifierPolicy;
import org.gama.stalactite.persistence.engine.ElementCollectionOptions;
import org.gama.stalactite.persistence.engine.ElementCollectionTableNamingStrategy;
import org.gama.stalactite.persistence.engine.EmbeddableMappingConfiguration;
import org.gama.stalactite.persistence.engine.EmbeddableMappingConfigurationProvider;
import org.gama.stalactite.persistence.engine.EntityMappingConfiguration;
import org.gama.stalactite.persistence.engine.EntityMappingConfigurationProvider;
import org.gama.stalactite.persistence.engine.EnumOptions;
import org.gama.stalactite.persistence.engine.ForeignKeyNamingStrategy;
import org.gama.stalactite.persistence.engine.IFluentEmbeddableMappingBuilder.IFluentEmbeddableMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions;
import org.gama.stalactite.persistence.engine.IFluentEmbeddableMappingBuilder.IFluentEmbeddableMappingBuilderEnumOptions;
import org.gama.stalactite.persistence.engine.IFluentEntityMappingBuilder;
import org.gama.stalactite.persistence.engine.ImportedEmbedWithColumnOptions;
import org.gama.stalactite.persistence.engine.IndexableCollectionOptions;
import org.gama.stalactite.persistence.engine.InheritanceOptions;
import org.gama.stalactite.persistence.engine.OneToManyOptions;
import org.gama.stalactite.persistence.engine.OneToOneOptions;
import org.gama.stalactite.persistence.engine.PersistenceContext;
import org.gama.stalactite.persistence.engine.PolymorphismPolicy;
import org.gama.stalactite.persistence.engine.TableNamingStrategy;
import org.gama.stalactite.persistence.engine.VersioningStrategy;
import org.gama.stalactite.persistence.engine.configurer.FluentEmbeddableMappingConfigurationSupport.AbstractLinkage;
import org.gama.stalactite.persistence.engine.runtime.AbstractVersioningStrategy.VersioningStrategySupport;
import org.gama.stalactite.persistence.engine.runtime.IEntityConfiguredPersister;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

import static org.gama.lang.Reflections.propertyName;

/**
 * A class that stores configuration made throught a {@link IFluentEntityMappingBuilder}
 * 
 * @author Guillaume Mary
 */
public class FluentEntityMappingConfigurationSupport<C, I> implements IFluentEntityMappingBuilder<C, I>, EntityMappingConfiguration<C, I> {
	
	private final Class<C> persistedClass;
	
	private IdentifierPolicy identifierPolicy;
	
	private TableNamingStrategy tableNamingStrategy = TableNamingStrategy.DEFAULT;
	
	private IReversibleAccessor<C, I> identifierAccessor;
	
	private final MethodReferenceCapturer methodSpy;
	
	private final List<CascadeOne<C, Object, Object>> cascadeOnes = new ArrayList<>();
	
	private final List<CascadeMany<C, ?, ?, ? extends Collection>> cascadeManys = new ArrayList<>();
	
	private final List<ElementCollectionLinkage<C, ?, ? extends Collection>> elementCollections = new ArrayList<>();
	
	private final EntityDecoratedEmbeddableConfigurationSupport<C, I> propertiesMappingConfigurationSurrogate;
	
	private ForeignKeyNamingStrategy foreignKeyNamingStrategy = ForeignKeyNamingStrategy.DEFAULT;
	
	private ColumnNamingStrategy joinColumnNamingStrategy = ColumnNamingStrategy.JOIN_DEFAULT;
	
	private ColumnNamingStrategy indexColumnNamingStrategy;
	
	private AssociationTableNamingStrategy associationTableNamingStrategy = AssociationTableNamingStrategy.DEFAULT;
	
	private ElementCollectionTableNamingStrategy elementCollectionTableNamingStrategy = ElementCollectionTableNamingStrategy.DEFAULT;
	
	private OptimisticLockOption optimisticLockOption;
	
	private InheritanceConfigurationSupport<? super C, I> inheritanceConfiguration;
	
	private PolymorphismPolicy<C> polymorphismPolicy;
	
	private Function<Function<Column, Object>, C> entityFactory;
	
	/**
	 * Creates a builder to map the given class for persistence
	 *
	 * @param persistedClass the class to create a mapping for
	 */
	public FluentEntityMappingConfigurationSupport(Class<C> persistedClass) {
		this.persistedClass = persistedClass;
		
		// Helper to capture Method behind method reference
		this.methodSpy = new MethodReferenceCapturer();
		
		this.propertiesMappingConfigurationSurrogate = new EntityDecoratedEmbeddableConfigurationSupport<>(this, persistedClass);
	}
	
	@Override
	public Class<C> getEntityType() {
		return persistedClass;
	}
	
	@Override
	public Function<Function<Column, Object>, C> getEntityFactory() {
		return this.entityFactory;
	}
	
	@Override
	public TableNamingStrategy getTableNamingStrategy() {
		return tableNamingStrategy;
	}
	
	@Override
	public ColumnNamingStrategy getJoinColumnNamingStrategy() {
		return joinColumnNamingStrategy;
	}
	
	@Override
	public ColumnNamingStrategy getIndexColumnNamingStrategy() {
		return indexColumnNamingStrategy;
	}
	
	private Method captureMethod(SerializableFunction getter) {
		return this.methodSpy.findMethod(getter);
	}
	
	private Method captureMethod(SerializableBiConsumer setter) {
		return this.methodSpy.findMethod(setter);
	}
	
	@Override
	public IdentifierPolicy getIdentifierPolicy() {
		return identifierPolicy;
	}
	
	@Override
	public IReversibleAccessor<C, I> getIdentifierAccessor() {
		return this.identifierAccessor;
	}
	
	@Override
	public EmbeddableMappingConfiguration<C> getPropertiesMapping() {
		return propertiesMappingConfigurationSurrogate;
	}
	
	@Override
	public VersioningStrategy getOptimisticLockOption() {
		return Nullable.nullable(this.optimisticLockOption).map(OptimisticLockOption::getVersioningStrategy).get();
	}
	
	@Override
	public <TRGT, TRGTID> List<CascadeOne<C, TRGT, TRGTID>> getOneToOnes() {
		return (List) cascadeOnes;
	}
	
	@Override
	public <TRGT, TRGTID> List<CascadeMany<C, TRGT, TRGTID, ? extends Collection<TRGT>>> getOneToManys() {
		return (List) cascadeManys;
	}
	
	@Override
	public List<ElementCollectionLinkage<C, ?, ? extends Collection>> getElementCollections() {
		return elementCollections;
	}
	
	@Override
	public InheritanceConfiguration<? super C, I> getInheritanceConfiguration() {
		return inheritanceConfiguration;
	}
	
	@Override
	public ForeignKeyNamingStrategy getForeignKeyNamingStrategy() {
		return this.foreignKeyNamingStrategy;
	}
	
	@Override
	public AssociationTableNamingStrategy getAssociationTableNamingStrategy() {
		return this.associationTableNamingStrategy;
	}
	
	@Override
	public ElementCollectionTableNamingStrategy getElementCollectionTableNamingStrategy() {
		return this.elementCollectionTableNamingStrategy;
	}
	
	@Override
	public EntityMappingConfiguration<C, I> getConfiguration() {
		return this;
	}
	
	@Override
	public PolymorphismPolicy<C> getPolymorphismPolicy() {
		return polymorphismPolicy;
	}
	
	@Override
	public <O> IFluentMappingBuilderPropertyOptions<C, I> add(SerializableBiConsumer<C, O> setter) {
		return add(setter, (String) null);
	}
	
	@Override
	public <O> IFluentMappingBuilderPropertyOptions<C, I> add(SerializableFunction<C, O> getter) {
		return add(getter, (String) null);
	}
	
	@Override
	public <O> IFluentMappingBuilderPropertyOptions<C, I> add(SerializableBiConsumer<C, O> setter, String columnName) {
		AbstractLinkage<C> mapping = propertiesMappingConfigurationSurrogate.addMapping(setter, columnName);
		return this.propertiesMappingConfigurationSurrogate.wrapForAdditionalOptions(mapping);
	}
	
	@Override
	public <O> IFluentMappingBuilderPropertyOptions<C, I> add(SerializableFunction<C, O> getter, String columnName) {
		AbstractLinkage<C> mapping = propertiesMappingConfigurationSurrogate.addMapping(getter, columnName);
		return this.propertiesMappingConfigurationSurrogate.wrapForAdditionalOptions(mapping);
	}
	
	@Override
	public <O> IFluentMappingBuilderPropertyOptions<C, I> add(SerializableBiConsumer<C, O> setter, Column<? extends Table, O> column) {
		AbstractLinkage<C> mapping = propertiesMappingConfigurationSurrogate.addMapping(setter, column);
		return this.propertiesMappingConfigurationSurrogate.wrapForAdditionalOptions(mapping);
	}
	
	@Override
	public <O> IFluentMappingBuilderPropertyOptions<C, I> add(SerializableFunction<C, O> getter, Column<? extends Table, O> column) {
		AbstractLinkage<C> mapping = propertiesMappingConfigurationSurrogate.addMapping(getter, column);
		return this.propertiesMappingConfigurationSurrogate.wrapForAdditionalOptions(mapping);
	}
	
	@Override
	public <E extends Enum<E>> IFluentMappingBuilderEnumOptions<C, I> addEnum(SerializableBiConsumer<C, E> setter) {
		return addEnum(setter, (String) null);
	}
	
	@Override
	public <E extends Enum<E>> IFluentMappingBuilderEnumOptions<C, I> addEnum(SerializableFunction<C, E> getter) {
		return addEnum(getter, (String) null);
	}
	
	@Override
	public <E extends Enum<E>> IFluentMappingBuilderEnumOptions<C, I> addEnum(SerializableBiConsumer<C, E> setter, @javax.annotation.Nullable String columnName) {
		AbstractLinkage<C> linkage = propertiesMappingConfigurationSurrogate.addMapping(setter, columnName);
		return handleEnumOptions(propertiesMappingConfigurationSurrogate.addEnumOptions(linkage));
	}
	
	@Override
	public <E extends Enum<E>> IFluentMappingBuilderEnumOptions<C, I> addEnum(SerializableFunction<C, E> getter, @javax.annotation.Nullable String columnName) {
		AbstractLinkage<C> linkage = propertiesMappingConfigurationSurrogate.addMapping(getter, columnName);
		return handleEnumOptions(propertiesMappingConfigurationSurrogate.addEnumOptions(linkage));
	}
	
	@Override
	public <E extends Enum<E>> IFluentMappingBuilderEnumOptions<C, I> addEnum(SerializableBiConsumer<C, E> setter, Column<? extends Table, E> column) {
		AbstractLinkage<C> linkage = propertiesMappingConfigurationSurrogate.addMapping(setter, column);
		return handleEnumOptions(propertiesMappingConfigurationSurrogate.addEnumOptions(linkage));
	}
	
	@Override
	public <E extends Enum<E>> IFluentMappingBuilderEnumOptions<C, I> addEnum(SerializableFunction<C, E> getter, Column<? extends Table, E> column) {
		AbstractLinkage<C> linkage = propertiesMappingConfigurationSurrogate.addMapping(getter, column);
		return handleEnumOptions(propertiesMappingConfigurationSurrogate.addEnumOptions(linkage));
	}
	
	private IFluentMappingBuilderEnumOptions<C, I> handleEnumOptions(IFluentEmbeddableMappingBuilderEnumOptions<C> enumOptionsHandler) {
		// we redirect all of the EnumOptions method to the instance that can handle them, returning the dispatcher on this methods so one can chain
		// with some other methods, other methods are redirected to this instance because it can handle them.
		return new MethodDispatcher()
				.redirect(EnumOptions.class, enumOptionsHandler, true)
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderEnumOptions<C, I>>) (Class) IFluentMappingBuilderEnumOptions.class);
	}
	
	@Override
	public <O, S extends Collection<O>> IFluentMappingBuilderElementCollectionOptions<C, I, O, S> addCollection(SerializableFunction<C, S> getter,
																										  Class<O> componentType) {
		ElementCollectionLinkage<C, O, S> elementCollectionLinkage = new ElementCollectionLinkage<>(getter, componentType,
				propertiesMappingConfigurationSurrogate, null);
		elementCollections.add(elementCollectionLinkage);
		return new MethodReferenceDispatcher()
				.redirect((SerializableBiFunction<IFluentMappingBuilderElementCollectionOptions, String, IFluentMappingBuilderElementCollectionOptions>)
								IFluentMappingBuilderElementCollectionOptions::override,
						elementCollectionLinkage::overrideColumnName)
				.redirect(ElementCollectionOptions.class, wrapAsOptions(elementCollectionLinkage), true)
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderElementCollectionOptions<C, I, O, S>>) (Class) IFluentMappingBuilderElementCollectionOptions.class);
	}
	
	@Override
	public <O, S extends Collection<O>> IFluentMappingBuilderElementCollectionOptions<C, I, O, S> addCollection(SerializableBiConsumer<C, S> setter,
																										  Class<O> componentType) {
		ElementCollectionLinkage<C, O, S> elementCollectionLinkage = new ElementCollectionLinkage<>(setter, componentType, null);
		elementCollections.add(elementCollectionLinkage);
		return new MethodReferenceDispatcher()
				.redirect((SerializableBiFunction<IFluentMappingBuilderElementCollectionOptions, String, IFluentMappingBuilderElementCollectionOptions>)
								IFluentMappingBuilderElementCollectionOptions::override,
						elementCollectionLinkage::overrideColumnName)
				.redirect(ElementCollectionOptions.class, wrapAsOptions(elementCollectionLinkage), true)
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderElementCollectionOptions<C, I, O, S>>) (Class) IFluentMappingBuilderElementCollectionOptions.class);
	}
	
	@Override
	public <O, S extends Collection<O>> IFluentMappingBuilderElementCollectionImportEmbedOptions<C, I, O, S> addCollection(SerializableFunction<C, S> getter,
																												Class<O> componentType,
																												EmbeddableMappingConfigurationProvider<O> embeddableConfiguration) {
		ElementCollectionLinkage<C, O, S> elementCollectionLinkage = new ElementCollectionLinkage<>(getter, componentType,
				propertiesMappingConfigurationSurrogate,
				embeddableConfiguration);
		elementCollections.add(elementCollectionLinkage);
		return new MethodReferenceDispatcher()
				.redirect((SerializableTriFunction<IFluentMappingBuilderElementCollectionImportEmbedOptions, SerializableFunction, String, IFluentMappingBuilderElementCollectionImportEmbedOptions>)
								IFluentMappingBuilderElementCollectionImportEmbedOptions::overrideName,
						(BiConsumer<SerializableFunction, String>) elementCollectionLinkage::overrideName)
				.redirect(ElementCollectionOptions.class, wrapAsOptions(elementCollectionLinkage), true)
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderElementCollectionImportEmbedOptions<C, I, O, S>>) (Class) IFluentMappingBuilderElementCollectionImportEmbedOptions.class);
	}
	
	@Override
	public <O, S extends Collection<O>> IFluentMappingBuilderElementCollectionImportEmbedOptions<C, I, O, S> addCollection(SerializableBiConsumer<C, S> setter,
																												Class<O> componentType,
																												EmbeddableMappingConfigurationProvider<O> embeddableConfiguration) {
		ElementCollectionLinkage<C, O, S> elementCollectionLinkage = new ElementCollectionLinkage<>(setter, componentType, embeddableConfiguration);
		elementCollections.add(elementCollectionLinkage);
		return new MethodReferenceDispatcher()
				.redirect((SerializableTriFunction<IFluentMappingBuilderElementCollectionImportEmbedOptions, SerializableFunction, String, IFluentMappingBuilderElementCollectionImportEmbedOptions>)
								IFluentMappingBuilderElementCollectionImportEmbedOptions::overrideName,
						(BiConsumer<SerializableFunction, String>) elementCollectionLinkage::overrideName)
				.redirect(ElementCollectionOptions.class, wrapAsOptions(elementCollectionLinkage), true)
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderElementCollectionImportEmbedOptions<C, I, O, S>>) (Class) IFluentMappingBuilderElementCollectionImportEmbedOptions.class);
	}
	
	private <O, S extends Collection<O>> ElementCollectionOptions<C, O, S> wrapAsOptions(ElementCollectionLinkage<C, O, S> elementCollectionLinkage) {
		return new ElementCollectionOptions<C, O, S>() {
			
			@Override
			public ElementCollectionOptions<C, O, S> withCollectionFactory(Supplier<? extends S> collectionFactory) {
				elementCollectionLinkage.setCollectionFactory(collectionFactory);
				return null;
			}
			
			@Override
			public IFluentMappingBuilderElementCollectionOptions<C, I, O, S> mappedBy(String name) {
				elementCollectionLinkage.setReverseColumnName(name);
				return null;
			}
			
			@Override
			public ElementCollectionOptions<C, O, S> withTable(Table table) {
				elementCollectionLinkage.setTargetTable(table);
				return null;
			}
			
			@Override
			public ElementCollectionOptions<C, O, S> withTable(String tableName) {
				elementCollectionLinkage.setTargetTableName(tableName);
				return null;
			}
		};
	}
	
	@Override
	public IFluentMappingBuilderInheritanceOptions<C, I> mapInheritance(EntityMappingConfiguration<? super C, I> mappingConfiguration) {
		inheritanceConfiguration = new InheritanceConfigurationSupport<>(mappingConfiguration);
		return new MethodReferenceDispatcher()
				.redirect((SerializableFunction<InheritanceOptions, InheritanceOptions>) InheritanceOptions::withJoinedTable,
						() -> this.inheritanceConfiguration.joinTable = true)
				.redirect((SerializableBiFunction<InheritanceOptions, Table, InheritanceOptions>) InheritanceOptions::withJoinedTable,
						t -> { this.inheritanceConfiguration.joinTable = true; this.inheritanceConfiguration.table = t;})
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderInheritanceOptions<C, I>>) (Class) IFluentMappingBuilderInheritanceOptions.class);
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> mapSuperClass(EmbeddableMappingConfigurationProvider<? super C> superMappingConfiguration) {
		this.propertiesMappingConfigurationSurrogate.mapSuperClass(superMappingConfiguration);
		return this;
	}
	
	@Override
	public <O, J, T extends Table> IFluentMappingBuilderOneToOneOptions<C, I, T> addOneToOne(
			SerializableFunction<C, O> getter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration) {
		return addOneToOne(getter, mappingConfiguration, null);
	}
	
	@Override
	public <O, J, T extends Table> IFluentMappingBuilderOneToOneOptions<C, I, T> addOneToOne(
			SerializableBiConsumer<C, O> setter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration) {
		return addOneToOne(setter, mappingConfiguration, null);
	}
	
	@Override
	public <O, J, T extends Table> IFluentMappingBuilderOneToOneOptions<C, I, T> addOneToOne(
			SerializableBiConsumer<C, O> setter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration,
			T table) {
		MutatorByMethodReference<C, O> mutatorByMethodReference = Accessors.mutatorByMethodReference(setter);
		PropertyAccessor<C, O> propertyAccessor = new PropertyAccessor<>(
				// we keep close to user demand : we keep its method reference ...
				new MutatorByMethod<C, O>(captureMethod(setter)).toAccessor(),
				// ... but we can't do it for mutator, so we use the most equivalent manner : a mutator based on setter method (fallback to property if not present)
				mutatorByMethodReference);
		CascadeOne<C, O, J> cascadeOne = new CascadeOne<>(propertyAccessor, mappingConfiguration.getConfiguration(), table);
		this.cascadeOnes.add((CascadeOne<C, Object, Object>) cascadeOne);
		return wrapForAdditionalOptions(cascadeOne);
	}
	
	@Override
	public <O, J, T extends Table> IFluentMappingBuilderOneToOneOptions<C, I, T> addOneToOne(
			SerializableFunction<C, O> getter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration,
			T table) {
		AccessorByMethodReference<C, O> accessorByMethodReference = Accessors.accessorByMethodReference(getter);
		PropertyAccessor<C, O> propertyAccessor = new PropertyAccessor<>(
				// we keep close to user demand : we keep its method reference ...
				accessorByMethodReference,
				// ... but we can't do it for mutator, so we use the most equivalent manner : a mutator based on setter method (fallback to property if not present)
				new AccessorByMethod<C, O>(captureMethod(getter)).toMutator());
		CascadeOne<C, O, J> cascadeOne = new CascadeOne<>(propertyAccessor, mappingConfiguration, table);
		this.cascadeOnes.add((CascadeOne<C, Object, Object>) cascadeOne);
		return wrapForAdditionalOptions(cascadeOne);
	}
	
	private <O, J, T extends Table> IFluentMappingBuilderOneToOneOptions<C, I, T> wrapForAdditionalOptions(final CascadeOne<C, O, J> cascadeOne) {
		// then we return an object that allows fluent settings over our OneToOne cascade instance
		return new MethodDispatcher()
				.redirect(OneToOneOptions.class, new OneToOneOptions() {
					@Override
					public OneToOneOptions cascading(RelationMode relationMode) {
						cascadeOne.setRelationMode(relationMode);
						return null;	// we can return null because dispatcher will return proxy
					}
					
					@Override
					public OneToOneOptions mandatory() {
						cascadeOne.setNullable(false);
						return null;	// we can return null because dispatcher will return proxy
					}
					
					@Override
					public OneToOneOptions mappedBy(SerializableFunction reverseLink) {
						cascadeOne.setReverseGetter(reverseLink);
						return null;	// we can return null because dispatcher will return proxy
					}
					
					@Override
					public OneToOneOptions mappedBy(SerializableBiConsumer reverseLink) {
						cascadeOne.setReverseSetter(reverseLink);
						return null;	// we can return null because dispatcher will return proxy
					}
					
					@Override
					public OneToOneOptions mappedBy(Column reverseLink) {
						cascadeOne.setReverseColumn(reverseLink);
						return null;	// we can return null because dispatcher will return proxy
					}
				}, true)	// true to allow "return null" in implemented methods
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderOneToOneOptions<C, I, T>>) (Class) IFluentMappingBuilderOneToOneOptions.class);
	}
	
	@Override
	public <O, J, S extends Set<O>> IFluentMappingBuilderOneToManyOptions<C, I, O, S> addOneToManySet(
			SerializableFunction<C, S> getter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration) {
		return addOneToManySet(getter, mappingConfiguration, null);
	}
		
	@Override
	public <O, J, S extends Set<O>, T extends Table> IFluentMappingBuilderOneToManyOptions<C, I, O, S> addOneToManySet(
			SerializableFunction<C, S> getter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration,
			@javax.annotation.Nullable T table) {
		
		AccessorByMethodReference<C, S> getterReference = Accessors.accessorByMethodReference(getter);
		IReversibleAccessor<C, S> propertyAccessor = new PropertyAccessor<>(
				// we keep close to user demand : we keep its method reference ...
				getterReference,
				// ... but we can't do it for mutator, so we use the most equivalent manner : a mutator based on setter method (fallback to property if not present)
				new AccessorByMethod<C, S>(captureMethod(getter)).toMutator());
		return addOneToManySet(propertyAccessor, getterReference, mappingConfiguration, table);
	}
	
	@Override
	public <O, J, S extends Set<O>, T extends Table> IFluentMappingBuilderOneToManyOptions<C, I, O, S> addOneToManySet(
			SerializableBiConsumer<C, S> setter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration,
			@javax.annotation.Nullable T table) {
		
		MutatorByMethodReference<C, S> setterReference = Accessors.mutatorByMethodReference(setter);
		PropertyAccessor<C, S> propertyAccessor = new PropertyAccessor<>(
				Accessors.accessor(setterReference.getDeclaringClass(), propertyName(setterReference.getMethodName())),
				setterReference
		);
		return addOneToManySet(propertyAccessor, setterReference, mappingConfiguration, table);
	}
	
	private <O, J, S extends Set<O>, T extends Table> IFluentMappingBuilderOneToManyOptions<C, I, O, S> addOneToManySet(
			IReversibleAccessor<C, S> propertyAccessor,
			ValueAccessPointByMethodReference methodReference,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration,
			@javax.annotation.Nullable T table) {
		CascadeMany<C, O, J, S> cascadeMany = new CascadeMany<>(propertyAccessor, methodReference, mappingConfiguration, table);
		this.cascadeManys.add(cascadeMany);
		return new MethodDispatcher()
				.redirect(OneToManyOptions.class, new OneToManyOptionsSupport<>(cascadeMany), true)	// true to allow "return null" in implemented methods
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderOneToManyOptions<C, I, O, S>>) (Class) IFluentMappingBuilderOneToManyOptions.class);
	}
	
	@Override
	public <O, J, S extends List<O>> IFluentMappingBuilderOneToManyListOptions<C, I, O, S> addOneToManyList(
			SerializableFunction<C, S> getter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration) {
		return addOneToManyList(getter, mappingConfiguration, null);
	}
		
	@Override
	public <O, J, S extends List<O>, T extends Table> IFluentMappingBuilderOneToManyListOptions<C, I, O, S> addOneToManyList(
			SerializableFunction<C, S> getter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration,
			@javax.annotation.Nullable T table) {
		
		AccessorByMethodReference<C, S> getterReference = Accessors.accessorByMethodReference(getter);
		IReversibleAccessor<C, S> propertyAccessor = new PropertyAccessor<>(
				// we keep close to user demand : we keep its method reference ...
				getterReference,
				// ... but we can't do it for mutator, so we use the most equivalent manner : a mutator based on setter method (fallback to property if not present)
				new AccessorByMethod<C, S>(captureMethod(getter)).toMutator());
		return addOneToManyList(propertyAccessor, getterReference, mappingConfiguration, table);
	}
	
	@Override
	public <O, J, S extends List<O>, T extends Table> IFluentMappingBuilderOneToManyListOptions<C, I, O, S> addOneToManyList(
			SerializableBiConsumer<C, S> setter,
			EntityMappingConfigurationProvider<O, J> mappingConfiguration,
			@javax.annotation.Nullable T table) {
		
		MutatorByMethodReference<C, S> setterReference = Accessors.mutatorByMethodReference(setter);
		PropertyAccessor<C, S> propertyAccessor = new PropertyAccessor<>(
				Accessors.accessor(setterReference.getDeclaringClass(), propertyName(setterReference.getMethodName())),
				setterReference
		);
		return addOneToManyList(propertyAccessor, setterReference, mappingConfiguration, table);
	}
	
	private <O, J, S extends List<O>, T extends Table> IFluentMappingBuilderOneToManyListOptions<C, I, O, S> addOneToManyList(
			IReversibleAccessor<C, S> propertyAccessor,
			ValueAccessPointByMethodReference methodReference,
			EntityMappingConfigurationProvider<? extends O, J> mappingConfiguration,
			@javax.annotation.Nullable T table) {
		CascadeManyList<C, O, J, ? extends List<O>> cascadeMany = new CascadeManyList<>(propertyAccessor, methodReference, mappingConfiguration.getConfiguration(), table);
		this.cascadeManys.add(cascadeMany);
		return new MethodDispatcher()
				.redirect(OneToManyOptions.class, new OneToManyOptionsSupport<>(cascadeMany), true)	// true to allow "return null" in implemented methods
				.redirect(IndexableCollectionOptions.class, orderingColumn -> {
					cascadeMany.setIndexingColumn(orderingColumn);
					return null;
				}, true)	// true to allow "return null" in implemented methods
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderOneToManyListOptions<C, I, O, S>>) (Class) IFluentMappingBuilderOneToManyListOptions.class);
	}
	
	@Override
	public <O> IFluentMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions<C, I, O> embed(SerializableFunction<C, O> getter,
																									  EmbeddableMappingConfigurationProvider<? extends O> embeddableMappingBuilder) {
		return embed(propertiesMappingConfigurationSurrogate.embed(getter, embeddableMappingBuilder));
	}
	
	@Override
	public <O> IFluentMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions<C, I, O> embed(SerializableBiConsumer<C, O> setter,
																									  EmbeddableMappingConfigurationProvider<? extends O> embeddableMappingBuilder) {
		return embed(propertiesMappingConfigurationSurrogate.embed(setter, embeddableMappingBuilder));
	}
	
	private <O> IFluentMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions<C, I, O> embed(IFluentEmbeddableMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions<C, O> support) {
		return new MethodDispatcher()
				.redirect(ImportedEmbedWithColumnOptions.class, new ImportedEmbedWithColumnOptions() {
					@Override
					public ImportedEmbedWithColumnOptions overrideName(SerializableBiConsumer setter, String columnName) {
						support.overrideName(setter, columnName);
						return null;	// we can return null because dispatcher will return proxy
					}
					
					@Override
					public ImportedEmbedWithColumnOptions overrideName(SerializableFunction getter, String columnName) {
						support.overrideName(getter, columnName);
						return null;	// we can return null because dispatcher will return proxy
					}
					
					@Override
					public ImportedEmbedWithColumnOptions override(SerializableBiConsumer setter, Column targetColumn) {
						propertiesMappingConfigurationSurrogate.currentInset().override(setter, targetColumn);
						return null;
					}
					
					@Override
					public ImportedEmbedWithColumnOptions override(SerializableFunction getter, Column targetColumn) {
						propertiesMappingConfigurationSurrogate.currentInset().override(getter, targetColumn);
						return null;
					}
					
					@Override
					public ImportedEmbedWithColumnOptions exclude(SerializableBiConsumer setter) {
						support.exclude(setter);
						return null;	// we can return null because dispatcher will return proxy
					}
					
					@Override
					public ImportedEmbedWithColumnOptions exclude(SerializableFunction getter) {
						support.exclude(getter);
						return null;	// we can return null because dispatcher will return proxy
					}
				}, true)
				.fallbackOn(this)
				.build((Class<IFluentMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions<C, I, O>>) (Class) IFluentMappingBuilderEmbeddableMappingConfigurationImportedEmbedOptions.class);
	}
	
	@Override
	public <X> IFluentEntityMappingBuilder<C, I> useConstructor(Function<X, C> factory, Column<? extends Table, X> input) {
		this.entityFactory = row -> factory.apply((X) row.apply(input));
		return this;
	}
	
	@Override
	public <X, Y> IFluentEntityMappingBuilder<C, I> useConstructor(BiFunction<X, Y, C> factory,
																   Column<? extends Table, X> input1,
																   Column<? extends Table, Y> input2) {
		this.entityFactory = row -> factory.apply((X) row.apply(input1), (Y) row.apply(input2));
		return this;
	}
	
	@Override
	public <X, Y, Z> IFluentEntityMappingBuilder<C, I> useConstructor(TriFunction<X, Y, Z, C> factory,
																	  Column<? extends Table, X> input1,
																	  Column<? extends Table, Y> input2,
																	  Column<? extends Table, Z> input3) {
		this.entityFactory = row -> factory.apply((X) row.apply(input1), (Y) row.apply(input2), (Z) row.apply(input3));
		return this;
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> useConstructor(Function<? extends Function<? extends Column, ? extends Object>, C> factory) {
		this.entityFactory = (SerializableFunction<Function<Column, Object>, C>) factory;
		return this;
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> withElementCollectionTableNaming(ElementCollectionTableNamingStrategy tableNamingStrategy) {
		this.elementCollectionTableNamingStrategy = tableNamingStrategy;
		return this;
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> withForeignKeyNaming(ForeignKeyNamingStrategy foreignKeyNamingStrategy) {
		this.foreignKeyNamingStrategy = foreignKeyNamingStrategy;
		return this;
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> withColumnNaming(ColumnNamingStrategy columnNamingStrategy) {
		this.propertiesMappingConfigurationSurrogate.withColumnNaming(columnNamingStrategy);
		return this;
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> withJoinColumnNaming(ColumnNamingStrategy columnNamingStrategy) {
		this.joinColumnNamingStrategy = columnNamingStrategy;
		return this;
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> withIndexColumnNamingStrategy(ColumnNamingStrategy columnNamingStrategy) {
		this.indexColumnNamingStrategy = columnNamingStrategy;
		return this;
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> withAssociationTableNaming(AssociationTableNamingStrategy associationTableNamingStrategy) {
		this.associationTableNamingStrategy = associationTableNamingStrategy;
		return this;
	}
	
	/**
	 * Defines the versioning property of beans. This implies that Optmistic Locking will be applied on those beans.
	 * Versioning policy is supported for following types:
	 * <ul>
	 * <li>{@link Integer} : a "+1" policy will be applied, see {@link Serie#INTEGER_SERIE}</li>
	 * <li>{@link Long} : a "+1" policy will be applied, see {@link Serie#LONG_SERIE}</li>
	 * <li>{@link Date} : a "now" policy will be applied, see {@link Serie#NOW_SERIE}</li>
	 * </ul>
	 * 
	 * @param getter the funciton that gives access to the versioning property
	 * @param <V> type of the versioning property, determines versioning policy
	 * @return this
	 * @see #versionedBy(SerializableFunction, Serie)
	 */
	@Override
	public <V> IFluentEntityMappingBuilder<C, I> versionedBy(SerializableFunction<C, V> getter) {
		AccessorByMethodReference methodReference = Accessors.accessorByMethodReference(getter);
		Serie<V> serie;
		if (Integer.class.isAssignableFrom(methodReference.getPropertyType())) {
			serie = (Serie<V>) Serie.INTEGER_SERIE;
		} else if (Long.class.isAssignableFrom(methodReference.getPropertyType())) {
			serie = (Serie<V>) Serie.LONG_SERIE;
		} else if (Date.class.isAssignableFrom(methodReference.getPropertyType())) {
			serie = (Serie<V>) Serie.NOW_SERIE;
		} else {
			throw new NotImplementedException("Type of versioned property is not implemented, please provide a "
					+ Serie.class.getSimpleName() + " for it : " + Reflections.toString(methodReference.getPropertyType()));
		}
		return versionedBy(getter, methodReference, serie);
	}
	
	@Override
	public <V> IFluentEntityMappingBuilder<C, I> versionedBy(SerializableFunction<C, V> getter, Serie<V> serie) {
		return versionedBy(getter, new AccessorByMethodReference<>(getter), serie);
	}
	
	private <V> IFluentEntityMappingBuilder<C, I> versionedBy(SerializableFunction<C, V> getter, AccessorByMethodReference methodReference, Serie<V> serie) {
		optimisticLockOption = new OptimisticLockOption<>(methodReference, serie);
		add(getter);
		return this;
	}
	
	@Override
	public IFluentEntityMappingBuilder<C, I> mapPolymorphism(PolymorphismPolicy<C> polymorphismPolicy) {
		this.polymorphismPolicy = polymorphismPolicy;
		return this;
	}
	
	@Override
	public IEntityConfiguredPersister<C, I> build(PersistenceContext persistenceContext) {
		return build(persistenceContext, null);
	}
	
	@Override
	public IEntityConfiguredPersister<C, I> build(PersistenceContext persistenceContext, @javax.annotation.Nullable Table targetTable) {
		return new PersisterBuilderImpl<>(this.getConfiguration()).build(persistenceContext, targetTable);
	}
	
	/**
	 * Class very close to {@link FluentEmbeddableMappingConfigurationSupport}, but with dedicated methods to entity mapping such as
	 * identifier definition or configuration override by {@link Column}
	 */
	static class EntityDecoratedEmbeddableConfigurationSupport<C, I> extends FluentEmbeddableMappingConfigurationSupport<C> {
		
		private final FluentEntityMappingConfigurationSupport<C, I> entityConfigurationSupport;
		
		/**
		 * Creates a builder to map the given class for persistence
		 *
		 * @param persistedClass the class to create a mapping for
		 */
		public EntityDecoratedEmbeddableConfigurationSupport(FluentEntityMappingConfigurationSupport<C, I> entityConfigurationSupport, Class<C> persistedClass) {
			super(persistedClass);
			this.entityConfigurationSupport = entityConfigurationSupport;
		}
		
		@Override
		protected <O> EntityLinkageByColumnName<C> newLinkage(IReversibleAccessor<C, O> accessor, Class<O> returnType, String linkName) {
			return new EntityLinkageByColumnName<>(accessor, returnType, linkName);
		}
		
		<E> AbstractLinkage<C> addMapping(SerializableBiConsumer<C, E> setter, Column column) {
			return addMapping(Accessors.mutator(setter), column);
		}
		
		<E> AbstractLinkage<C> addMapping(SerializableFunction<C, E> getter, Column column) {
			return addMapping(Accessors.accessor(getter), column);
		}
		
		/**
		 * Equivalent of {@link #addMapping(IReversibleAccessor, AccessorDefinition, String)} with a {@link Column}
		 * 
		 * @return a new Column added to the target table, throws an exception if already mapped
		 */
		AbstractLinkage<C> addMapping(IReversibleAccessor<C, ?> propertyAccessor, Column column) {
			EntityLinkageByColumn<C> newLinkage = new EntityLinkageByColumn<>(propertyAccessor, column);
			mapping.add(newLinkage);
			return newLinkage;
		}
		
		private IFluentMappingBuilderPropertyOptions<C, I> wrapForAdditionalOptions(AbstractLinkage<C> newMapping) {
			return new MethodDispatcher()
					.redirect(ColumnOptions.class, new ColumnOptions() {
						@Override
						public ColumnOptions identifier(IdentifierPolicy identifierPolicy) {
							// Please note that we don't check for any id presence in inheritance since this will override parent one (see final build()) 
							if (entityConfigurationSupport.identifierAccessor != null) {
								throw new IllegalArgumentException("Identifier is already defined by " + AccessorDefinition.toString(entityConfigurationSupport.identifierAccessor));
							}
							entityConfigurationSupport.identifierAccessor = newMapping.getAccessor();
							entityConfigurationSupport.identifierPolicy = identifierPolicy;
							
							if (newMapping instanceof EntityLinkageByColumnName) {
								// we force primary key
								((EntityLinkageByColumnName) newMapping).primaryKey();
							} else if (newMapping instanceof EntityLinkageByColumn) {
								if (!((EntityLinkageByColumn) newMapping).isPrimaryKey()){
									// safeguard about misconfiguration, even if mapping would work it smells bad configuration
									throw new IllegalArgumentException("Identifier policy is assigned to a non primary key column");
								}
							} else {
								// in case of evolution in the Linkage API
								throw new NotImplementedException(newMapping.getClass());
							}
							return null;
						}
						
						@Override
						public ColumnOptions mandatory() {
							newMapping.setNullable(false);
							return null;
						}
						
						@Override
						public ColumnOptions setByConstructor() {
							newMapping.setByConstructor();
							return null;
						}
					}, true)
					.fallbackOn(entityConfigurationSupport)
					.build((Class<IFluentMappingBuilderPropertyOptions<C, I>>) (Class) IFluentMappingBuilderPropertyOptions.class);
		}
	}
	
	private static class OptimisticLockOption<C> {
		
		private final VersioningStrategy<Object, C> versioningStrategy;
		
		public OptimisticLockOption(AccessorByMethodReference<Object, C> versionAccessor, Serie<C> serie) {
			this.versioningStrategy = new VersioningStrategySupport<>(new PropertyAccessor<>(
					versionAccessor,
					Accessors.mutator(versionAccessor.getDeclaringClass(), propertyName(versionAccessor.getMethodName()), versionAccessor.getPropertyType())
			), serie);
		}
		
		public VersioningStrategy getVersioningStrategy() {
			return versioningStrategy;
		}
	}
	
	/**
	 * A small class for one-to-many options storage into a {@link CascadeMany}. Acts as a wrapper over it.
	 */
	static class OneToManyOptionsSupport<C, I, O, S extends Collection<O>>
			implements OneToManyOptions<C, I, O, S> {
		
		private final CascadeMany<C, O, I, S> cascadeMany;
		
		public OneToManyOptionsSupport(CascadeMany<C, O, I, S> cascadeMany) {
			this.cascadeMany = cascadeMany;
		}
		
		@Override
		public IFluentMappingBuilderOneToManyOptions<C, I, O, S> mappedBy(SerializableBiConsumer<O, ? super C> reverseLink) {
			cascadeMany.setReverseSetter(reverseLink);
			return null;	// we can return null because dispatcher will return proxy
		}
		
		@Override
		public IFluentMappingBuilderOneToManyOptions<C, I, O, S> mappedBy(SerializableFunction<O, ? super C> reverseLink) {
			cascadeMany.setReverseGetter(reverseLink);
			return null;	// we can return null because dispatcher will return proxy
		}
		
		@Override
		public IFluentMappingBuilderOneToManyOptions<C, I, O, S> mappedBy(Column<Table, ?> reverseLink) {
			cascadeMany.setReverseColumn(reverseLink);
			return null;	// we can return null because dispatcher will return proxy
		}
		
		@Override
		public OneToManyOptions<C, I, O, S> reverselySetBy(SerializableBiConsumer<O, C> reverseLink) {
			cascadeMany.setReverseLink(reverseLink);
			return null;	// we can return null because dispatcher will return proxy
		}
		
		@Override
		public IFluentMappingBuilderOneToManyOptions<C, I, O, S> initializeWith(Supplier<S> collectionFactory) {
			cascadeMany.setCollectionFactory(collectionFactory);
			return null;	// we can return null because dispatcher will return proxy
		}
		
		@Override
		public IFluentMappingBuilderOneToManyOptions<C, I, O, S> cascading(RelationMode relationMode) {
			cascadeMany.setRelationMode(relationMode);
			return null;	// we can return null because dispatcher will return proxy
		}
	}
	
	/**
	 * Stores informations of {@link InheritanceConfiguration}
	 * 
	 * @param <E> entity type
	 * @param <I> identifier type
	 */
	static class InheritanceConfigurationSupport<E, I> implements InheritanceConfiguration<E, I> {
		
		private final EntityMappingConfiguration<E, I> configuration;
		
		private boolean joinTable = false;
		
		private Table table;
		
		InheritanceConfigurationSupport(EntityMappingConfiguration<E, I> configuration) {
			this.configuration = configuration;
		}
		
		@Override
		public EntityMappingConfiguration<E, I> getConfiguration() {
			return configuration;
		}
		
		@Override
		public boolean isJoinTable() {
			return this.joinTable;
		}
		
		@Override
		public Table getTable() {
			return this.table;
		}
	}
}
