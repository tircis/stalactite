package org.gama.stalactite.persistence.engine.cascade;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.gama.lang.StringAppender;
import org.gama.lang.Strings;
import org.gama.lang.collection.Collections;
import org.gama.lang.collection.Iterables;
import org.gama.lang.exception.Exceptions;
import org.gama.sql.IConnectionProvider;
import org.gama.sql.SimpleConnectionProvider;
import org.gama.sql.binder.ParameterBinderIndex;
import org.gama.sql.dml.ReadOperation;
import org.gama.sql.result.Row;
import org.gama.sql.result.RowIterator;
import org.gama.stalactite.persistence.engine.BeanRelationFixer;
import org.gama.stalactite.persistence.engine.ConnectionProvider;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.sql.Dialect;
import org.gama.stalactite.persistence.sql.dml.ColumnParamedSelect;
import org.gama.stalactite.persistence.structure.Table.Column;
import org.gama.stalactite.query.builder.SelectQueryBuilder;
import org.gama.stalactite.query.model.SelectQuery;

import static org.gama.sql.dml.ExpandableSQL.ExpandableParameter.SQL_PARAMETER_MARK_1;
import static org.gama.sql.dml.ExpandableSQL.ExpandableParameter.SQL_PARAMETER_MARK_10;
import static org.gama.sql.dml.ExpandableSQL.ExpandableParameter.SQL_PARAMETER_MARK_100;

/**
 * Class aimed at executing a SQL select statement from multiple joined {@link ClassMappingStrategy}.
 * Based on {@link JoinedStrategiesSelect} for storing the joins structure and {@link StrategyJoinsRowTransformer} for building the entities from
 * the {@link ResultSet}.
 * 
 * @author Guillaume Mary
 */
public class JoinedStrategiesSelectExecutor<T, I> {
	
	/** The surrogate for joining the strategies, will help to build the SQL */
	private final JoinedStrategiesSelect<T, I> joinedStrategiesSelect;
	private final ParameterBinderIndex<Column> parameterBinderProvider;
	private final Map<Column, int[]> inOperatorValueIndexes = new HashMap<>();
	private final int blockSize;
	private final ConnectionProvider connectionProvider;
	
	private final Column keyColumn;
	private List<T> result;
	
	JoinedStrategiesSelectExecutor(ClassMappingStrategy<T, I> classMappingStrategy, Dialect dialect, ConnectionProvider connectionProvider) {
		this.parameterBinderProvider = dialect.getColumnBinderRegistry();
		this.joinedStrategiesSelect = new JoinedStrategiesSelect<>(classMappingStrategy, this.parameterBinderProvider);
		this.connectionProvider = connectionProvider;
		// post-initialization
		this.blockSize = dialect.getInOperatorMaxSize();
		this.keyColumn = classMappingStrategy.getTargetTable().getPrimaryKey();
	}
	
	public ConnectionProvider getConnectionProvider() {
		return connectionProvider;
	}
	
	public <U> String addComplementaryTables(String leftStrategyName, ClassMappingStrategy<U, ?> mappingStrategy,
											 BeanRelationFixer beanRelationFixer,
											 Column leftJoinColumn, Column rightJoinColumn) {
		// we outer join nullable columns
		boolean isOuterJoin = rightJoinColumn.isNullable();
		return addComplementaryTables(leftStrategyName, mappingStrategy, beanRelationFixer, leftJoinColumn, rightJoinColumn, isOuterJoin);
	}
	
	public <U> String addComplementaryTables(String leftStrategyName, ClassMappingStrategy<U, ?> mappingStrategy,
											 BeanRelationFixer beanRelationFixer,
											 Column leftJoinColumn, Column rightJoinColumn, boolean isOuterJoin) {
		return joinedStrategiesSelect.add(leftStrategyName, mappingStrategy, leftJoinColumn, rightJoinColumn, isOuterJoin, beanRelationFixer);
	}
	
