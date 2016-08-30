package org.gama.stalactite.persistence.id.generator;

import java.util.Map;

import org.gama.sql.dml.GeneratedKeysReader;

/**
 * Marker interface for identifier generators which get identifiers from JDBC insert statements thanks to
 * {@link java.sql.PreparedStatement#getGeneratedKeys()}.
 * 
 * Shouldn't be combined with {@link BeforeInsertIdentifierGenerator} nor {@link AutoAssignedIdentifierGenerator}.
 * 
 * @author Guillaume Mary
 */
public interface AfterInsertIdentifierGenerator<I> extends IdentifierGenerator {
	
	I get(Map<String, Object> generatedKeys);
	
	GeneratedKeysReader getGeneratedKeysReader();
}