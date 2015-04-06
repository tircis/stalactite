package org.stalactite.benchmark;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.stalactite.lang.exception.Exceptions;
import org.stalactite.persistence.id.sequence.PooledSequenceIdentifierGenerator;
import org.stalactite.persistence.id.sequence.PooledSequenceIdentifierGeneratorOptions;
import org.stalactite.persistence.id.sequence.PooledSequencePersistenceOptions;
import org.stalactite.persistence.mapping.ClassMappingStrategy;
import org.stalactite.persistence.mapping.ColumnedMapMappingStrategy;
import org.stalactite.persistence.mapping.PersistentFieldHarverster;
import org.stalactite.persistence.structure.Table;
import org.stalactite.persistence.structure.Table.Column;

/**
 * @author Guillaume Mary
 */
public class TotoMappingBuilder {
	
	private TotoTable targetTable;
	
	public ClassMappingStrategy getClassMappingStrategy() {
		targetTable = new TotoTable();
		PersistentFieldHarverster persistentFieldHarverster = new PersistentFieldHarverster();
		Map<Field, Column> fieldColumnMap = persistentFieldHarverster.mapFields(Toto.class, targetTable);
		ClassMappingStrategy<Toto> classMappingStrategy = new ClassMappingStrategy<>(Toto.class, targetTable, fieldColumnMap, new PooledSequenceIdentifierGenerator(new PooledSequenceIdentifierGeneratorOptions(100, "Toto", PooledSequencePersistenceOptions.DEFAULT)));
		Field answersField = null;
		try {
			answersField = Toto.class.getDeclaredField("answers");
		} catch (NoSuchFieldException e) {
			Exceptions.throwAsRuntimeException(e);
		}
		classMappingStrategy.put(answersField, new ColumnedMapMappingStrategy<Map<Long, Object>, Long, Object, Object>(targetTable, targetTable.dynamicColumns.values(), HashMap.class) {
			@Override
			protected Column getColumn(Long key) {
				return targetTable.dynamicColumns.get(key);
			}
			
			@Override
			protected Object toDatabaseValue(Long key, Object value) {
				return value;
			}
			
			@Override
			protected Long getKey(Column column) {
				return targetTable.dynamicColumns.inverseBidiMap().get(column);
			}
			
			@Override
			protected Object toMapValue(Long key, Object t) {
				if (t == null) {
					return null;
				} else {
					return t;
				}
			}
		});
		return classMappingStrategy;
	}
	
	public TotoTable getTargetTable() {
		return targetTable;
	}
	
	public static class TotoTable extends Table {
		
		public final Column id;
//		public final Column a;
//		public final Column b;
		public final BidiMap<Long, Column> dynamicColumns = new DualHashBidiMap<>();
		
		public TotoTable() {
			super(null, "Toto");
			id = new Column("id", Long.TYPE);
			id.setPrimaryKey(true);
//			a = new Column("a", String.class);
//			b = new Column("b", Integer.class);
			for (int i = 0; i < Toto.QUESTION_COUNT; i++) {
				Class columnType;
//				if (i%2 == 0) {
//					columnType = String.class;
//				} else {
					columnType = Integer.class;
//				}
				Column column = new Column("q" + i, columnType);
				dynamicColumns.put((long) i, column);
				if (i%5==0) {
					new Index(column, "idx_" + column.getName());
				}
			}
		}
	}
	
	public static class Toto {
		
		public static int QUESTION_COUNT = 100;
		
		private Long id;
//		private String a;
//		private Integer b;
		public Map<Long, Object> answers = new HashMap<>();
		
		public Toto() {
		}
		
		public void setId(long id) {
			this.id = id;
		}
		
		//		public void setA(String a) {
//			this.a = a;
//		}
//		
//		public void setB(Integer b) {
//			this.b = b;
//		}
		
		public void put(Long key, Object value) {
			answers.put(key, value);
		}
	}
}