package org.gama.stalactite.persistence.engine.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.gama.stalactite.persistence.id.Identifier;
import org.gama.stalactite.persistence.id.PersistableIdentifier;

/**
 * @author Guillaume Mary
 */
public class Car extends Vehicle {
	
	private String model;
	
	private Radio radio;
	
	private List<Wheel> wheels = new ArrayList<>();
	
	private Set<String> plates = new HashSet<>();
	
	public Car() {
	}
	
	public Car(Long id) {
		this(new PersistableIdentifier<>(id));
	}
	
	public Car(Identifier<Long> id) {
		super(id);
	}
	
	public Car(Long id, String model) {
		this(new PersistableIdentifier<>(id), model);
	}
	
	public Car(Identifier<Long> id, String model) {
		super(id);
		setModel(model);
	}
	
	public String getModel() {
		return model;
	}
	
	public void setModel(String model) {
		this.model = model;
	}
	
	public Radio getRadio() {
		return radio;
	}
	
	public void setRadio(Radio radio) {
		this.radio = radio;
		radio.setCar(this);
	}
	
	public List<Wheel> getWheels() {
		return wheels;
	}
	
	public void setWheels(List<Wheel> wheels) {
		this.wheels = wheels;
	}
	
	public void addWheel(Wheel wheel) {
		this.wheels.add(wheel);
		wheel.setCar(this);
	}
	
	public Set<String> getPlates() {
		return plates;
	}
	
	public void addPlate(String plateNumber) {
		this.plates.add(plateNumber);
	}
	
	public static class Radio {
		
		private String serialNumber;
		
		private String model;
		
		private Car car;
		
		private boolean persisted;
		
		private Radio() {
		}
		
		public Radio(String serialNumber) {
			this.serialNumber = serialNumber;
		}
		
		public String getSerialNumber() {
			return serialNumber;
		}
		
		public String getModel() {
			return model;
		}
		
		public Radio setModel(String model) {
			this.model = model;
			return this;
		}
		
		public Car getCar() {
			return car;
		}
		
		public void setCar(Car car) {
			this.car = car;
		}
		
		public boolean isPersisted() {
			return persisted;
		}
		
		public void markAsPersisted() {
			this.persisted = true;
		}
		
		@Override
		public boolean equals(Object o) {
			return EqualsBuilder.reflectionEquals(this, o);
		}
		
		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
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
	
	public static class Wheel {
		
		private String serialNumber;
		
		private String model;
		
		private Car car;
		
		private boolean persisted;
		
		private Wheel() {
		}
		
		public Wheel(String serialNumber) {
			this.serialNumber = serialNumber;
		}
		
		public String getSerialNumber() {
			return serialNumber;
		}
		
		public String getModel() {
			return model;
		}
		
		public Wheel setModel(String model) {
			this.model = model;
			return this;
		}
		
		public Car getCar() {
			return car;
		}
		
		public void setCar(Car car) {
			this.car = car;
		}
		
		public boolean isPersisted() {
			return persisted;
		}
		
		public void markAsPersisted() {
			this.persisted = true;
		}
		
		@Override
		public boolean equals(Object o) {
			return EqualsBuilder.reflectionEquals(this, o);
		}
		
		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
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
}
