package org.gama.stalactite.query.model;

import org.gama.stalactite.persistence.structure.Column;

/**
 * @author Guillaume Mary
 */
public class ColumnCriterion extends AbstractCriterion {
	
	private final Column column;
	private final Object /* String or Operator */ condition;
	
	public ColumnCriterion(Column column, CharSequence condition) {
		this(null, column, condition);
	}
	
	public ColumnCriterion(Column column, AbstractRelationalOperator operator) {
		this(null, column, operator);
	}
	
	public ColumnCriterion(LogicalOperator operator, Column column, Object /* String or Operator */ condition) {
		super(operator);
		this.column = column;
		this.condition = condition;
	}
	
	public Column getColumn() {
		return column;
	}

	public Object getCondition() {
		return condition;
	}
	
	public ColumnCriterion copyFor(Column column) {
		return new ColumnCriterion(getOperator(), column, getCondition());
	}
}
