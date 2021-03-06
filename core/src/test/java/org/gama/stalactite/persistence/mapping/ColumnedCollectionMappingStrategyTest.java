package org.gama.stalactite.persistence.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Maps;
import org.gama.lang.collection.Maps.ChainingMap;
import org.gama.stalactite.sql.result.Row;
import org.gama.stalactite.persistence.mapping.IMappingStrategy.UpwhereColumn;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Guillaume Mary
 */
public class ColumnedCollectionMappingStrategyTest {
	
	private static Table targetTable;
	private static Column col1;
	private static Column col2;
	private static Column col3;
	private static Column col4;
	private static Column col5;
	
	@BeforeAll
	public static void setUpClass() {
		targetTable = new Table<>(null, "Toto");
		int nbCol = 5;
		for (int i = 1; i <= nbCol; i++) {
			String columnName = "col_" + i;
			targetTable.addColumn(columnName, String.class);
		}
		Map<String, Column> namedColumns = targetTable.mapColumnsOnName();
		col1 = namedColumns.get("col_1");
		col1.setPrimaryKey(true);
		col2 = namedColumns.get("col_2");
		col3 = namedColumns.get("col_3");
		col4 = namedColumns.get("col_4");
		col5 = namedColumns.get("col_5");
	}
	
	private ColumnedCollectionMappingStrategy<List<String>, String, Table> testInstance;
	
	@BeforeEach
	public void setUp() {
		testInstance = new ColumnedCollectionMappingStrategy<List<String>, String, Table>(targetTable, targetTable.getColumns(), (Class<List<String>>) (Class) ArrayList.class) {
			@Override
			protected String toCollectionValue(Object object) {
				return object == null ?  null : object.toString();
			}
		};
	}
	
	
	public static Object[][] testGetInsertValues() {
		setUpClass();
		return new Object[][] {
				{ Arrays.asList("a", "b", "c"), Maps.asMap(col1, "a").add(col2, "b").add(col3, "c").add(col4, null).add(col5, null) },
				{ Arrays.asList("a", "b", null), Maps.asMap(col1, "a").add(col2, "b").add(col3, null).add(col4, null).add(col5, null) },
				{ null, Maps.asMap(col1, null).add(col2, null).add(col3, null).add(col4, null).add(col5, null) },
		};
	}
	
	@ParameterizedTest
	@MethodSource("testGetInsertValues")
	public void testGetInsertValues(List<String> toInsert, ChainingMap<Column, String> expected) {
		Map<Column<Table, Object>, Object> insertValues = testInstance.getInsertValues(toInsert);
		assertEquals(insertValues, expected);
	}
	
	public static Object[][] testGetUpdateValues_diffOnly() {
		setUpClass();
		return new Object[][] {
				{ Arrays.asList("a", "b", "c"), Arrays.asList("x", "y", "x"),
						Maps.asMap(col1, "a").add(col2, "b").add(col3, "c") },
				{ Arrays.asList("a", "b"), Arrays.asList("x", "y", "x"),
						Maps.asMap(col1, "a").add(col2, "b").add(col3, null) },
				{ Arrays.asList("a", "b", "c"), Arrays.asList("x", "y"),
						Maps.asMap(col1, "a").add(col2, "b").add(col3, "c") },
				{ Arrays.asList("x", "b"), Arrays.asList("x", "y"),
						Maps.asMap(col2, "b") },
				{ Arrays.asList("x", "b", null), Arrays.asList("x", "y", "z"),
						Maps.asMap(col2, "b").add(col3, null) },
				{ Arrays.asList("x", "b", null), null,
						Maps.asMap(col1, "x").add(col2, "b") },
		};
	}
	
	@ParameterizedTest
	@MethodSource("testGetUpdateValues_diffOnly")
	public void testGetUpdateValues_diffOnly(List<String> modified, List<String> unmodified, Map<Column, String> expected) {
		Map<UpwhereColumn<Table>, Object> updateValues = testInstance.getUpdateValues(modified, unmodified, false);
		Map<UpwhereColumn, Object> expectationWithUpwhereColumn = new HashMap<>();
		expected.forEach((c, s) -> expectationWithUpwhereColumn.put(new UpwhereColumn(c, true), s));
		assertEquals(expectationWithUpwhereColumn, updateValues);
	}
	
	public static Object[][] testGetUpdateValues_allColumns() {
		setUpClass();
		return new Object[][] {
				{ Arrays.asList("a", "b", "c"), Arrays.asList("x", "y", "x"),
						Maps.asMap(col1, "a").add(col2, "b").add(col3, "c").add(col4, null).add(col5, null) },
				{ Arrays.asList("a", "b"), Arrays.asList("x", "y", "x"),
						Maps.asMap(col1, "a").add(col2, "b").add(col3, null).add(col4, null).add(col5, null) },
				{ Arrays.asList("a", "b", "c"), Arrays.asList("x", "y"),
						Maps.asMap(col1, "a").add(col2, "b").add(col3, "c").add(col4, null).add(col5, null) },
				{ Arrays.asList("x", "b"), Arrays.asList("x", "y"),
						Maps.asMap(col1, "x").add(col2, "b").add(col3, null).add(col4, null).add(col5, null) },
				{ Arrays.asList("x", "b", null), Arrays.asList("x", "y", "z"),
						Maps.asMap(col1, "x").add(col2, "b").add(col3, null).add(col4, null).add(col5, null) },
				{ Arrays.asList("x", "b", null), null,
						Maps.asMap(col1, "x").add(col2, "b").add(col3, null).add(col4, null).add(col5, null) },
				{ Arrays.asList("a", "b", "c"), Arrays.asList("a", "b", "c"),
						new HashMap<>() },
		};
	}
	
	@ParameterizedTest
	@MethodSource("testGetUpdateValues_allColumns")
	public void testGetUpdateValues_allColumns(List<String> modified, List<String> unmodified, Map<Column, String> expected) {
		Map<UpwhereColumn<Table>, Object> updateValues = testInstance.getUpdateValues(modified, unmodified, true);
		Map<UpwhereColumn, Object> expectationWithUpwhereColumn = new HashMap<>();
		expected.forEach((c, s) -> expectationWithUpwhereColumn.put(new UpwhereColumn(c, true), s));
		assertEquals(expectationWithUpwhereColumn, updateValues);
	}
	
	@Test
	public void testTransform() {
		Row row = new Row();
		row.put(col1.getName(), "a");
		row.put(col2.getName(), "b");
		row.put(col3.getName(), "c");
		List<String> toto = testInstance.transform(row);
		// all 5th first element should be filled
		assertEquals("a", toto.get(0));
		assertEquals("b", toto.get(1));
		assertEquals("c", toto.get(2));
		assertNull(toto.get(3));
		assertNull(toto.get(4));
		// there's not more element since mapping used 5 columns
		assertEquals(5, toto.size());
	}
}