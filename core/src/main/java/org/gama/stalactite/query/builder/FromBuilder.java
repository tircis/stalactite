package org.gama.stalactite.query.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

import org.gama.lang.StringAppender;
import org.gama.lang.Strings;
import org.gama.lang.bean.Objects;
import org.gama.lang.collection.Iterables;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.query.model.From;
import org.gama.stalactite.query.model.From.AliasedTable;
import org.gama.stalactite.query.model.From.ColumnJoin;
import org.gama.stalactite.query.model.From.CrossJoin;
import org.gama.stalactite.query.model.From.IJoin;
import org.gama.stalactite.query.model.From.AbstractJoin;
import org.gama.stalactite.query.model.From.AbstractJoin.JoinDirection;
import org.gama.stalactite.query.model.From.RawTableJoin;

/**
 * @author Guillaume Mary
 */
public class FromBuilder extends AbstractDMLBuilder implements SQLBuilder {
	
	private final From from;

	public FromBuilder(From from) {
		super(from.getTableAliases());
		this.from = from;
	}

	@Override
	public String toSQL() {
		StringAppender sql = new FromGenerator();
		
		if (from.getJoins().isEmpty()) {
			// invalid SQL
			throw new IllegalArgumentException("Empty from");
		} else {
			Comparator<AliasedTable> aliasedTableComparator = (t1, t2) -> toString(t1).compareToIgnoreCase(toString(t2));
			SortedSet<AliasedTable> addedTables = new TreeSet<>(aliasedTableComparator);
			for (IJoin iJoin : from) {
				if (iJoin instanceof AbstractJoin) {
					AbstractJoin join = (AbstractJoin) iJoin;
					AliasedTable aliasedTable;
					if (addedTables.isEmpty()) {
						addedTables.add(join.getLeftTable());
					}
					
					Collection<AliasedTable> nonAddedTable = new ArrayList<>(); 
					if (addedTables.contains(join.getLeftTable())) {
						nonAddedTable.add(join.getRightTable());
					} else {
						if (addedTables.contains(join.getRightTable())) {
							nonAddedTable.add(join.getLeftTable());
						}
					}
					if (nonAddedTable.isEmpty()) {
						throw new UnsupportedOperationException("Join is declared on non-added tables : "
								+ toString(join.getLeftTable()) + " / " + toString(join.getRightTable()));
					} else if (nonAddedTable.size() == 2) {
						throw new UnsupportedOperationException("Join is declared on already-added tables : "
								+ toString(join.getLeftTable()) + " / " + toString(join.getRightTable()));
					} else {
						aliasedTable = Iterables.first(nonAddedTable);
					}
					
					join.getRightTable();
					sql.cat(join);
					addedTables.add(aliasedTable);
				} else if (iJoin instanceof CrossJoin) {
					sql.cat(iJoin);
					addedTables.add(iJoin.getLeftTable());
				}
			}
		}
		return sql.toString();
	}
	
	private static String toString(AliasedTable table) {
		return table.getTable().getAbsoluteName() + " as " + table.getAlias();
	}
	
	/**
	 * A dedicated {@link StringAppender} for the From clause
	 */
	private class FromGenerator extends StringAppender {
		
		private static final String INNER_JOIN = " inner join ";
		private static final String LEFT_OUTER_JOIN = " left outer join ";
		private static final String RIGHT_OUTER_JOIN = " right outer join ";
		private static final String CROSS_JOIN = " cross join ";
		private static final String ON = " on ";
		
		public FromGenerator() {
			super(200);
		}
		
		/** Overriden to dispatch to dedicated cat methods */
		@Override
		public StringAppender cat(Object o) {
			if (o instanceof AliasedTable) {
				return cat((AliasedTable) o);
			} else if (o instanceof CrossJoin) {
				return cat((CrossJoin) o);
			} else if (o instanceof AbstractJoin) {
				return cat((AbstractJoin) o);
			} else {
				return super.cat(o);
			}
		}
		
		private StringAppender cat(AliasedTable aliasedTable) {
			Table table = aliasedTable.getTable();
			String tableAlias = Objects.preventNull(aliasedTable.getAlias(), getAlias(table));
			return cat(table.getName()).catIf(!Strings.isEmpty(tableAlias), " as " + tableAlias);
		}
		
		private StringAppender cat(CrossJoin join) {
			catIf(length() > 0, CROSS_JOIN).cat(join.getLeftTable());
			return this;
		}
		
		private StringAppender cat(AbstractJoin join) {
			catIf(length() == 0, join.getLeftTable());
			cat(join.getJoinDirection(), join.getRightTable());
			if (join instanceof RawTableJoin) {
				cat(((RawTableJoin) join).getJoinClause());
			} else if (join instanceof ColumnJoin) {
				ColumnJoin columnJoin = (ColumnJoin) join;
				CharSequence leftPrefix = Strings.preventEmpty(columnJoin.getLeftTable().getAlias(), columnJoin.getLeftTable().getTable().getName());
				CharSequence rightPrefix = Strings.preventEmpty(columnJoin.getRightTable().getAlias(), columnJoin.getRightTable().getTable().getName());
				cat(leftPrefix, ".", columnJoin.getLeftColumn().getName(), " = ", rightPrefix, ".", columnJoin.getRightColumn().getName());
			} else {
				// did I miss something ?
				throw new UnsupportedOperationException("From building is not implemented for " + join.getClass().getName());
			}
			return this;
		}
		
		protected void cat(JoinDirection joinDirection, AliasedTable joinTable) {
			String joinType;
			switch (joinDirection) {
				case INNER_JOIN:
					joinType = INNER_JOIN;
					break;
				case LEFT_OUTER_JOIN:
					joinType = LEFT_OUTER_JOIN;
					break;
				case RIGHT_OUTER_JOIN:
					joinType = RIGHT_OUTER_JOIN;
					break;
				default:
					throw new IllegalArgumentException("Join type not implemented");
			}
			cat(joinType, joinTable, ON);
		}
	}
}