	public List<T> select(Iterable<I> ids) {
		// cutting ids into pieces, adjusting expected result size
		List<List<I>> parcels = Collections.parcel(ids, blockSize);
		result = new ArrayList<>(parcels.size() * blockSize);
		
		SelectQuery selectQuery = joinedStrategiesSelect.buildSelectQuery();
		SelectQueryBuilder queryBuilder = new SelectQueryBuilder(selectQuery);
		queryBuilder.toSQL();
		
		// Use same Connection for all operations
		IConnectionProvider connectionProvider = new SimpleConnectionProvider(getConnectionProvider().getCurrentConnection());
		// Creation of the where clause: we use a dynamic "in" operator clause to avoid multiple SelectQueryBuilder instanciation
		DynamicInClause condition = new DynamicInClause();
		selectQuery.where(keyColumn, condition);
		List<I> lastBlock = Iterables.last(parcels);
		// keep only full blocks to run them on the fully filled "in" operator
		int lastBlockSize = lastBlock.size();
		if (lastBlockSize != blockSize) {
			parcels = Collections.cutTail(parcels);
		}
		if (!parcels.isEmpty()) {
			// change parameter mark count to adapt "in" operator values
			condition.setParamMarkCount(blockSize);
			// adding "in" identifiers to where clause
			bindInClause(blockSize);
			execute(connectionProvider, queryBuilder, parcels);
		}
		if (!lastBlock.isEmpty()) {
			// change parameter mark count to adapt "in" operator values
			condition.setParamMarkCount(lastBlockSize);
			bindInClause(lastBlockSize);
			execute(connectionProvider, queryBuilder, java.util.Collections.singleton(lastBlock));
		}
		return result;
	}
	
	private void bindInClause(int inSize) {
		int[] indexes = new int[inSize];
		for (int i = 0; i < inSize; ) {
			indexes[i] = ++i;
		}
		inOperatorValueIndexes.put(keyColumn, indexes);
	}
	
	private void execute(IConnectionProvider connectionProvider,
						 SelectQueryBuilder queryBuilder,
						 Iterable<? extends Iterable<I>> idsParcels) {
		ColumnParamedSelect preparedSelect = new ColumnParamedSelect(queryBuilder.toSQL(), inOperatorValueIndexes, parameterBinderProvider, joinedStrategiesSelect.getSelectParameterBinders());
		ReadOperation<Column> columnReadOperation = new ReadOperation<>(preparedSelect, connectionProvider);
		for (Iterable<I> parcel : idsParcels) {
			execute(columnReadOperation, parcel);
		}
	}
	
	private void execute(ReadOperation<Column> operation, Iterable<I> ids) {
		try (ReadOperation<Column> closeableOperation = operation) {
			operation.setValue(keyColumn, ids);
			ResultSet resultSet = closeableOperation.execute();
			// NB: we give the same ParametersBinders of those given at ColumnParamedSelect since the row iterator is expected to read column from it
			RowIterator rowIterator = new RowIterator(resultSet, ((ColumnParamedSelect) closeableOperation.getSqlStatement()).getSelectParameterBinders());
			result.addAll(transform(rowIterator));
		} catch (Exception e) {
			throw Exceptions.asRuntimeException(e);
		}
	}
	
	List<T> transform(Iterator<Row> rowIterator) {
		StrategyJoinsRowTransformer<T> strategyJoinsRowTransformer = new StrategyJoinsRowTransformer<>(joinedStrategiesSelect.getStrategyJoins(JoinedStrategiesSelect.FIRST_STRATEGY_NAME));
		
		strategyJoinsRowTransformer.setAliases(this.joinedStrategiesSelect.getAliases());
		return strategyJoinsRowTransformer.transform(() -> rowIterator);
	}
	
	private static class DynamicInClause implements CharSequence {
		
		private String dynamicIn;
		
		public DynamicInClause setParamMarkCount(int markCount) {
			StringAppender result = new StringAppender(10 + markCount * SQL_PARAMETER_MARK_1.length());
			Strings.repeat(result.getAppender(), markCount, SQL_PARAMETER_MARK_1, SQL_PARAMETER_MARK_100, SQL_PARAMETER_MARK_10);
			result.cutTail(2);
			result.wrap("in (", ")");
			dynamicIn = result.toString();
			return this;
		}
		
		@Override
		public int length() {
			return dynamicIn.length();
		}
		
		@Override
		public char charAt(int index) {
			return dynamicIn.charAt(index);
		}
		
		@Override
		public CharSequence subSequence(int start, int end) {
			return dynamicIn.subSequence(start, end);
		}
		
		@Override
		public String toString() {
			return dynamicIn;
		}
	}
}