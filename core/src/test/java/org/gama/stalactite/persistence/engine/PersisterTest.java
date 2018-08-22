package org.gama.stalactite.persistence.engine;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.gama.lang.Duo;
import org.gama.lang.Retryer;
import org.gama.lang.collection.Arrays;
import org.gama.lang.collection.Iterables;
import org.gama.lang.collection.Maps;
import org.gama.reflection.Accessors;
import org.gama.reflection.IReversibleAccessor;
import org.gama.sql.ConnectionProvider;
import org.gama.stalactite.persistence.engine.PersisterDatabaseTest.Toto;
import org.gama.stalactite.persistence.engine.PersisterDatabaseTest.TotoTable;
import org.gama.stalactite.persistence.engine.listening.IDeleteByIdListener;
import org.gama.stalactite.persistence.engine.listening.IDeleteListener;
import org.gama.stalactite.persistence.engine.listening.IInsertListener;
import org.gama.stalactite.persistence.engine.listening.IUpdateByIdListener;
import org.gama.stalactite.persistence.engine.listening.IUpdateListener;
import org.gama.stalactite.persistence.engine.listening.IUpdateListener.UpdatePayload;
import org.gama.stalactite.persistence.id.manager.AlreadyAssignedIdentifierManager;
import org.gama.stalactite.persistence.mapping.ClassMappingStrategy;
import org.gama.stalactite.persistence.mapping.IMappingStrategy.UpwhereColumn;
import org.gama.stalactite.persistence.sql.dml.DMLGenerator;
import org.gama.stalactite.persistence.sql.dml.binder.ColumnBinderRegistry;
import org.gama.stalactite.persistence.structure.Column;
import org.gama.stalactite.persistence.structure.Table;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.internal.util.Primitives;

import static org.gama.stalactite.persistence.engine.PersisterTest.Ensures.test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.internal.progress.ThreadSafeMockingProgress.mockingProgress;

/**
 * @author Guillaume Mary
 */
public class PersisterTest {
	
	@Test
	public void testPersist() {
		TotoTable totoTable = new TotoTable("TotoTable");
		Column<TotoTable, Long> primaryKey = totoTable.addColumn("a", Long.class).primaryKey();
		IReversibleAccessor<Toto, Long> identifier = Accessors.accessorByField(Toto.class, "a");
		Map<? extends IReversibleAccessor<Toto, Object>, Column<TotoTable, Object>> mapping = (Map) Maps.asMap(identifier, primaryKey);
		ClassMappingStrategy<Toto, Long, TotoTable> classMappingStrategy = new ClassMappingStrategy<>(Toto.class, totoTable,
				mapping, identifier,
				new AlreadyAssignedIdentifierManager<>(Long.class));
		Persister<Toto, Long, TotoTable> testInstance = new Persister<Toto, Long, TotoTable>(classMappingStrategy,
				mock(ConnectionProvider.class), new DMLGenerator(new ColumnBinderRegistry()), Retryer.NO_RETRY, 0, 0) {
			/** Overriden to prevent from building real world SQL statement because ConnectionProvider is mocked */
			@Override
			protected int doInsert(Iterable entities) {
				return ((Collection) entities).size();
			}
			
			/** Overriden to prevent from building real world SQL statement because ConnectionProvider is mocked */
			@Override
			protected int doUpdateById(Iterable<Toto> entities) {
				return ((Collection) entities).size();
			}
		};
		
		
		IInsertListener insertListener = mock(IInsertListener.class);
		testInstance.getPersisterListener().addInsertListener(insertListener);
		IUpdateByIdListener updateListener = mock(IUpdateByIdListener.class);
		testInstance.getPersisterListener().addUpdateByIdListener(updateListener);
		
		int rowCount = testInstance.persist(Arrays.asList());
		assertEquals(0, rowCount);
		verifyNoMoreInteractions(insertListener);
		verifyNoMoreInteractions(updateListener);
		
		// On persist of a never persisted instance (no id), insertion chain must be invoked 
		Toto unPersisted = new Toto();
		Toto persisted = new Toto(1, 2, 3);
		rowCount = testInstance.persist(unPersisted);
		assertEquals(1, rowCount);
		verify(insertListener).beforeInsert(eq(Arrays.asList(unPersisted)));
		verify(insertListener).afterInsert(eq(Arrays.asList(unPersisted)));
		
		// On persist of a already persisted instance (with id), "rough update" chain must be invoked
		rowCount = testInstance.persist(persisted);
		assertEquals(1, rowCount);
		verify(updateListener).beforeUpdateById(eq(Arrays.asList(persisted)));
		verify(updateListener).afterUpdateById(eq(Arrays.asList(persisted)));
		
		
		clearInvocations(insertListener, updateListener);
		// mix
		rowCount = testInstance.persist(Arrays.asList(unPersisted, persisted));
		assertEquals(2, rowCount);
		verify(insertListener).beforeInsert(eq(Arrays.asList(unPersisted)));
		verify(insertListener).afterInsert(eq(Arrays.asList(unPersisted)));
		verify(updateListener).beforeUpdateById(eq(Arrays.asList(persisted)));
		verify(updateListener).afterUpdateById(eq(Arrays.asList(persisted)));
	}
	
