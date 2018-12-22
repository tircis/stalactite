package org.gama.stalactite.persistence.engine;

import java.util.HashSet;
import java.util.Set;

import org.gama.stalactite.persistence.engine.AssociationTableNamingStrategy.DefaultAssociationTableNamingStrategy;
import org.gama.stalactite.persistence.engine.model.City;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Guillaume Mary
 */
class AssociationTableNamingStrategyTest {
	
	@Test
	void giveManySideColumnName() throws NoSuchMethodException {
		Table countryTable = new Table(null, "CountryTable");
		Column countryPK = countryTable.addColumn("id", String.class).primaryKey();
		Table cityTable = new Table(null, "CityTable");
		Column cityPK = cityTable.addColumn("id", String.class).primaryKey();
		Column countryFK = cityTable.addColumn("countryId", String.class);
		
		
		DefaultAssociationTableNamingStrategy testInstance = new DefaultAssociationTableNamingStrategy();
		
		assertEquals("CountryTable_cities", testInstance.giveName(Country.class.getDeclaredMethod("getCities"), countryPK, countryFK));
		assertEquals("CountryTable_CityTables", testInstance.giveName(Country.class.getDeclaredMethod("giveCities"), countryPK, countryFK));
		
	}
	
	private static class Country {
		
		private final Set<City> cities = new HashSet<>();
		
		Country() {
			
		}
		
		public Set<City> getCities() {
			return cities;
		}
		
		public Set<City> giveCities() {
			return cities;
		}
		
	}
}