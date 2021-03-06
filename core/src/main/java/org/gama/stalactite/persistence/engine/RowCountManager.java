package org.gama.stalactite.persistence.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A contract that allow to decide what happens when expected and effective row count doesn't match on update and delete statement.
 * Primarly thought to be used with optimistic lock, but may be used even on non versioned entities.
 * 
 * @author Guillaume Mary
 */
public interface RowCountManager {
	
	/** {@link RowCountManager} that will do nothing during check, created for testing purpose */
	RowCountManager NOOP_ROW_COUNT_MANAGER = (rowCounter, effectiveRowCount) -> { /* Nothing is done */ };
	
	/** {@link RowCountManager} that will throw a {@link StaleObjectExcepion} during check if expected and effective row count doesn't match */
	RowCountManager THROWING_ROW_COUNT_MANAGER = (rowCounter, effectiveRowCount) -> {
		if (rowCounter.size() != effectiveRowCount) {
			// row count miss => we throw an exception
			throw new StaleObjectExcepion(rowCounter.size(), effectiveRowCount);
		}
	};
	
	void checkRowCount(RowCounter rowCounter, int effectiveRowCount);
	
	/** A basic register for row update or delete */
	class RowCounter {
		
		/**
		 * All values of SQL statement.
		 * Not crucial but could be usefull for future features needing touched columns or debugging purpose.
		 * This storage should not keep duplicates because it is used for update and delete orders, and database will not update nor delete twice
		 * some records in the same transaction for same values (well ... it depends on transaction isolation, but we left it as this)
		 */
		private final List<Map<? /* UpwhereColumn or Column */, Object>> rowValues = new ArrayList<>(100);
		
		public void add(Map<? /* UpwhereColumn or Column */, Object> updateValues) {
			this.rowValues.add(updateValues);
		}
		
		public int size() {
			return rowValues.size();
		}
	}
}