	@Test
	public void testInsert() {
		TotoTable totoTable = new TotoTable("TotoTable");
		Column<TotoTable, Long> primaryKey = totoTable.addColumn("a", Long.class).primaryKey();
		IReversibleAccessor<Toto, Long> identifier = Accessors.accessorByField(Toto.class, "a");
		Map<? extends IReversibleAccessor<Toto, Object>, Column<TotoTable, Object>> mapping = (Map) Maps.asMap(identifier, primaryKey);
		ClassMappingStrategy<Toto, Long, TotoTable> classMappingStrategy = new ClassMappingStrategy<>(Toto.class, totoTable,
				mapping, identifier,
				new AlreadyAssignedIdentifierManager<>(Long.class));
		Persister<Toto, Long, TotoTable> testInstance = new Persister<Toto, Long, TotoTable>(classMappingStrategy,
				mock(ConnectionProvider.class), new DMLGenerator(new ColumnBinderRegistry()), Retryer.NO_RETRY, 0, 0) {
			/** Overriden to prevent from building real world SQL statement because ConnectionProvider is mocked */
			@Override
			protected int doInsert(Iterable entities) {
				return ((Collection) entities).size();
			}
		};
		
		
		IInsertListener insertListener = mock(IInsertListener.class);
		testInstance.getPersisterListener().addInsertListener(insertListener);
		
		int rowCount = testInstance.insert(Arrays.asList());
		assertEquals(0, rowCount);
		verifyNoMoreInteractions(insertListener);
		
		// On persist of a never persisted instance (no id), insertion chain must be invoked 
		Toto toBeInserted = new Toto(1, 2, 3);
		rowCount = testInstance.insert(toBeInserted);
		assertEquals(1, rowCount);
		verify(insertListener).beforeInsert(eq(Arrays.asList(toBeInserted)));
		verify(insertListener).afterInsert(eq(Arrays.asList(toBeInserted)));
	}
	
	@Test
	public void testUpdate() {
		TotoTable totoTable = new TotoTable("TotoTable");
		Column<TotoTable, Long> primaryKey = totoTable.addColumn("a", Long.class).primaryKey();
		Column<TotoTable, Long> columnB = totoTable.addColumn("b", Long.class);
		IReversibleAccessor<Toto, Long> identifier = Accessors.accessorByField(Toto.class, "a");
		IReversibleAccessor<Toto, Long> propB = Accessors.accessorByField(Toto.class, "b");
		// we must add a property to let us set some differences between 2 instances and have them detected by the system
		Map<? extends IReversibleAccessor<Toto, Object>, Column<TotoTable, Object>> mapping = (Map) Maps
				.asMap(identifier, primaryKey)
				.add(propB, columnB);
		ClassMappingStrategy<Toto, Long, TotoTable> classMappingStrategy = new ClassMappingStrategy<>(Toto.class, totoTable,
				mapping, identifier,
				new AlreadyAssignedIdentifierManager<>(Long.class));
		Persister<Toto, Long, TotoTable> testInstance = new Persister<Toto, Long, TotoTable>(classMappingStrategy,
				mock(ConnectionProvider.class), new DMLGenerator(new ColumnBinderRegistry()), Retryer.NO_RETRY, 0, 0) {
			/** Overriden to prevent from building real world SQL statement because ConnectionProvider is mocked */
			@Override
			protected int doUpdate(Iterable<UpdatePayload<Toto, TotoTable>> entities, boolean allColumnsStatement) {
				return ((Collection) entities).size();
			}
		};
		
		IUpdateListener updateListener = mock(IUpdateListener.class);
		testInstance.getPersisterListener().addUpdateListener(updateListener);
		
		// when nothing to be deleted, listener is not invoked
		testInstance.update(Arrays.asList(), false);
		verifyNoMoreInteractions(updateListener);
		testInstance.update(Arrays.asList(), true);
		verifyNoMoreInteractions(updateListener);
		
		Toto original = new Toto(1, 2, 3);
		Toto modified = new Toto(1, -2, -3);
		
		// On persist of a already persisted instance (with id), "rough update" chain must be invoked
		testInstance.update(modified, original, false);
		UpdatePayload<Toto, TotoTable> expectedPayload = new UpdatePayload<Toto, TotoTable>(
				new Duo<>(modified, original),
				(Map) Maps.asMap(new UpwhereColumn<>(columnB, true), -2)
					.add(new UpwhereColumn<>(primaryKey, false), 1));
		PayloadPredicate<Toto, TotoTable> payloadPredicate = new PayloadPredicate<>(expectedPayload);
		verify(updateListener).beforeUpdate(test(Iterable.class, p -> payloadPredicate.test(Iterables.first(((Iterable<UpdatePayload<Toto, TotoTable>>) p).iterator()))), eq(false));
		verify(updateListener).afterUpdate(test(Iterable.class, p -> payloadPredicate.test(Iterables.first(((Iterable<UpdatePayload<Toto, TotoTable>>) p).iterator()))), eq(false));
	}
	
