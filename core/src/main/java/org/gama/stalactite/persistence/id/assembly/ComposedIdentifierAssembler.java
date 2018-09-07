package org.gama.stalactite.persistence.id.assembly;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gama.lang.collection.Iterables;
import org.gama.sql.result.Row;
import org.gama.stalactite.persistence.mapping.ColumnedRow;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;

/**
 * Describes the way a composed identifier is read and written to a database.
 *
 * @param <C> entity type
 * @param <I> identifier type
 * @author Guillaume Mary
 * @see SimpleIdentifierAssembler
 */
public abstract class ComposedIdentifierAssembler<C, I> implements IdentifierAssembler<C, I> {
	
	private final Set<Column> primaryKeyColumns;
	
	protected ComposedIdentifierAssembler(Table table) {
		this(table.getPrimaryKey().getColumns());
	}
	
	protected ComposedIdentifierAssembler(Set<Column> primaryKeyColumns) {
		this.primaryKeyColumns = primaryKeyColumns;
	}
	
	@Override
	public I assemble(@Nonnull Row row, @Nonnull ColumnedRow rowAliaser) {
		Map<Column, Object> primaryKeyElements = new HashMap<>();
		for (Column column : primaryKeyColumns) {
			primaryKeyElements.put(column, rowAliaser.getValue(column, row));
		}
		return assemble(primaryKeyElements);
	}
	
	protected abstract I assemble(Map<Column, Object> primaryKeyElements);
	
	@Override
	public <T extends Table<T>> Map<Column<T, Object>, Object> getColumnValues(@Nonnull List<I> ids) {
		Map<Column<T, Object>, Object> pkValues = new HashMap<>();
		// we must pass a single value when expected, else ExpandableStatement may be confused when applying them
		if (ids.size() == 1) {
			Map<Column<T, Object>, Object> localPkValues = getColumnValues(Iterables.first(ids));
			primaryKeyColumns.forEach(pkColumn -> pkValues.put(pkColumn, localPkValues.get(pkColumn)));
		} else {
			ids.forEach(id -> {
				Map<Column<T, Object>, Object> localPkValues = getColumnValues(id);
				primaryKeyColumns.forEach(pkColumn -> ((List<Object>) pkValues.computeIfAbsent(pkColumn, k -> new ArrayList<>())).add(localPkValues.get(pkColumn)));
			});
		}
		return pkValues;
	}
}