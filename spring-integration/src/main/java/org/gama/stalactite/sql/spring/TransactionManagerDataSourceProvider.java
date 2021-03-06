package org.gama.stalactite.sql.spring;

import javax.sql.DataSource;

/**
 * @author Guillaume Mary
 */
@FunctionalInterface
public interface TransactionManagerDataSourceProvider {
	
	DataSource getDataSource();
}
