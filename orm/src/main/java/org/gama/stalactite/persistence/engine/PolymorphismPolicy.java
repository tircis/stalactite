package org.gama.stalactite.persistence.engine;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gama.lang.Duo;
import org.gama.lang.collection.Iterables;
import org.gama.stalactite.persistence.structure.Table;

/**
 * @author Guillaume Mary
 */
public interface PolymorphismPolicy<C> {
	
	static <C> TablePerClassPolymorphism<C> tablePerClass() {
		return new TablePerClassPolymorphism<>();
	}
	
	static <C> JoinedTablesPolymorphism<C> joinedTables() {
		return new JoinedTablesPolymorphism<>();
	}
	
	static <C> JoinedTablesPolymorphism<C> joinedTables(Class<? extends C> c) {
		return new JoinedTablesPolymorphism<>();
	}
	
	/**
	 * Creates a single-table polymorphism configuration with a default discriminating column names "DTYPE" of {@link String} type
	 * @param <C> entity type
	 * @return a new {@link SingleTablePolymorphism} with "DTYPE" as String discriminator column
	 */
	static <C> SingleTablePolymorphism<C, String> singleTable() {
		return singleTable("DTYPE");
	}
	
	static <C> SingleTablePolymorphism<C, String> singleTable(String discriminatorColumnName) {
		return new SingleTablePolymorphism<>(discriminatorColumnName, String.class);
	}
	
	Set<SubEntityMappingConfiguration<? extends C>> getSubClasses();
	
	class TablePerClassPolymorphism<C> implements PolymorphismPolicy<C> {
		
		private final Set<Duo<SubEntityMappingConfiguration<? extends C>, Table /* Nullable */>> subClasses = new HashSet<>();
		
		public TablePerClassPolymorphism<C> addSubClass(SubEntityMappingConfiguration<? extends C> entityMappingConfigurationProvider) {
			addSubClass(entityMappingConfigurationProvider, null);
			return this;
		}
		
		public TablePerClassPolymorphism<C> addSubClass(SubEntityMappingConfiguration<? extends C> entityMappingConfigurationProvider, @Nullable Table table) {
			subClasses.add(new Duo<>((SubEntityMappingConfiguration<? extends C>) entityMappingConfigurationProvider, table));
			return this;
		}
		
		public Set<SubEntityMappingConfiguration<? extends C>> getSubClasses() {
			return Iterables.collect(subClasses, Duo::getLeft, HashSet::new);
		}
		
		@Nullable
		public Table giveTable(SubEntityMappingConfiguration<? extends C> key) {
			return Iterables.find(subClasses, duo -> duo.getLeft().equals(key)).getRight();
		}
	}
	
	class JoinedTablesPolymorphism<C> implements PolymorphismPolicy<C> {
		
		private final Set<Duo<SubEntityMappingConfiguration<? extends C>, Table /* Nullable */>> subClasses = new HashSet<>();
		
		public JoinedTablesPolymorphism<C> addSubClass(SubEntityMappingConfiguration<? extends C> entityMappingConfigurationProvider) {
			addSubClass(entityMappingConfigurationProvider, null);
			return this;
		}
		
		public JoinedTablesPolymorphism<C> addSubClass(SubEntityMappingConfiguration<? extends C> entityMappingConfigurationProvider, @Nullable Table table) {
			subClasses.add(new Duo<>(entityMappingConfigurationProvider, table));
			return this;
		}
		
		public Set<SubEntityMappingConfiguration<? extends C>> getSubClasses() {
			return Iterables.collect(subClasses, Duo::getLeft, HashSet::new);
		}
		
		@Nullable
		public Table giveTable(SubEntityMappingConfiguration key) {
			return Iterables.find(subClasses, duo -> duo.getLeft().equals(key)).getRight();
		}
	}
	
	class SingleTablePolymorphism<C, D> implements PolymorphismPolicy<C> {
		
		private final String discriminatorColumn;
		
		private final Class<D> discriminatorType;
		
		private final Map<D, SubEntityMappingConfiguration<? extends C>> subClasses = new HashMap<>();
		
		public SingleTablePolymorphism(String discriminatorColumn, Class<D> discriminatorType) {
			this.discriminatorColumn = discriminatorColumn;
			this.discriminatorType = discriminatorType;
		}
		
		public String getDiscriminatorColumn() {
			return discriminatorColumn;
		}
		
		public Class<D> getDiscrimintorType() {
			return discriminatorType;
		}
		
		public SingleTablePolymorphism<C, D> addSubClass(SubEntityMappingConfiguration<? extends C> entityMappingConfiguration, D discriminatorValue) {
			subClasses.put(discriminatorValue, entityMappingConfiguration);
			return this;
		}
		
		public Class<? extends C> getClass(D discriminatorValue) {
			return subClasses.get(discriminatorValue).getEntityType();
		}
		
		public D getDiscriminatorValue(Class<? extends C> instanceType) {
			return Iterables.find(subClasses.entrySet(), e -> e.getValue().getEntityType().isAssignableFrom(instanceType)).getKey();
		}
		
		public Set<SubEntityMappingConfiguration<? extends C>> getSubClasses() {
			return new HashSet<>(this.subClasses.values());
		}
	}
}
