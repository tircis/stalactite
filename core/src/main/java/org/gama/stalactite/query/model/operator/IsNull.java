package org.gama.stalactite.query.model.operator;

import org.gama.stalactite.query.model.UnitaryOperator;

/**
 * Represents a "is null" comparison
 * 
 * @author Guillaume Mary
 */
public class IsNull extends UnitaryOperator {
	
	public IsNull() {
		super(null);
	}
	
	@Override
	public void setValue(Object value) {
		// setting a value on this as no effect because it has no sense
	}
	
	@Override
	public final boolean isNull() {
		return true;
	}
}