	public static class PayloadPredicate<A, B extends Table<B>> implements Predicate<UpdatePayload<A, B>> {
		
		public static final BiPredicate<UpdatePayload<?, ?>, UpdatePayload<?, ?>> UPDATE_PAYLOAD_TESTER =
				(p1, p2) -> p1.getValues().equals(p2.getValues()) && p1.getEntities().equals(p2.getEntities());
	
		
		private final UpdatePayload<A, B> expectedPayload;
		
		public PayloadPredicate(UpdatePayload<A, B> expectedPayload) {
			this.expectedPayload = expectedPayload;
		}
		
		@Override
		public boolean test(UpdatePayload<A, B> elem) {
			return UPDATE_PAYLOAD_TESTER.test(elem, expectedPayload);
		}
	}
	
	/**
	 * {@link ArgumentMatcher} to be plugged with a {@link Predicate}
	 * 
	 * @param <C> type of {@link Predicate}'s input
	 */
	public static class Ensures<C> implements ArgumentMatcher<C>, Serializable {
		
		/** Same as utility methods in {@link org.mockito.ArgumentMatchers} dedicated to {@link Ensures} */
		public static <P> P test(Class<P> clazz, Predicate<P> matcher) {
			mockingProgress().getArgumentMatcherStorage().reportMatcher(new Ensures<>(matcher));
			return Primitives.defaultValue(clazz);
		}
		
		private final Predicate<C> predicate;
		
		public Ensures(Predicate<C> predicate) {
			this.predicate = predicate;
		}
		
		public boolean matches(C actual) {
			return predicate.test(actual);
		}
		
	}
	
	@Test
	public void testUpdateById() {
		TotoTable totoTable = new TotoTable("TotoTable");
		Column<TotoTable, Long> primaryKey = totoTable.addColumn("a", Long.class).primaryKey();
		Column<TotoTable, Long> columnB = totoTable.addColumn("b", Long.class);
		IReversibleAccessor<Toto, Long> identifier = Accessors.accessorByField(Toto.class, "a");
		IReversibleAccessor<Toto, Long> propB = Accessors.accessorByField(Toto.class, "b");
		// we must add a property to let us set some differences between 2 instances and have them detected by the system
		Map<? extends IReversibleAccessor<Toto, Object>, Column<TotoTable, Object>> mapping = (Map) Maps
				.asMap(identifier, primaryKey)
				.add(propB, columnB);
		ClassMappingStrategy<Toto, Long, TotoTable> classMappingStrategy = new ClassMappingStrategy<>(Toto.class, totoTable,
				mapping, identifier,
				new AlreadyAssignedIdentifierManager<>(Long.class));
		Persister<Toto, Long, TotoTable> testInstance = new Persister<Toto, Long, TotoTable>(classMappingStrategy,
				mock(ConnectionProvider.class), new DMLGenerator(new ColumnBinderRegistry()), Retryer.NO_RETRY, 0, 0) {
			/** Overriden to prevent from building real world SQL statement because ConnectionProvider is mocked */
			@Override
			protected int doUpdateById(Iterable entities) {
				return ((Collection) entities).size();
			}
		};
		
		IUpdateByIdListener updateListener = mock(IUpdateByIdListener.class);
		testInstance.getPersisterListener().addUpdateByIdListener(updateListener);
		
		// when nothing to be deleted, listener is not invoked
		testInstance.updateById(Arrays.asList());
		verifyNoMoreInteractions(updateListener);
		testInstance.updateById(Arrays.asList());
		verifyNoMoreInteractions(updateListener);
		
		Toto toBeUpdated = new Toto(1, 2, 3);
		
		// On persist of a already persisted instance (with id), "rough update" chain must be invoked
		testInstance.updateById(toBeUpdated);
		verify(updateListener).beforeUpdateById(Arrays.asList(toBeUpdated));
		verify(updateListener).afterUpdateById(Arrays.asList(toBeUpdated));
	}
	
