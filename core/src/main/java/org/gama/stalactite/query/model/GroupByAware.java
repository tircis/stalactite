package org.gama.stalactite.query.model;

import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.query.model.SelectQuery.FluentGroupBy;

/**
 * @author Guillaume Mary
 */
public interface GroupByAware {
	
	FluentGroupBy groupBy(Column column, Column... columns);
	
	FluentGroupBy groupBy(String column, String... columns);
}
