package org.gama.stalactite.persistence.engine.runtime.load;

import javax.annotation.Nullable;
import java.util.Set;

import org.gama.lang.collection.ReadOnlyList;
import org.gama.stalactite.persistence.mapping.ColumnedRow;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * @author Guillaume Mary
 */
public interface JoinNode<T extends Table> {
	
	T getTable();
	
	Set<Column<T, Object>> getColumnsToSelect();
	
	@Nullable
	String getTableAlias();
	
	/**
	 * Return elements joined to this node. Those can only be of type {@link AbstractJoinNode} since it can't be {@link JoinRoot}.
	 * 
	 * @return joins associated to this node
	 */
	ReadOnlyList<AbstractJoinNode> getJoins();
	
	void add(AbstractJoinNode node);
	
	JoinRowConsumer toConsumer(ColumnedRow columnedRow);
}
