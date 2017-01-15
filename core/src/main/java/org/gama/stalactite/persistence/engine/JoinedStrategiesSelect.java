package org.gama.stalactite.persistence.engine;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.gama.lang.Strings;
import org.gama.sql.binder.ParameterBinder;
import org.gama.sql.binder.ParameterBinderProvider;
import org.gama.stalactite.persistence.engine.JoinedStrategiesSelect.StrategyJoins.Join;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.persistence.structure.Table.Column;
import org.gama.stalactite.query.model.From;
import org.gama.stalactite.query.model.SelectQuery;

/**
 * Class that eases the creation of a SQL selection with multiple joined {@link ClassMappingStrategy}.
 * The representation of a link between strategies is done throught {@link StrategyJoins}
 *
 * @author Guillaume Mary
 * @see #buildSelectQuery()
 */
public class JoinedStrategiesSelect<T, I> {
	
	/** Key of the very first {@link StrategyJoins} added to the join structure (the one generated by constructor) */
	public static final String FIRST_STRATEGY_NAME = "ROOT";
	
	/** Mappig between column name in select and their {@link ParameterBinder} for reading */
	private final Map<String, ParameterBinder> selectParameterBinders = new HashMap<>();
	/** Aliases of columns. Values are keys of {@link #selectParameterBinders} */
	private final Map<Column, String> aliases = new HashMap<>();
	/** Will give the {@link ParameterBinder} for the reading of the final select clause */
	private final ParameterBinderProvider<Column> parameterBinderProvider;
	/** The very first {@link ClassMappingStrategy} on which other strategies will be joined */
	private final StrategyJoins<T> root;
	/**
	 * A mapping between a name and join to help finding them when we want to join them with a new one
	 * @see #add(String, ClassMappingStrategy, Column, Column, boolean, BeanRelationFixer) 
	 */
	private final Map<String, StrategyJoins> strategyIndex = new HashMap<>();
	/** The objet that will help to give names of strategies into the index (no impact on the generated SQL) */
	private final StrategyIndexNamer indexNamer = new StrategyIndexNamer();
	
	private ColumnAliasBuilder columnAliasBuilder = new ColumnAliasBuilder();
	
	/**
	 * Default constructor
	 *
	 * @param classMappingStrategy the root strategy, added strategy will be joined wih it
	 * @param parameterBinderProvider the objet that will give {@link ParameterBinder} to read the selected columns
	 */
	JoinedStrategiesSelect(ClassMappingStrategy<T, I> classMappingStrategy, ParameterBinderProvider<Column> parameterBinderProvider) {
		this(classMappingStrategy, parameterBinderProvider, FIRST_STRATEGY_NAME);
	}
	
	/**
	 * Default constructor
	 *
	 * @param classMappingStrategy the root strategy, added strategy will be joined wih it
	 * @param parameterBinderProvider the objet that will give {@link ParameterBinder} to read the selected columns
	 */
	JoinedStrategiesSelect(ClassMappingStrategy<T, I> classMappingStrategy, ParameterBinderProvider<Column> parameterBinderProvider, String strategyName) {
		this.parameterBinderProvider = parameterBinderProvider;
		this.root = new StrategyJoins<>(classMappingStrategy);
		this.strategyIndex.put(strategyName, this.root);
	}
	
	@SuppressWarnings("unchecked")
	public ClassMappingStrategy<T, I> getRoot() {
		return (ClassMappingStrategy<T, I>) root.getStrategy();
	}
	
	public Map<String, ParameterBinder> getSelectParameterBinders() {
		return selectParameterBinders;
	}
	
	/**
	 * @return the generated aliases by {@link Column} during the {@link #addColumnsToSelect(String, Iterable, SelectQuery)} phase
	 */
	public Map<Column, String> getAliases() {
		return aliases;
	}
	
	StrategyJoins getStrategyJoins(String leftStrategyName) {
		return this.strategyIndex.get(leftStrategyName);
	}
	
	public Collection<StrategyJoins> getStrategies() {
		return strategyIndex.values();
	}
	
	public SelectQuery buildSelectQuery() {
		SelectQuery selectQuery = new SelectQuery();
		
		// initialization of the from clause with the very first table
		From from = selectQuery.getFrom().add(root.getTable());
		String tableAlias = columnAliasBuilder.buildAlias(root.getTable(), root.getTableAlias());
		addColumnsToSelect(tableAlias, root.getStrategy().getSelectableColumns(), selectQuery);
		
		Queue<Join> stack = new ArrayDeque<>();
		stack.addAll(root.getJoins());
		while (!stack.isEmpty()) {
			Join join = stack.poll();
			String joinTableAlias = columnAliasBuilder.buildAlias(join.getStrategy().getTable(), join.getStrategy().getTableAlias());
			addColumnsToSelect(joinTableAlias, join.getStrategy().getStrategy().getSelectableColumns(), selectQuery);
			Column leftJoinColumn = join.getLeftJoinColumn();
			Column rightJoinColumn = join.getRightJoinColumn();
			from.add(from.new ColumnJoin(leftJoinColumn, rightJoinColumn, join.isOuter() ? false : null));
			
			stack.addAll(join.getStrategy().getJoins());
		}
		
		return selectQuery;
	}
	
	public <U> String add(String leftStrategyName, ClassMappingStrategy<U, ?> strategy, Column leftJoinColumn, Column rightJoinColumn,
						  boolean isOuterJoin, BeanRelationFixer beanRelationFixer) {
		StrategyJoins hangingJoins = getStrategyJoins(leftStrategyName);
		if (hangingJoins == null) {
			throw new IllegalStateException("No strategy with name " + leftStrategyName + " exists to add a new strategy");
		}
		return add(hangingJoins, strategy, leftJoinColumn, rightJoinColumn, isOuterJoin, beanRelationFixer);
	}
	
