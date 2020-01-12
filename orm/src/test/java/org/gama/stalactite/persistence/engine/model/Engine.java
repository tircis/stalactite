package org.gama.stalactite.persistence.engine.model;

import java.util.Objects;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.gama.stalactite.persistence.engine.FluentEntityMappingConfigurationSupportInheritanceTest;
import org.gama.stalactite.persistence.id.Identified;
import org.gama.stalactite.persistence.id.Identifier;
import org.gama.stalactite.persistence.id.PersistableIdentifier;

/**
 * @author Guillaume Mary
 */
public class Engine implements Identified<Long> {
	
	private Identifier<Long> id;
	
	private double displacement;
	
	public Engine() {
	}
	
	public Engine(Long id) {
		this.id = new PersistableIdentifier<>(id);
	}
	
	public Engine(Identifier<Long> id) {
		this.id = id;
	}
	
	public Identifier<Long> getId() {
		return id;
	}
	
	public double getDisplacement() {
		return displacement;
	}
	
	public void setDisplacement(double displacement) {
		this.displacement = displacement;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		
		Engine engine = (Engine) o;
		
		return Objects.equals(id, engine.id);
	}
	
	@Override
	public int hashCode() {
		return id.hashCode();
	}
	
	/**
	 * Implemented for easier debug
	 *
	 * @return a simple representation of this
	 */
	@Override
	public String toString() {
		return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
