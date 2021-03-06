package org.gama.stalactite.persistence.sql.ddl;

import org.gama.lang.StringAppender;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.gama.stalactite.query.builder.DMLNameProvider;

/**
 * A {@link StringAppender} that automatically appends {@link Table} and {@link Column} names
 */
public class DDLAppender extends StringAppender {
	
	/** Made transient to comply with {@link java.io.Serializable} contract of parent class */
	private final transient DMLNameProvider dmlNameProvider;
	
	public DDLAppender(DMLNameProvider dmlNameProvider, Object... o) {
		this.dmlNameProvider = dmlNameProvider;
		// we don't all super(o) because it may need dmlNameProvider
		cat(o);
	}
	
	/**
	 * Overriden to append {@link Table} and {@link Column} names according to {@link DMLNameProvider} given at construction time
	 * 
	 * @param o any object
	 * @return this
	 */
	@Override
	public StringAppender cat(Object o) {
		if (o instanceof Table) {
			return super.cat(dmlNameProvider.getSimpleName((Table) o));
		} else if (o instanceof Column) {
			return super.cat(dmlNameProvider.getSimpleName((Column) o));
		} else {
			return super.cat(o);
		}
	}
}
