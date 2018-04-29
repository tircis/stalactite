# Dialects

Dialect contains the ways to interact with RDBMSes : each of them have particular ways to do the same things,
 even if they are SQL-99 compliant. Hence Dialect might be specialiazed for each RDBMS.

They declare :

* Java type to SQL type mapping, see [JavaTypeToSqlTypeMapping](ddl/JavaTypeToSqlTypeMapping.java)
* SQL generation, see [DMLGenerator](dml/DMLGenerator.java), [DDLGenerator](ddl/DDLGenerator.java), [DDLSchemaGenerator](ddl/DDLSchemaGenerator.java)


Specialized Dialects are available for :
- [MySQL](MySQLDialect.java)
- [HSQLDB](HSQLDBDialect.java)