	@Test
	public void testDelete() {
		TotoTable totoTable = new TotoTable("TotoTable");
		Column<TotoTable, Long> primaryKey = totoTable.addColumn("a", Long.class).primaryKey();
		Column<TotoTable, Long> columnB = totoTable.addColumn("b", Long.class);
		IReversibleAccessor<Toto, Long> identifier = Accessors.accessorByField(Toto.class, "a");
		IReversibleAccessor<Toto, Long> propB = Accessors.accessorByField(Toto.class, "b");
		// we must add a property to let us set some differences between 2 instances and have them detected by the system
		Map<? extends IReversibleAccessor<Toto, Object>, Column<TotoTable, Object>> mapping = (Map) Maps
				.asMap(identifier, primaryKey)
				.add(propB, columnB);
		ClassMappingStrategy<Toto, Long, TotoTable> classMappingStrategy = new ClassMappingStrategy<>(Toto.class, totoTable,
				mapping, identifier,
				new AlreadyAssignedIdentifierManager<>(Long.class));
		Persister<Toto, Long, TotoTable> testInstance = new Persister<Toto, Long, TotoTable>(classMappingStrategy,
				mock(ConnectionProvider.class), new DMLGenerator(new ColumnBinderRegistry()), Retryer.NO_RETRY, 0, 0) {
			/** Overriden to prevent from building real world SQL statement because ConnectionProvider is mocked */
			@Override
			protected int doDelete(Iterable entities) {
				return ((Collection) entities).size();
			}
		};
		
		
		IDeleteListener deleteListener = mock(IDeleteListener.class);
		testInstance.getPersisterListener().addDeleteListener(deleteListener);
		
		// when nothing to be deleted, listener is not invoked
		int rowCount = testInstance.delete(Arrays.asList());
		assertEquals(0, rowCount);
		verifyNoMoreInteractions(deleteListener);
		
		Toto toBeDeleted = new Toto(1, 2, 3);
		rowCount = testInstance.delete(toBeDeleted);
		assertEquals(1, rowCount);
		verify(deleteListener).beforeDelete(eq(Arrays.asList(toBeDeleted)));
		verify(deleteListener).afterDelete(eq(Arrays.asList(toBeDeleted)));
	}
	
	@Test
	public void testDeleteById() {
		TotoTable totoTable = new TotoTable("TotoTable");
		Column<TotoTable, Long> primaryKey = totoTable.addColumn("a", Long.class).primaryKey();
		Column<TotoTable, Long> columnB = totoTable.addColumn("b", Long.class);
		IReversibleAccessor<Toto, Long> identifier = Accessors.accessorByField(Toto.class, "a");
		IReversibleAccessor<Toto, Long> propB = Accessors.accessorByField(Toto.class, "b");
		// we must add a property to let us set some differences between 2 instances and have them detected by the system
		Map<? extends IReversibleAccessor<Toto, Object>, Column<TotoTable, Object>> mapping = (Map) Maps
				.asMap(identifier, primaryKey)
				.add(propB, columnB);
		ClassMappingStrategy<Toto, Long, TotoTable> classMappingStrategy = new ClassMappingStrategy<>(Toto.class, totoTable,
				mapping, identifier,
				new AlreadyAssignedIdentifierManager<>(Long.class));
		Persister<Toto, Long, TotoTable> testInstance = new Persister<Toto, Long, TotoTable>(classMappingStrategy,
				mock(ConnectionProvider.class), new DMLGenerator(new ColumnBinderRegistry()), Retryer.NO_RETRY, 0, 0) {
			/** Overriden to prevent from building real world SQL statement because ConnectionProvider is mocked */
			@Override
			protected int doDeleteById(Iterable entities) {
				return ((Collection) entities).size();
			}
		};
		
		IDeleteByIdListener deleteListener = mock(IDeleteByIdListener.class);
		testInstance.getPersisterListener().addDeleteByIdListener(deleteListener);
		
		// when nothing to be deleted, listener is not invoked
		int rowCount = testInstance.deleteById(Arrays.asList());
		assertEquals(0, rowCount);
		verifyNoMoreInteractions(deleteListener);
		Toto toBeDeleted = new Toto(1, 2, 3);
		
		rowCount = testInstance.deleteById(toBeDeleted);
		assertEquals(1, rowCount);
		verify(deleteListener).beforeDeleteById(eq(Arrays.asList(toBeDeleted)));
		verify(deleteListener).afterDeleteById(eq(Arrays.asList(toBeDeleted)));
	}
	
	
}