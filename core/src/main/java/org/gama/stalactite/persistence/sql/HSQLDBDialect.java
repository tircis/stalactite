package org.gama.stalactite.persistence.sql;

import java.util.Collections;

import org.gama.stalactite.persistence.sql.MySQLDialect.MySQLDMLNameProvier;
import org.gama.stalactite.persistence.sql.ddl.DDLAppender;
import org.gama.stalactite.persistence.sql.ddl.DDLTableGenerator;
import org.gama.stalactite.persistence.sql.ddl.JavaTypeToSqlTypeMapping;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.PrimaryKey;

/**
 * @author Guillaume Mary
 */
public class HSQLDBDialect extends Dialect { 
	
	public HSQLDBDialect() {
		super(new HSQLDBTypeMapping());
	}
	
	public static class HSQLDBTypeMapping extends DefaultTypeMapping {
		
		public HSQLDBTypeMapping() {
			super();
			// to prevent "length must be specified in type definition: VARCHAR"
			put(String.class, "varchar(255)");
		}
	}
	
	protected HSQLDBDDLTableGenerator newDdlTableGenerator() {
		return new HSQLDBDDLTableGenerator(getJavaTypeToSqlTypeMapping());
	}
	
	public static class HSQLDBDDLTableGenerator extends DDLTableGenerator {
		
		public HSQLDBDDLTableGenerator(JavaTypeToSqlTypeMapping typeMapping) {
			super(typeMapping, new MySQLDMLNameProvier(Collections.emptyMap()));
		}
		
		@Override
		protected String getSqlType(Column column) {
			String sqlType = super.getSqlType(column);
			if (column.isAutoGenerated()) {
				sqlType += " GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1)";
			}
			return sqlType;
		}
		
		/** Overriden to do nothing because HSQLDB does not support "primary key" and "identity" */
		@Override
		protected void generateCreatePrimaryKey(PrimaryKey primaryKey, DDLAppender sqlCreateTable) {
		}
	}
}
