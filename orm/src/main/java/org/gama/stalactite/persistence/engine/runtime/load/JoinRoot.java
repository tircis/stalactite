package org.gama.stalactite.persistence.engine.runtime.load;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import org.gama.lang.collection.ReadOnlyList;
import org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree.EntityInflater;
import org.gama.stalactite.persistence.engine.runtime.load.EntityTreeInflater.TreeInflationContext;
import org.gama.stalactite.persistence.mapping.ColumnedRow;
import org.gama.stalactite.persistence.mapping.IRowTransformer;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.sql.result.Row;

/**
 * Very first table (and its joins) of a from clause
 * 
 * @author Guillaume Mary
 */
public class JoinRoot<C, I, T extends Table> implements JoinNode<T> {

	private final EntityJoinTree<C, I> tree;
	
	/** Root entity inflater */
	private final EntityInflater<C, I, T> entityInflater;
	
	private final T table;
	
	/** Joins */
	private final List<AbstractJoinNode> joins = new ArrayList<>();
	
	@Nullable
	private String tableAlias;
	
	public JoinRoot(EntityJoinTree<C, I> tree, EntityInflater<C, I, T> entityInflater, T table) {
		this.tree = tree;
		this.entityInflater = entityInflater;
		this.table = table;
	}
	
	@Override
	public EntityJoinTree<C, I> getTree() {
		return tree;
	}
	
	public EntityInflater<C, I, T> getEntityInflater() {
		return entityInflater;
	}
	
	@Override
	public T getTable() {
		return table;
	}
	
	@Override
	public Set<Column<T, Object>> getColumnsToSelect() {
		return getEntityInflater().getSelectableColumns();
	}
	
	@Override
	public ReadOnlyList<AbstractJoinNode> getJoins() {
		return new ReadOnlyList<>(joins);
	}
	
	@Override
	public void add(AbstractJoinNode node) {
		this.joins.add(node);
	}
	
	@Nullable
	@Override
	public String getTableAlias() {
		return tableAlias;
	}
	
	@Override
	public JoinRootRowConsumer<C, I> toConsumer(ColumnedRow columnedRow) {
		return new JoinRootRowConsumer<>(entityInflater, columnedRow);
	}
	
	static class JoinRootRowConsumer<C, I> implements JoinRowConsumer {
		
		private final Class<C> entityType;
		
		/** Root entity identifier decoder */
		private final BiFunction<Row, ColumnedRow, I> identifierDecoder;
		
		private final IRowTransformer<C> entityBuilder;
		
		private final ColumnedRow columnedRow;
		
		JoinRootRowConsumer(EntityInflater<C, I, ? extends Table> entityInflater, ColumnedRow columnedRow) {
			this.entityType = entityInflater.getEntityType();
			this.identifierDecoder = entityInflater::giveIdentifier;
			this.entityBuilder = entityInflater.copyTransformerWithAliases(columnedRow);
			this.columnedRow = columnedRow;
		}
		
		C createRootInstance(Row row, TreeInflationContext context) {
			Object identifier = identifierDecoder.apply(row, columnedRow);
			if (identifier == null) {
				return null;
			} else {
				return (C) context.giveEntityFromCache(entityType, identifier, () -> entityBuilder.transform(row));
			}
		}
	} 
}
