// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mockit.Deencapsulation;
import mockit.Expectations;
import org.junit.jupiter.api.Test;

public class BasicCounterAggregatorTest {

  private long CURRENT_TIME = System.currentTimeMillis();

  @Test
  public void testAggregation() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 10);

    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(new String[]{"sum"}, cursor.getFields());
    assertArrayEquals(new String[]{"testTag", "value"}, cursor.getTags());
    assertEquals(110L, cursor.readLong(0));
    assertEquals(1, table.size());

    table.apply(new String[]{"testTag", "value", "testTag2", "value2"}, 10L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value", "testTag2", "value2"}, 99L, CURRENT_TIME);
    assertEquals(2, table.size());

    table.apply(new String[]{"testTag", "value2"}, 2L, CURRENT_TIME);
    assertEquals(3, table.size());

    String[] tagSet1 = new String[]{"testTag", "value"};
    String[] tagSet2 = new String[]{"testTag", "value", "testTag2", "value2"};
    String[] tagSet3 = new String[]{"testTag", "value2"};

    assertEquals(10, table.capacity());
    assertEquals(3, table.size());

    cursor = table.cursor();
    while (cursor.next()) {
      final int hash = Arrays.hashCode(cursor.getTags());
      assertEquals(CURRENT_TIME, cursor.lastUpdated());
      if (hash == Arrays.hashCode(tagSet1)) {
        assertEquals(111L, cursor.readLong(0));
      } else if (hash == Arrays.hashCode(tagSet2)) {
        assertEquals(109L, cursor.readLong(0));
      } else if (hash == Arrays.hashCode(tagSet3)) {
        assertEquals(2L, cursor.readLong(0));
      } else {
        fail("Unknown hashcode");
      }
    }
  }
  
  @Test
  public void testReadAndReset() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 10);

    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    assertTrue(cursor.next());

    assertEquals(110L, cursor.readAndResetLong(0));

    // Assert that identity is set
    assertEquals(0L, cursor.readLong(0));
  }

  @Test
  public void testGrowTable() {
    final BasicCounterAggregator aggregator = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 2);
    aggregator.apply(new String[]{}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value2"}, 1L, CURRENT_TIME);

    assertEquals(3, aggregator.size());
    assertEquals(6, aggregator.capacity());
    // next prime from 2 is 3
    aggregator.apply(new String[]{"testTag", "value3"}, 1L, CURRENT_TIME);

    assertEquals(4, aggregator.size());
    assertEquals(6, aggregator.capacity());
  }

  @Test
  public void testGrowTableWithMaxCapacity() {
    final BasicCounterAggregator aggregator = new BasicCounterAggregator("test", 3, 1);
    aggregator.apply(new String[]{}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value2"}, 1L, CURRENT_TIME);

    // Silently ignored, over capacity
    aggregator.apply(new String[]{"testTag", "value3"}, 1L, CURRENT_TIME);

    assertEquals(3, aggregator.size());
    assertEquals(3, aggregator.capacity()); // caped at the max capacity.
  }
  
  @Test
  public void testFreeCurrentRowAndPurge() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 3);

    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(new String[]{"sum"}, cursor.getFields());
    assertArrayEquals(new String[]{"testTag", "value"}, cursor.getTags());
    assertEquals(110L, cursor.readLong(0));
    assertEquals(1, table.size());

    table.apply(new String[]{"testTag", "value", "testTag2", "value2"}, 10L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value", "testTag2", "value2"}, 99L, CURRENT_TIME);
    assertEquals(2, table.size());
    table.apply(new String[]{"testTag", "value2"}, 2L, CURRENT_TIME - 2*60*1000l);
    assertEquals(3, table.size());

    String[] tagSet1 = new String[]{"testTag", "value"};
    String[] tagSet2 = new String[]{"testTag", "value", "testTag2", "value2"};
    String[] tagSet3 = new String[]{"testTag", "value3"};

    assertEquals(3, table.capacity());
    assertEquals(3, table.size());
    cursor = table.cursor();
    while (cursor.next()) {
      if(cursor.lastUpdated() < CURRENT_TIME) {
        cursor.freeCurrentRow();
      }
    }

    table.apply(new String[]{"testTag", "value3"}, 3L, CURRENT_TIME - 2*60*1000l);
    assertEquals(3, table.size());
    
    cursor = table.cursor();
    while (cursor.next()) {
      final int hash = Arrays.hashCode(cursor.getTags());
      if(cursor.lastUpdated() < CURRENT_TIME) {
        cursor.freeCurrentRow();
      } else {
        assertEquals(CURRENT_TIME, cursor.lastUpdated());
      }
      if (hash == Arrays.hashCode(tagSet1)) {
        assertEquals(111L, cursor.readLong(0));
      } else if (hash == Arrays.hashCode(tagSet2)) {
        assertEquals(109L, cursor.readLong(0));
      } else if (hash == Arrays.hashCode(tagSet3)) {
        assertEquals(3L, cursor.readLong(0));
      } else {
        fail("Unknown hashcode");
      }
      
      cursor = table.sortedCursor();
      while(cursor.next()) {
        cursor.freeCurrentRow();
      }
    }
  }
  
  @Test
  public void testFreeCurrentRow() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 10);
    
    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(new String[]{"sum"}, cursor.getFields());
    assertArrayEquals(new String[]{"testTag", "value"}, cursor.getTags());
    assertEquals(110L, cursor.readLong(0));
    assertEquals(1, table.size());
    
    table.apply(new String[]{"testTag", "value", "testTag2", "value2"}, 10L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value", "testTag2", "value2"}, 99L, CURRENT_TIME);
    assertEquals(2, table.size());
    table.apply(new String[]{"testTag", "value2"}, 2L, CURRENT_TIME - 2*60*1000l);
    table.apply(new String[]{"testTag", "value4"}, 1L, CURRENT_TIME - 1*60*1000l);
    table.apply(new String[]{"testTag", "value5"}, 8L, CURRENT_TIME - 3*60*1000l);
    assertEquals(5, table.size());
    
    String[] tagSet1 = new String[]{"testTag", "value"};
    String[] tagSet2 = new String[]{"testTag", "value", "testTag2", "value2"};
    String[] tagSet3 = new String[]{"testTag", "value2"};
    String[] tagSet4 = new String[]{"testTag", "value3"};
    
    assertEquals(10, table.capacity());
    assertEquals(5, table.size());
    cursor = table.cursor();
    while (cursor.next()) {
      if(cursor.lastUpdated() < CURRENT_TIME) {
        cursor.freeCurrentRow();
      }
    }
    assertEquals(5, table.size());
    table.apply(new String[]{"testTag", "value3"}, 3L, CURRENT_TIME - 2*60*1000l);
    assertEquals(6, table.size());
    
    cursor = table.cursor();
    while (cursor.next()) {
      final int hash = Arrays.hashCode(cursor.getTags());
      if(cursor.lastUpdated() < CURRENT_TIME) {
        cursor.freeCurrentRow();
      } else {
        assertEquals(CURRENT_TIME, cursor.lastUpdated());
      }
      if (hash == Arrays.hashCode(tagSet1)) {
        assertEquals(111L, cursor.readLong(0));
      } else if (hash == Arrays.hashCode(tagSet2)) {
        assertEquals(109L, cursor.readLong(0));
      } else if (hash == Arrays.hashCode(tagSet4)) {
        assertEquals(3L, cursor.readLong(0));
      } else {
        fail("Unknown hashcode");
      }
    }
  }

  @Test
  public void mathAbsReturnsNegative() {
    final BasicCounterAggregator aggregator = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 3);
    Deencapsulation.setField(aggregator, "capacity", Integer.MAX_VALUE);
    new Expectations(aggregator) {{
      aggregator.hashCode((String[]) any);
      result = Integer.MIN_VALUE;
    }};
    assertEquals(0, aggregator.index(new String[]{}, false));
  }

  @Test
  public void growDataAndTagTableTillDefaultMaxLimit() {

    final int maxCapacity = 4096;
    int capacity = maxCapacity - 1;

    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, capacity);
    String[][] tagSets = Deencapsulation.getField(table, "tagSets");

    assertEquals(capacity, table.capacity());
    assertEquals(capacity, tagSets.length);

    for (int i = 0; i < maxCapacity + 2; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
    }

    assertEquals(maxCapacity, table.size());
    assertEquals(maxCapacity, table.capacity());
  }

  @Test
  public void growDataTable() {
    final int requestedCapacity = 128 * 2;
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, requestedCapacity);

    int capacity = table.capacity();
    assertEquals(requestedCapacity, capacity);

    for (int i = 0; i < capacity + 1; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
    }

    assertEquals(capacity + 1, table.size());
    int newCapacity = capacity * 2 + capacity;
    assertEquals(newCapacity, table.capacity());
  }

  @Test
  public void growTagTable() {
    final int requestedCapacity = 128;
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, requestedCapacity);

    String[][] tagSets = Deencapsulation.getField(table, "tagSets");
    assertEquals(requestedCapacity, tagSets.length);

    for (int i = 0; i < requestedCapacity + 1; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
    }

    int oldLength = tagSets.length;
    tagSets = Deencapsulation.getField(table, "tagSets");
    assertEquals(oldLength * 2, tagSets.length);
  }

  @Test
  public void testTagsetsMaxSize() {

    final int maxCapacity = 4096;
    final BasicCounterAggregator table = new BasicCounterAggregator("test", maxCapacity, 128);

    for (int i = 0; i < maxCapacity + 2; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
    }

    assertEquals(maxCapacity, table.size());
    assertEquals(maxCapacity, table.capacity());
  }

  @Test
  public void testTagsetsConcurrent() throws InterruptedException {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", 128);
    final Object lock = new Object();
    synchronized (table) {
      new Thread(() -> {
        for (int i = 0; i < 64; i++) {
          table.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
        }
        synchronized (lock) {
          lock.notify();
        }
        for (int i = 64; i < 128; i++) {
          table.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
        }
      }).start();
      synchronized (lock) {
        lock.wait(500L);
      }
      Thread.sleep(100L);
      for (int i = 0; i < 128; i++) {
        table.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
      }
    }
    assertEquals(128, table.size());
  }

  @Test
  public void testReadLongInvalidFieldIndex() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1L, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertThrows(IndexOutOfBoundsException.class, () -> cursor.readLong(cursor.getFields().length));
  }

  @Test
  public void testReadLongInvalidCursorIndex() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1L, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertThrows(IndexOutOfBoundsException.class, () -> cursor.readLong(0));
  }

  @Test
  public void testReadAndResetLongInvalidFieldIndex() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1L, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertThrows(IndexOutOfBoundsException.class,
        () -> cursor.readAndResetLong(cursor.getFields().length));
  }

  @Test
  public void testReadAndResetLongInvalidCursorIndex() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY,2);
    table.apply(new String[]{}, 1L, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertThrows(IndexOutOfBoundsException.class, () -> cursor.readAndResetLong(0));
  }

  @Test
  public void testGetTagsInvalidCursorIndex() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1L, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertThrows(IndexOutOfBoundsException.class, cursor::getTags);
  }

  @Test
  public void testLastUpdatedInvalidCursorIndex() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1L, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertThrows(IndexOutOfBoundsException.class, cursor::lastUpdated);
  }

  @Test
  public void tableGrowthIsSynchronized() throws InterruptedException {

    final BasicCounterAggregator aggregator = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 2);
    aggregator.apply(new String[]{}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);

    assertEquals(2, aggregator.size());
    assertEquals(2, aggregator.capacity());

    Thread t1 = new Thread(() -> {
      synchronized (aggregator) {
        try {
          Thread.sleep(100);
          aggregator.index(new String[]{"testTag", "value2"}, false);
        } catch (InterruptedException ignored) {
        }
      }
    }, "t1");

    Thread t2 = new Thread(() -> {
      try {
        Thread.sleep(50);
        aggregator.index(new String[]{"testTag", "value3"}, false);
      } catch (InterruptedException ignored) {
      }
      aggregator.index(new String[]{"testTag", "value3"}, false);
    }, "t2");

    t1.start();
    t2.start();

    t1.join();
    t2.join();

    assertEquals(4, aggregator.size());
    assertEquals(6, aggregator.capacity());
  }

  @Test
  public void testConcurrentGrowTableWithMaxCapacity() throws InterruptedException {

    int requestedCapacity = 128;
    int maxCapacity = requestedCapacity * 2;
    final BasicCounterAggregator aggregator = new BasicCounterAggregator("test", maxCapacity,
        requestedCapacity);

    for (int i = 0; i < requestedCapacity; i++) {
      aggregator.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
    }

    Thread t1 = new Thread(() -> {
      synchronized (aggregator) {
        try {
          Thread.sleep(100);
          aggregator.apply(new String[]{"testTag", String.valueOf(128)}, 1L, CURRENT_TIME);
        } catch (InterruptedException ignored) {
        }
      }
    }, "t1");

    Thread t2 = new Thread(() -> {
      aggregator.apply(new String[]{"testTag", String.valueOf(129)}, 1L, CURRENT_TIME);
    }, "t2");

    t1.start();
    Thread.sleep(5);
    t2.start();

    t1.join();
    t2.join();

    assertEquals(130, aggregator.size());
    assertEquals(maxCapacity, aggregator.capacity()); // capped at the max capacity.

    for (int i = 130; i < maxCapacity + 2; i++) {
      aggregator.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
    }

    assertEquals(maxCapacity, aggregator.size()); // silently dropped 2 data points after the max capacity
    assertEquals(maxCapacity, aggregator.capacity()); // still capped at the max capacity.
  }

  @Test
  public void tagTableGrowthIsSynchronized() throws InterruptedException {
    int requestedCapacity = 131072;
    final BasicCounterAggregator aggregator = new BasicCounterAggregator("test", 1_048_576,
        requestedCapacity);

    for (int i = 0; i < requestedCapacity * 2; i++) {
      aggregator.apply(new String[]{"testTag", String.valueOf(i)}, 1L, CURRENT_TIME);
    }

    String[][] tagSets = Deencapsulation.getField(aggregator, "tagSets");
    assertEquals(requestedCapacity * 2, tagSets.length);

    Thread t1 = new Thread(() -> {
      synchronized (aggregator) {
        try {
          Thread.sleep(100);
          aggregator.apply(new String[]{"testTag", String.valueOf(262145)}, 1L, CURRENT_TIME);
          aggregator.apply(new String[]{"testTag", String.valueOf(262147)}, 1L, CURRENT_TIME);
        } catch (InterruptedException ignored) {
        }
      }
    }, "t1");

    Thread t2 = new Thread(() -> {
      aggregator.apply(new String[]{"testTag", String.valueOf(262146)}, 1L, CURRENT_TIME);
      aggregator.apply(new String[]{"testTag", String.valueOf(262148)}, 1L, CURRENT_TIME);
      aggregator.apply(new String[]{"testTag", String.valueOf(262149)}, 1L, CURRENT_TIME);
    }, "t2");
    
    t1.start();
    Thread.sleep(5);
    t2.start();

    t1.join();
    t2.join();

    int oldLength = tagSets.length;
    tagSets = Deencapsulation.getField(aggregator, "tagSets");
    assertEquals(oldLength + requestedCapacity, tagSets.length);
    assertEquals(262149, aggregator.size()); // ensures nothing is dropped.
  }

  @Test
  public void testConcurrentSlotReservation() throws InterruptedException {

    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 5);

    Thread t1 = new Thread(() -> {

      synchronized (table) {
        try {
          Thread.sleep(100);
          table.index(new String[]{"testTag", "value1"}, false);
          table.index(new String[]{"testTag", "value8"}, false);
        } catch (InterruptedException ignored) {
        }
      }
    });

    Thread t2 = new Thread(() -> {
      try {
        Thread.sleep(50);
      } catch (InterruptedException ignored) {
      }
      table.index(new String[]{"testTag", "value16"}, false);
      table.index(new String[]{"testTag", "value23"}, false);
    });

    t1.start();
    t2.start();

    t1.join();
    t2.join();

    assertEquals(4, table.size());
    assertEquals(5, table.capacity());
  }

  @Test
  public void createWithSizeZero() {

    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 0);
    assertNotNull(table);
    assertEquals(16, table.capacity());

    table.apply(new String[]{}, 1L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);

    table.apply(new String[]{"testTag", "value2"}, 1L, CURRENT_TIME);
  }

  @Test
  public void createWithNegativeSize() {
    assertThrows(IllegalArgumentException.class, () -> new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, -1));
  }

  @Test
  public void readsFromNextTableWhenCurrentSlotIsEmpty() {

    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 3);
    String[] tagSet1 = new String[]{"key1", "value1"};
    String[] tagSet2 = new String[]{"key2", "value2"};
    String[] tagSet3 = new String[]{"key3", "value3"};
    String[] tagSet4 = new String[]{"key4", "value4"};
    String[] tagSet5 = new String[]{"key5", "value5"};

    table.apply(tagSet1, 1, CURRENT_TIME);
    table.apply(tagSet2, 1, CURRENT_TIME);
    table.apply(tagSet3, 1, CURRENT_TIME);
    table.apply(tagSet4, 1, CURRENT_TIME);
    table.apply(tagSet5, 1, CURRENT_TIME);

    Cursor cursor = table.cursor();
    while (cursor.next()) {
      assertEquals(1L, cursor.readLong(0));
    }

    List<long[]> tables = Deencapsulation.getField(table, "tables");
    List<AtomicInteger> recordCounts = Deencapsulation.getField(table, "recordCounts");
    List<Integer> tableCapacities = Deencapsulation.getField(table, "tableCapacities");
    assertEquals(2, tables.size());
    assertEquals(2, recordCounts.size());
    assertEquals(3, recordCounts.get(0).get());
    assertEquals(2, recordCounts.get(1).get());
    assertEquals(2, tableCapacities.size());
    assertEquals(3, tableCapacities.get(0).intValue());
    assertEquals(6, tableCapacities.get(1).intValue());
  }

  @Test
  public void readingByInvalidTagSets() {
    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 3);
    String[] tagSet1 = new String[]{"key1", "value1"};
    String[] tagSet2 = new String[]{"key2", "value2"};

    table.apply(tagSet1, 1, CURRENT_TIME);

    Cursor cursor = table.cursor();
    Deencapsulation.setField(cursor, "tagSets", new String[][]{tagSet2});

    assertFalse(cursor.next());
  }

  @Test
  void testDefaultCardinality() {
    BasicCounterAggregator aggregator = new BasicCounterAggregator("test");
    int maxCapacity = Deencapsulation.getField(aggregator, "maxCapacity");
    assertEquals(128, aggregator.capacity());
    assertEquals(4096, maxCapacity);
  }
}
