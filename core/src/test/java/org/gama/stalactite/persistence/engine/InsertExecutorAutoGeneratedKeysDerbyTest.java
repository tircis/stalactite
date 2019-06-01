package org.gama.stalactite.persistence.engine;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gama.lang.Lambdas;
import org.gama.lang.collection.Iterables;
import org.gama.sql.dml.WriteOperation;
import org.gama.sql.result.Row;
import org.gama.sql.test.DerbyInMemoryDataSource;
import org.gama.stalactite.persistence.id.manager.JDBCGeneratedKeysIdentifierManager;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.mapping.SinglePropertyIdAccessor;
import org.gama.stalactite.persistence.sql.ddl.DDLTableGenerator;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.gama.stalactite.persistence.id.manager.JDBCGeneratedKeysIdentifierManager.keyMapper;

/**
 * Same as {@link InsertExecutorAutoGeneratedKeysTest} but dedicated to Derby that requires an override that is not compatible with
 * {@link DataProvider}: {@link AutoGeneratedKeysDerbyDataSet#newPersistenceConfigurationBuilder()}
 *
 * @author Guillaume Mary
 */
public class InsertExecutorAutoGeneratedKeysDerbyTest extends InsertExecutorAutoGeneratedKeysTest {
	
	protected static class AutoGeneratedKeysDerbyDataSet extends AutoGeneratedKeysDataSet {
		
		protected AutoGeneratedKeysDerbyDataSet() throws SQLException {
			super();
		}
		
		@Override
		protected PersistenceConfigurationBuilder newPersistenceConfigurationBuilder() {
			return new PersistenceConfigurationBuilder<Toto, Integer, Table>()
					.withTableAndClass("Toto", Toto.class, (tableAndClass, primaryKeyField) -> {
							Set<Column<? extends Table, Object>> primaryKeyColumns = tableAndClass.targetTable.getPrimaryKey().getColumns();
							Column<? extends Table, Object> primaryKeyColumn = Iterables.first(primaryKeyColumns);
							primaryKeyColumn.setAutoGenerated(true);
							
							String primaryKeyColumnName = primaryKeyColumn.getName();;
							GeneratedKeysReaderAsInt derbyGeneratedKeysReader = new GeneratedKeysReaderAsInt(primaryKeyColumnName) {
								/** Overriden to simulate generated keys for Derby because it only returns the highest generated key */
								@Override
								public List<Row> read(WriteOperation writeOperation) throws SQLException {
									List<Row> rows = super.read(writeOperation);
									// Derby only returns one row: the highest generated key
									Row first = Iterables.first(rows);
									int returnedKey = (int) first.get(getKeyName());
									// we append the missing values in incrementing order, assuming that's a one by one increment
									for (int i = 0; i < writeOperation.getUpdatedRowCount(); i++) {
										Row row = new Row();
										row.put(getKeyName(), returnedKey - i);
										rows.add(0, row);
									}
									return rows;
								}
							};
							
							return new ClassMappingStrategy<Toto, Integer, Table>(
									tableAndClass.mappedClass,
									tableAndClass.targetTable,
									(Map) tableAndClass.persistentFieldHarverster.getFieldToColumn(),
									primaryKeyField,
									new JDBCGeneratedKeysIdentifierManager<>(
											new SinglePropertyIdAccessor<>(primaryKeyField),
											derbyGeneratedKeysReader,
											Lambdas.before(keyMapper(primaryKeyColumnName), () -> generatedKeysGetCallCount.increment()),
											Integer.class)
							);
						
					})
					.withPrimaryKeyFieldName("a");
		}
	}
	
	public static Object[][] dataSources() throws SQLException {
		AutoGeneratedKeysDerbyDataSet dataSet = new AutoGeneratedKeysDerbyDataSet();
		return new Object[][] {
				{ dataSet, new DerbyInMemoryDataSource(), new DDLTableGenerator(dataSet.dialect.getJavaTypeToSqlTypeMapping()) {
					
					@Override
					protected String getSqlType(Column column) {
						String sqlType = super.getSqlType(column);
						if (column.isAutoGenerated()) {
							sqlType += " GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)";
						}
						return sqlType;
					}
				} },
		};
	}
	
	/**
	 * Overriden to force use of the declared data provider in this class, otherwise this test class is exactly the same as the parent one because
	 * the static data provider method is ignored (method hiding)
	 */
	@Override
	@ParameterizedTest
	@MethodSource("dataSources")
	public void testInsert_generated_pk_real_life(AutoGeneratedKeysDataSet dataSet, DataSource dataSource, DDLTableGenerator ddlTableGenerator)
			throws SQLException {
		super.testInsert_generated_pk_real_life(dataSet, dataSource, ddlTableGenerator);
	}
}