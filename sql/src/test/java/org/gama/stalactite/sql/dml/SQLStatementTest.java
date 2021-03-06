package org.gama.stalactite.sql.dml;

import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;

import org.gama.lang.collection.Maps;
import org.gama.lang.collection.Maps.ChainingMap;
import org.gama.stalactite.sql.binder.PreparedStatementWriter;
import org.junit.jupiter.api.Test;

import static org.gama.stalactite.sql.binder.DefaultParameterBinders.INTEGER_BINDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * @author Guillaume Mary
 */
public class SQLStatementTest {
	
	@Test
	public void testApplyValue_missingBinder_exceptionIsThrown() {
		SQLStatement<String> testInstance = new SQLStatementStub(Maps.asMap("a", INTEGER_BINDER));
		testInstance.setValues(Maps.asMap("a", 1).add("b", 2));
		assertThrows(IllegalArgumentException.class, () -> testInstance.applyValues(mock(PreparedStatement.class)),
				"Missing binder for [b] for values {a=1, b=2} in \"dummy sql\"");
	}
	
	@Test
	public void testApplyValue_missingValue_exceptionIsThrown() {
		SQLStatement<String> testInstance = new SQLStatementStub(Maps.asMap("a", INTEGER_BINDER));
		testInstance.setValues(Maps.asMap("b", 2));
		assertThrows(IllegalArgumentException.class, () -> testInstance.applyValues(mock(PreparedStatement.class)),
				"Missing value for parameters [a] in values {b=2} in \"dummy sql\"");
	}
	
	@Test
	public void testApplyValue_allBindersPresent_doApplyValueIsCalled() {
		Map<String, Object> appliedValues = new HashMap<>();
		SQLStatement<String> testInstance = new SQLStatementStub(Maps.asMap("a", (PreparedStatementWriter) INTEGER_BINDER).add("b", INTEGER_BINDER)) {
			@Override
			protected void doApplyValue(String key, Object value, PreparedStatement statement) {
				appliedValues.put(key, value);
			}
		};
		ChainingMap<String, Integer> expectedValues = Maps.asMap("a", 1).add("b", 2);
		testInstance.setValues(expectedValues);
		testInstance.applyValues(mock(PreparedStatement.class));
		assertEquals(expectedValues, appliedValues);
	}
	
	private static class SQLStatementStub extends SQLStatement<String> {
		
		public SQLStatementStub(Map<String, PreparedStatementWriter> paramBinders) {
			super(paramBinders);
		}
		
		@Override
		public String getSQL() {
			// we don't need sql in our test
			return "dummy sql";
		}
		
		@Override
		protected void doApplyValue(String key, Object value, PreparedStatement statement) {
			// nothing to do
		}
	}
}