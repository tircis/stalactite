package org.gama.stalactite.persistence.engine.cascade;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.stalactite.persistence.engine.Persister;
import org.gama.stalactite.persistence.id.generator.AutoAssignedIdentifierGenerator;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.sql.Dialect;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Guillaume Mary
 */
public class DeleteToAfterDeleteCascaderTest extends AbstractCascaderTest {
	
	@Test
	public void testAfterDelete() throws SQLException {
		ClassMappingStrategy mappingStrategyMock = mock(ClassMappingStrategy.class);
		// AutoAssignedIdentifierGenerator is sufficient for our test case
		when(mappingStrategyMock.getIdentifierGenerator()).thenReturn(new AutoAssignedIdentifierGenerator());
		Persister<Tata, Long> persisterMock = new Persister<Tata, Long>(mappingStrategyMock, mock(Dialect.class), null, 10) {
			@Override
			protected int doDelete(Iterable<Tata> iterable) {
				// Overriden to do no action, because default super action is complex to mock
				return 0;
			}
		};
		
		List<String> actions = new ArrayList<>();
		List<Tata> triggeredTarget = new ArrayList<>();
		// Instance to test: overriden methods allow later checking
		DeleteToAfterDeleteCascader<Toto, Tata> testInstance = new DeleteToAfterDeleteCascader<Toto, Tata>(persisterMock) {
			@Override
			protected void postTargetDelete(Iterable<Tata> iterables) {
				actions.add("postTargetDelete");
				triggeredTarget.addAll(Iterables.copy(iterables));
			}
			
			@Override
			protected Collection<Tata> getTargets(Toto toto) {
				actions.add("getTargets");
				return Arrays.asList(toto.tata);
			}
		};
		
		// 
		Toto triggeringInstance1 = new Toto(new Tata());
		Toto triggeringInstance2 = new Toto(new Tata());
		testInstance.afterDelete(Arrays.asList(triggeringInstance1, triggeringInstance2));
		
		// check actions are done in good order
		assertEquals(Arrays.asList("getTargets", "getTargets", "postTargetDelete"), actions);
		// check triggered targets are those expected
		assertEquals(Arrays.asList(triggeringInstance1.tata, triggeringInstance2.tata), triggeredTarget);
	}
}