	private <U> String add(StrategyJoins owner, ClassMappingStrategy<U, ?> strategy,
						   Column leftJoinColumn, Column rightJoinColumn, boolean isOuterJoin,
						   BeanRelationFixer beanRelationFixer) {
		Join join = owner.add(strategy, leftJoinColumn, rightJoinColumn, isOuterJoin, beanRelationFixer);
		String indexKey = indexNamer.generateName(strategy);
		strategyIndex.put(indexKey, join.getStrategy());
		return indexKey;
	}
	
	private void addColumnsToSelect(String tableAlias, Iterable<Column> selectableColumns, SelectQuery selectQuery) {
		for (Column selectableColumn : selectableColumns) {
			String alias = columnAliasBuilder.buildAlias(tableAlias, selectableColumn);
			selectQuery.select(selectableColumn, alias);
			// we link the column alias to the binder so it will be easy to read the ResultSet
			selectParameterBinders.put(alias, parameterBinderProvider.getBinder(selectableColumn));
			aliases.put(selectableColumn, alias);
		}
	}
	
	/**
	 * Joins between strategies: owns the left part of the join, and "right parts" are represented by a collection of {@link Join}.
	 *
	 * @param <I> the type of the entity mapped by the {@link ClassMappingStrategy}
	 */
	static class StrategyJoins<I> {
		/** The left part of the join */
		private final ClassMappingStrategy<I, ?> strategy;
		/** Joins */
		private final List<Join> joins = new ArrayList<>();
		
		private String tableAlias;
		
		StrategyJoins(ClassMappingStrategy<I, ?> strategy) {
			this(strategy, strategy.getTargetTable().getAbsoluteName());
		}
		
		StrategyJoins(ClassMappingStrategy<I, ?> strategy, String absoluteName) {
			this.strategy = strategy;
			this.tableAlias = absoluteName;
		}
		
		public ClassMappingStrategy<I, ?> getStrategy() {
			return strategy;
		}
		
		public List<Join> getJoins() {
			return joins;
		}
		
		public Table getTable() {
			return strategy.getTargetTable();
		}
		
		public String getTableAlias() {
			return tableAlias;
		}
		
		/**
		 * To use to force the table alias of the strategy table in the select statement
		 * @param tableAlias not null, nor empty, sql compliant (none checked)
		 */
		public void setTableAlias(String tableAlias) {
			this.tableAlias = tableAlias;
		}
		
		/**
		 * Method dedicated to OneToOne relation
		 * @param strategy the new strategy on which to join
		 * @param leftJoinColumn the column of the owned strategy table (no check done) on which the join will be made
		 * @param rightJoinColumn the column of the new strategy table (no check done) on whoch the join will be made
		 * @param isOuterJoin indicates if the join is an outer (left) one or not
		 * @param beanRelationFixer will help to apply the instance of the new strategy on the owned one
		 * @return the created join
		 */
		<U> Join<I, U> add(ClassMappingStrategy strategy, Column leftJoinColumn, Column rightJoinColumn, boolean isOuterJoin, BeanRelationFixer beanRelationFixer) {
			Join<I, U> join = new Join<>(strategy, leftJoinColumn, rightJoinColumn, isOuterJoin, beanRelationFixer);
			this.joins.add(join);
			return join;
		}
		
		/** The "right part" of a join between between 2 {@link ClassMappingStrategy} */
		public static class Join<I, O> {
			/** The right part of the join */
			private final StrategyJoins<O> strategy;
			/** Join column with previous strategy table */
			private final Column leftJoinColumn;
			/** Join column with next strategy table */
			private final Column rightJoinColumn;
			/** Indicates if the join must be an inner or (left) outer join */
			private final boolean outer;
			/** Relation fixer for instances of this strategy on owning strategy entities */
			private final BeanRelationFixer beanRelationFixer;
			
			private Join(ClassMappingStrategy<O, ?> strategy, Column leftJoinColumn, Column rightJoinColumn, boolean outer, BeanRelationFixer beanRelationFixer) {
				this.strategy = new StrategyJoins<>(strategy);
				this.leftJoinColumn = leftJoinColumn;
				this.rightJoinColumn = rightJoinColumn;
				this.outer = outer;
				this.beanRelationFixer = beanRelationFixer;
			}
			
			StrategyJoins<O> getStrategy() {
				return strategy;
			}
			
			private Column getLeftJoinColumn() {
				return leftJoinColumn;
			}
			
			private Column getRightJoinColumn() {
				return rightJoinColumn;
			}
			
			public boolean isOuter() {
				return outer;
			}
			
			public BeanRelationFixer getBeanRelationFixer() {
				return beanRelationFixer;
			}
		}
	}
	
	private static class StrategyIndexNamer {
		
		private int aliasCount = 0;
		
		private String generateName(ClassMappingStrategy classMappingStrategy) {
			return classMappingStrategy.getTargetTable().getAbsoluteName() + aliasCount++;
		}
	}
	
	private static class ColumnAliasBuilder {
		
		/**
		 * Gives the alias of a table
		 * @param table the {@link Table} for which an alias is requested
		 * @param aliasOverride an optional given alias
		 * @return the given alias in priority or the name of the table
		 */
		public String buildAlias(Table table, String aliasOverride) {
			return (String) Strings.preventEmpty(aliasOverride, table.getName());
		}
		
		/**
		 * Gives the alias of a Column 
		 * @param tableAlias a non-null table alias
		 * @param selectableColumn the {@link Column} for which an alias is requested
		 * @return tableAlias + "_" + column.getName()
		 */
		public String buildAlias(@Nonnull String tableAlias, Column selectableColumn) {
			return tableAlias + "_" + selectableColumn.getName();
		}
	}
}
