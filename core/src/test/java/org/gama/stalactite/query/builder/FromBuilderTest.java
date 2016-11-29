package org.gama.stalactite.query.builder;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.persistence.structure.Table.Column;
import org.gama.stalactite.query.model.From;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * @author Guillaume Mary
 */
@RunWith(DataProviderRunner.class)
public class FromBuilderTest {
	
	@DataProvider
	public static Object[][] testToSQL_data() {
		Table tableToto = new Table(null, "Toto");
		Column colTotoA = tableToto.new Column("a", String.class);
		Column colTotoB = tableToto.new Column("b", String.class);
		Table tableTata = new Table(null, "Tata");
		Column colTataA = tableTata.new Column("a", String.class);
		Column colTataB = tableTata.new Column("b", String.class);
		Table tableTutu = new Table(null, "Tutu");
		Column colTutuA = tableTutu.new Column("a", String.class);
		Column colTutuB = tableTutu.new Column("b", String.class);
		
		Table tableToto2 = new Table(null, "Toto2");
		Column colToto2A = tableToto2.new Column("a", String.class);
		Column colToto2B = tableToto2.new Column("b", String.class);
		
		return new Object[][] {
				// testing syntax with Table API
				// 1 join, with or without alias
				{ new From().innerJoin(tableToto, tableTata, "Toto.id = Tata.id"), "Toto inner join Tata on Toto.id = Tata.id" },
				{ new From().innerJoin(tableToto, "to", tableTata, "ta", "to.id = ta.id"), "Toto as to inner join Tata as ta on to.id = ta.id" },
				{ new From().leftOuterJoin(tableToto, tableTata, "Toto.id = Tata.id"), "Toto left outer join Tata on Toto.id = Tata.id" },
				{ new From().leftOuterJoin(tableToto, "to", tableTata, "ta", "to.id = ta.id"), "Toto as to left outer join Tata as ta on to.id = ta.id" },
				{ new From().rightOuterJoin(tableToto, tableTata, "Toto.id = Tata.id"), "Toto right outer join Tata on Toto.id = Tata.id" },
				{ new From().rightOuterJoin(tableToto, "to", tableTata, "ta", "to.id = ta.id"), "Toto as to right outer join Tata as ta on to.id = ta.id" },
				// 2 joins, with or without alias
				{ new From().innerJoin(tableToto, tableTata, "Toto.a = Tata.a").innerJoin(tableToto, tableTutu, "Toto.b = Tutu.b"),
						"Toto inner join Tata on Toto.a = Tata.a inner join Tutu on Toto.b = Tutu.b" },
				{ new From().innerJoin(tableToto, "to", tableTata, "ta", "to.a = ta.a").innerJoin(tableToto, "to", tableTutu, null, "to.b = Tutu.b"),
						"Toto as to inner join Tata as ta on to.a = ta.a inner join Tutu on to.b = Tutu.b" },
				{ new From().innerJoin(tableToto, "to", tableTata, null, "to.a = Tata.a").innerJoin(tableToto, "to", tableTutu, "tu", "to.b = tu.b"),
						"Toto as to inner join Tata on to.a = Tata.a inner join Tutu as tu on to.b = tu.b" },
				
				// testing syntax with Column API
				// 1 join, with or without alias
				{ new From().innerJoin(colTotoA, colTataA), "Toto inner join Tata on Toto.a = Tata.a" },
				{ new From().innerJoin(colTotoA, "to", colTataA, "ta"), "Toto as to inner join Tata as ta on to.a = ta.a" },
				{ new From().leftOuterJoin(colTotoA, colTataA), "Toto left outer join Tata on Toto.a = Tata.a" },
				{ new From().leftOuterJoin(colTotoA, "to", colTataA, "ta"), "Toto as to left outer join Tata as ta on to.a = ta.a" },
				{ new From().rightOuterJoin(colTotoA, colTataA), "Toto right outer join Tata on Toto.a = Tata.a" },
				{ new From().rightOuterJoin(colTotoA, "to", colTataA, "ta"), "Toto as to right outer join Tata as ta on to.a = ta.a" },
				
				// 2 joins, with or without alias
				{ new From().innerJoin(colTotoA, colTataA).innerJoin(colTotoB, colTutuB),
						"Toto inner join Tata on Toto.a = Tata.a inner join Tutu on Toto.b = Tutu.b" },
				{ new From().innerJoin(colTotoA, "to", colTataA, "ta").innerJoin(colTotoB, "to", colTutuB, null),
						"Toto as to inner join Tata as ta on to.a = ta.a inner join Tutu on to.b = Tutu.b" },
				{ new From().innerJoin(colTotoA, "to", colTataA, "ta").crossJoin(tableToto2).innerJoin(colToto2B, colTutuB),
						"Toto as to inner join Tata as ta on to.a = ta.a cross join Toto2 inner join Tutu on Toto2.b = Tutu.b" },
				{ new From().innerJoin(colTotoA, "to", colTataA, null).innerJoin(colTotoB, "to", colTutuB, "tu"),
						"Toto as to inner join Tata on to.a = Tata.a inner join Tutu as tu on to.b = tu.b" },
				{ new From().innerJoin(colTotoA, "to", colTataA, "ta").innerJoin(colTotoB, "to", colTutuB, "tu").innerJoin(colTutuB, "tu", colTataA, "ta"),
						"Toto as to inner join Tata as ta on to.a = ta.a inner join Tutu as tu on to.b = tu.b inner join Tata as ta on tu.b = ta.a" },

				// mix with Table and Column
				{ new From().innerJoin(tableToto, tableTata, "Toto.a = Tata.a").innerJoin(colTotoB, colTutuB),
						"Toto inner join Tata on Toto.a = Tata.a inner join Tutu on Toto.b = Tutu.b" },
				
				// testing syntax with cross join
				{ new From().add(tableToto),
						"Toto" },
				{ new From().add(tableToto).crossJoin(tableTata),
						"Toto cross join Tata" },
				{ new From().innerJoin(tableToto, tableTata, "id = id"),
						"Toto inner join Tata on id = id" },
				{ new From().add(tableToto, "to").crossJoin(tableTata).innerJoin(tableToto, "to", tableTutu, "", "id = id"),
						"Toto as to cross join Tata inner join Tutu on id = id" },
				{ new From().add(tableToto, "to").crossJoin(tableTata).innerJoin(colTotoA, "to", colTutuA, ""),
						"Toto as to cross join Tata inner join Tutu on to.a = Tutu.a" },
				{ new From().add(tableToto).leftOuterJoin(tableToto, tableTata, "id = id"),
						"Toto left outer join Tata on id = id" },
				{ new From().add(tableToto).crossJoin(tableTata).leftOuterJoin(tableTata, tableTutu, "id = id"),
						"Toto cross join Tata left outer join Tutu on id = id" },
				{ new From().add(tableToto).rightOuterJoin(tableToto, tableTata, "id = id"),
						"Toto right outer join Tata on id = id" },
				{ new From().add(tableToto).crossJoin(tableTata).rightOuterJoin(tableToto, tableTutu, "id = id"),
						"Toto cross join Tata right outer join Tutu on id = id" },
		};
	}
	
	@Test
	@UseDataProvider("testToSQL_data")
	public void testToSQL(From from, String expected) {
		FromBuilder testInstance = new FromBuilder(from);
		assertEquals(expected, testInstance.toSQL());
	}
}