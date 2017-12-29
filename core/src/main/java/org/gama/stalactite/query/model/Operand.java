package org.gama.stalactite.query.model;

import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.query.builder.OperandBuilder;
import org.gama.stalactite.query.model.operand.Between;
import org.gama.stalactite.query.model.operand.Count;
import org.gama.stalactite.query.model.operand.Equals;
import org.gama.stalactite.query.model.operand.Greater;
import org.gama.stalactite.query.model.operand.In;
import org.gama.stalactite.query.model.operand.IsNull;
import org.gama.stalactite.query.model.operand.Like;
import org.gama.stalactite.query.model.operand.Lower;
import org.gama.stalactite.query.model.operand.Max;
import org.gama.stalactite.query.model.operand.Min;
import org.gama.stalactite.query.model.operand.Sum;

/**
 * General contract for operators such as <code>in, like, =, <, >, ... </code>.
 * Value of the operator is intentionnally left vague (Object), except for String operation, because some operators prefer {@link Column}, while
 * others prefers {@link Comparable}.
 * 
 * Static methods should be used to ease a fluent write of queries.
 * 
 * @author Guillaume Mary
 */
public abstract class Operand {
	
	public static Equals eq(Object value) {
		return new Equals(value);
	}
	
	public static <I extends Operand> I not(I operand) {
		operand.setNot();
		return operand;
	}
	
	/**
	 * Shortcut to <code>new Lower(value)</code> to ease a fluent write of queries for "lower than" comparisons
	 * @param value a value, null accepted, transformed to "is null" by {@link OperandBuilder})
	 * @return a new instance of {@link Lower}
	 */
	public static Lower lt(Object value) {
		return new Lower(value);
	}
	
	/**
	 * Shortcut to <code>new Lower(value, true)</code> to ease a fluent write of queries for "lower than equals" comparisons
	 * @param value a value, null accepted, transformed to "is null" by {@link OperandBuilder})
	 * @return a new instance of {@link Lower} with equals checking
	 */
	public static Lower lteq(Object value) {
		return new Lower(value, true);
	}
	
	/**
	 * Shortcut to <code>new Greater(value)</code> to ease a fluent write of queries for "greater than" comparisons
	 * @param value a value, null accepted, transformed to "is null" by {@link OperandBuilder})
	 * @return a new instance of {@link Greater}
	 */
	public static Greater gt(Object value) {
		return new Greater(value);
	}
	
	/**
	 * Shortcut to <code>new Greater(value, true)</code> to ease a fluent write of queries for "greater than equals" comparisons
	 * @param value a value, null accepted, transformed to "is null" by {@link OperandBuilder})
	 * @return a new instance of {@link Greater} with equals checking
	 */
	public static Greater gteq(Object value) {
		return new Greater(value, true);
	}
	
	/**
	 * Shortcut to <code>new Between(value1, value2)</code> to ease a fluent write of queries for "between" comparisons
	 * @param value1 a value, null accepted, transformed to "is null" by {@link OperandBuilder}) if both values are
	 * @param value2 a value, null accepted, transformed to "is null" by {@link OperandBuilder}) if both values are
	 * @return a new instance of {@link Between} with equals checking
	 */
	public static <O> Between between(O value1, O value2) {
		return new Between(value1, value2);
	}
	
	/**
	 * Shortcut to <code>new In(value)</code> to ease a fluent write of queries for "in" comparisons
	 * @param value a value, null accepted, transformed to "is null" by {@link OperandBuilder})
	 * @return a new instance of {@link In}
	 */
	public static In in(Iterable value) {
		return new In(value);
	}
	
	/**
	 * Shortcut to <code>new In(value)</code> to ease a fluent write of queries for "in" comparisons.
	 * Note that this signature won't transform null values to "is null" by {@link OperandBuilder}), prefers {@link #in(Iterable)} for it.
	 * 
	 * @param value a value, null accepted <b>but won't be transformed</b> to "is null" by {@link OperandBuilder})
	 * @return a new instance of {@link In}
	 * @see #in(Iterable)
	 */
	public static In in(Object ... value) {
		return new In(value);
	}
	
	/**
	 * Shortcut to <code>new IsNull()</code> to ease a fluent write of queries for "is null" comparisons
	 * @return a new instance of {@link IsNull}
	 */
	public static IsNull isNull() {
		return new IsNull();
	}
	
	/**
	 * Shortcut to <code>not(new IsNull())</code> to ease a fluent write of queries for "is not null" comparisons
	 * @return a new instance, negative form, of {@link IsNull}
	 */
	public static IsNull isNotNull() {
		return not(isNull());
	}
	
	/**
	 * Shortcut to <code>new Like(value)</code> to ease a fluent write of queries for "like" comparisons
	 * @return a new instance of {@link Like}
	 */
	public static Like like(CharSequence value) {
		return new Like(value);
	}
	
	/**
	 * Shortcut to <code>new Like(value, true, true)</code> to ease a fluent write of queries for "contains" comparisons
	 * @return a new instance of {@link Like}
	 */
	public static Like contains(CharSequence value) {
		return new Like(value, true, true);
	}
	
	/**
	 * Shortcut to <code>new Like(value, false, true)</code> to ease a fluent write of queries for "startsWith" comparisons
	 * @return a new instance of {@link Like}
	 */
	public static Like startsWith(CharSequence value) {
		return new Like(value, false, true);
	}
	
	/**
	 * Shortcut to <code>new Like(value, true, false)</code> to ease a fluent write of queries for "endsWith" comparisons
	 * @return a new instance of {@link Like}
	 */
	public static Like endsWith(CharSequence value) {
		return new Like(value, true, false);
	}
	
	/**
	 * Shortcut to <code>new Sum(column)</code> to ease a fluent write of queries for "sum" operation
	 * @return a new instance of {@link Sum}
	 */
	public static Sum sum(Column column) {
		return new Sum(column);
	}
	
	/**
	 * Shortcut to <code>new Count(column)</code> to ease a fluent write of queries for "count" operation
	 * @return a new instance of {@link Count}
	 */
	public static Count count(Column column) {
		return new Count(column);
	}
	
	/**
	 * Shortcut to <code>new Min(column)</code> to ease a fluent write of queries for "min" operation
	 * @return a new instance of {@link Min}
	 */
	public static Min min(Column column) {
		return new Min(column);
	}
	
	/**
	 * Shortcut to <code>new Max(column)</code> to ease a fluent write of queries for "max" operation
	 * @return a new instance of {@link Max}
	 */
	public static Max max(Column column) {
		return new Max(column);
	}
	
	/** Value of the operator */
	private Object value;
	
	/** Is this operator must be negated ? */
	private boolean not;
	
	/**
	 * Single constructor, basic
	 * @param value the value of the operator
	 */
	protected Operand(Object value) {
		this.value = value;
	}
	
	/**
	 * @return the value of this operand
	 */
	public Object getValue() {
		return value;
	}
	
	/**
	 * Sets the value of this operant
	 * @param value the new value
	 */
	public void setValue(Object value) {
		this.value = value;
	}
	
	/**
	 * @return true if this operand uses "not"
	 */
	public boolean isNot() {
		return not;
	}
	
	/**
	 * Sets "not" value
	 * @param not true for this operand to use "not", false to let it use normal operand
	 */
	public void setNot(boolean not) {
		this.not = not;
	}
	
	/**
	 * Negates this operand
	 */
	public void setNot() {
		setNot(true);
	}
	
	/**
	 * Reverses logical operator
	 */
	public void switchNot() {
		this.not = !this.not;
	}
}
