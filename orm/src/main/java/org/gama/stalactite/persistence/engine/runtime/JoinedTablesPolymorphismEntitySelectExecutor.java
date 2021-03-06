package org.gama.stalactite.persistence.engine.runtime;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.gama.lang.collection.Iterables;
import org.gama.stalactite.persistence.engine.runtime.load.EntityJoinTree;
import org.gama.stalactite.persistence.engine.runtime.load.EntityTreeQueryBuilder;
import org.gama.stalactite.persistence.mapping.ColumnedRow;
import org.gama.stalactite.persistence.query.IEntitySelectExecutor;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.query.builder.SQLQueryBuilder;
import org.gama.stalactite.query.model.CriteriaChain;
import org.gama.stalactite.query.model.Query;
import org.gama.stalactite.query.model.Select.AliasedColumn;
import org.gama.stalactite.sql.ConnectionProvider;
import org.gama.stalactite.sql.binder.ResultSetReader;
import org.gama.stalactite.sql.dml.PreparedSQL;
import org.gama.stalactite.sql.dml.ReadOperation;
import org.gama.stalactite.sql.dml.SQLExecutionException;
import org.gama.stalactite.sql.result.RowIterator;

/**
 * @author Guillaume Mary
 */
public class JoinedTablesPolymorphismEntitySelectExecutor<C, I, T extends Table> implements IEntitySelectExecutor<C> {
	
	private final Map<Class<? extends C>, JoinedTablesPersister<C, I, T>> persisterPerSubclass;
	private final Map<Class<? extends C>, JoinedTablesPersister<C, I, T>> persisterPerSubclass2;
	private final T mainTable;
	private final EntityJoinTree<C, I> entityJoinTree;
	private final ConnectionProvider connectionProvider;
	private final Dialect dialect;
	
	public JoinedTablesPolymorphismEntitySelectExecutor(Map<Class<? extends C>, JoinedTablesPersister<C, I, T>> persisterPerSubclass,
														Map<Class<? extends C>, JoinedTablesPersister<C, I, T>> persisterPerSubclass2,
													   T mainTable,
													   EntityJoinTree<C, I> entityJoinTree,
													   ConnectionProvider connectionProvider,
													   Dialect dialect) {
		this.persisterPerSubclass = persisterPerSubclass;
		this.persisterPerSubclass2 = persisterPerSubclass2;
		this.mainTable = mainTable;
		this.entityJoinTree = entityJoinTree;
		this.connectionProvider = connectionProvider;
		this.dialect = dialect;
	}
	
	@Override
	public List<C> loadSelection(CriteriaChain where) {
		return null;
	}
	
	@Override
	public List<C> loadGraph(CriteriaChain where) {
		Query query = new EntityTreeQueryBuilder<>(entityJoinTree, dialect.getColumnBinderRegistry()).buildSelectQuery().getQuery();
		
		Column<T, I> primaryKey = (Column<T, I>) Iterables.first(mainTable.getPrimaryKey().getColumns());
		persisterPerSubclass.values().forEach(subclassPersister -> {
			Column subclassPrimaryKey = Iterables.first(
					(Set<Column>) subclassPersister.getMainTable().getPrimaryKey().getColumns());
			query.select(subclassPrimaryKey, subclassPrimaryKey.getAlias());
			query.getFrom().leftOuterJoin(primaryKey, subclassPrimaryKey);
		});
		
		SQLQueryBuilder sqlQueryBuilder = IEntitySelectExecutor.createQueryBuilder(where, query);
		
		// selecting ids and their entity type
		Map<String, ResultSetReader> aliases = new HashMap<>();
		Iterables.stream(query.getSelectSurrogate())
				.map(AliasedColumn.class::cast).map(AliasedColumn::getColumn)
				.forEach(c -> aliases.put(c.getAlias(), dialect.getColumnBinderRegistry().getBinder(c)));
		Map<Class, Set<I>> idsPerSubtype = readIds(sqlQueryBuilder, aliases, primaryKey);
		
		List<C> result = new ArrayList<>();
		idsPerSubtype.forEach((k, v) -> result.addAll(persisterPerSubclass2.get(k).select(v)));
		return result;
	}
	
	private Map<Class, Set<I>> readIds(SQLQueryBuilder sqlQueryBuilder, Map<String, ResultSetReader> aliases,
									   Column<T, I> primaryKey) {
		Map<Class, Set<I>> result = new HashMap<>();
		PreparedSQL preparedSQL = sqlQueryBuilder.toPreparedSQL(dialect.getColumnBinderRegistry());
		try (ReadOperation readOperation = new ReadOperation<>(preparedSQL, connectionProvider)) {
			ResultSet resultSet = readOperation.execute();
			
			RowIterator resultSetIterator = new RowIterator(resultSet, aliases);
			ColumnedRow columnedRow = new ColumnedRow(Column::getAlias);
			resultSetIterator.forEachRemaining(row -> {
				
				// looking for entity type on row : we read each subclass PK and check for nullity. The non-null one is the 
				// right one
				Class<? extends C> entitySubclass;
				Set<Entry<Class<? extends C>, JoinedTablesPersister<C, I, T>>> entries = persisterPerSubclass.entrySet();
				Entry<Class<? extends C>, JoinedTablesPersister<C, I, T>> subclassEntityOnRow = Iterables.find(entries,
						e -> {
							boolean isPKEmpty = true;
							Iterator<Column> columnIt = e.getValue().getMainTable().getPrimaryKey().getColumns().iterator();
							while (isPKEmpty && columnIt.hasNext()) {
								Column column = columnIt.next();
								isPKEmpty = columnedRow.getValue(column, row) != null;
							}
							return isPKEmpty;
						});
				entitySubclass = subclassEntityOnRow.getKey();
				
				// adding identifier to subclass' ids
				result.computeIfAbsent(entitySubclass, k -> new HashSet<>())
						.add((I) columnedRow.getValue(primaryKey, row));
			});
			return result;
		} catch (RuntimeException e) {
			throw new SQLExecutionException(preparedSQL.getSQL(), e);
		}
		
	}
}
