package org.gama.stalactite.sql.result;

import org.gama.lang.collection.Maps;
import org.gama.stalactite.sql.binder.DefaultResultSetReaders;
import org.gama.stalactite.sql.dml.SQLStatement.BindingException;
import org.junit.jupiter.api.Test;

import static org.gama.lang.collection.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Guillaume Mary
 */
public class RowIteratorTest {
	
	@Test
	public void testConvert() {
		RowIterator testInstance = new RowIterator(null,
				Maps.asMap("toto", DefaultResultSetReaders.INTEGER_READER));
		Exception thrownException = assertThrows(BindingException.class, () -> {
			InMemoryResultSet rs = new InMemoryResultSet(asList(Maps.asMap("toto", "string value")));
			rs.next();
			testInstance.convert(rs);
		});
		assertEquals("Error while reading column 'toto' : trying to read 'string value' as java.lang.Integer but was java.lang.String", thrownException.getMessage());
		assertEquals(ClassCastException.class, thrownException.getCause().getClass());
		assertEquals("java.lang.String cannot be cast to java.lang.Integer", thrownException.getCause().getMessage());
	}
}