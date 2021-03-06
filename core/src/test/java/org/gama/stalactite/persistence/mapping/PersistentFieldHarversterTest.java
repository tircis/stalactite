package org.gama.stalactite.persistence.mapping;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;

import org.gama.lang.collection.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Guillaume Mary
 */
public class PersistentFieldHarversterTest {
	
	public static Object[][] testNextMethodsData() {
		return new Object[][] {
				{ X.class, Arrays.asList("f1") },
				{ Y.class, Arrays.asList("f2", "f1") },
				{ Z.class, Arrays.asList("f2", "f2", "f1") }
		};
	}
	
	@ParameterizedTest
	@MethodSource("testNextMethodsData")
	public void testGetFields(Class clazz, List<String> expectedFields) {
		PersistentFieldHarverster testInstance = new PersistentFieldHarverster();
		Iterable<Field> fields = testInstance.getFields(clazz);
		Iterator<Field> fieldsIterator = fields.iterator();
		assertTrue(fieldsIterator.hasNext());
		for (String expectedField : expectedFields) {
			assertEquals(expectedField, fieldsIterator.next().getName());
		}
		assertFalse(fieldsIterator.hasNext());
	}
	
	static class X { private String f1; }
	
	static class Y extends X { private String f2; }
	
	static class Z extends Y { private String f2; }
	
}