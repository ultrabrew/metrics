// Copyright 2019, Oath Inc.
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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import mockit.Deencapsulation;
import mockit.Expectations;
import org.junit.jupiter.api.Test;

public class BasicHistogramAggregatorTest {

  private long CURRENT_TIME = System.currentTimeMillis();

  @Test
  void testAggregation() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket);

    String[] tagset = {"testTag", "value"};
    table.apply(tagset, -1, CURRENT_TIME);
    table.apply(tagset, 0, CURRENT_TIME);
    table.apply(tagset, 1L, CURRENT_TIME);
    table.apply(tagset, 10, CURRENT_TIME);
    table.apply(tagset, 50, CURRENT_TIME);
    table.apply(tagset, 100, CURRENT_TIME);
    table.apply(tagset, 150, CURRENT_TIME);
    table.apply(tagset, 101, CURRENT_TIME);

    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(
        new String[]{"count", "sum", "min", "max", "lastValue", "0_10", "10_100", "overflow", "underflow"},
        cursor.getFields());
    assertArrayEquals(tagset, cursor.getTags());
    assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
    assertEquals(8, cursor.readLong(0)); // count
    assertEquals(411, cursor.readLong(1)); // sum
    assertEquals(-1, cursor.readLong(2)); // min
    assertEquals(150, cursor.readLong(3)); // max
    assertEquals(101, cursor.readLong(4)); // lastValue
    assertEquals(2, cursor.readLong(5)); // [0,10)
    assertEquals(2, cursor.readLong(6)); // [10,100)
    assertEquals(3, cursor.readLong(7)); // overflow
    assertEquals(1, cursor.readLong(8)); // underflow

    assertEquals(1, table.size());
    assertEquals(128, table.capacity());
  }

  @Test
  void testMinAggregation() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket);

    String[] tagset = {"testTag", "value"};
    table.apply(tagset, 15, CURRENT_TIME);
    table.apply(tagset, 49, CURRENT_TIME);
    table.apply(tagset, 75, CURRENT_TIME);
    table.apply(tagset, 99, CURRENT_TIME);
    table.apply(tagset, 100, CURRENT_TIME);

    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertEquals(15, cursor.readLong(2)); //min
  }

  @Test
  public void testReadAndReset() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket);

    table.apply(new String[]{"testTag", "value"}, -1, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 0, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 1, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 50, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 100, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 101, CURRENT_TIME);

    Cursor cursor = table.cursor();
    assertTrue(cursor.next());

    assertEquals(7, cursor.readAndResetLong(0)); // count
    assertEquals(261, cursor.readAndResetLong(1)); // sum
    assertEquals(-1, cursor.readAndResetLong(2)); // min
    assertEquals(101, cursor.readAndResetLong(3)); // max
    assertEquals(101, cursor.readAndResetLong(4)); // lastValue
    assertEquals(2, cursor.readAndResetLong(5)); // [0,10)
    assertEquals(2, cursor.readAndResetLong(6)); // [10,100)
    assertEquals(2, cursor.readAndResetLong(7)); // overflow
    assertEquals(1, cursor.readAndResetLong(8)); // underflow

    // Assert that identity is set
    assertEquals(0L, cursor.readLong(0)); // count
    assertEquals(0L, cursor.readLong(1)); // sum
    assertEquals(Long.MAX_VALUE, cursor.readLong(2)); // min
    assertEquals(Long.MIN_VALUE, cursor.readLong(3)); // max
    assertEquals(0L, cursor.readLong(4)); // lastValue
    assertEquals(0, cursor.readLong(5)); // underflow
    assertEquals(0, cursor.readLong(6)); // [0,10)
    assertEquals(0, cursor.readLong(7)); // [10,100)
    assertEquals(0, cursor.readLong(8)); // overflow
  }

  @Test
  public void testGrowTable() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 29, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value2"}, 300, CURRENT_TIME);

    assertEquals(3, table.size());
    assertEquals(6, table.capacity());
    // next prime from 2 is 3
    table.apply(new String[]{"testTag", "value3"}, 1, CURRENT_TIME);

    assertEquals(4, table.size());
    assertEquals(6, table.capacity());
  }

  @Test
  public void testGrowTableWithMaxCapacity() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, 3, 1);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 1, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value2"}, 1, CURRENT_TIME);

    // Silently ignored, over capacity
    table.apply(new String[]{"testTag", "value3"}, 1, CURRENT_TIME);

    assertEquals(3, table.size());
    assertEquals(3, table.capacity()); // caped at the max capacity.
  }

  @Test
  public void mathAbsReturnsNegative() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator aggregator = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 3);
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

    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, capacity);
    String[][] tagSets = Deencapsulation.getField(table, "tagSets");

    assertEquals(capacity, table.capacity());
    assertEquals(capacity, tagSets.length);

    for (int i = 0; i < maxCapacity + 2; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
    }

    assertEquals(maxCapacity, table.size());
    assertEquals(maxCapacity, table.capacity());
  }

  @Test
  public void growDataTable() {
    final int requestedCapacity = 128 * 2;
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY,
        requestedCapacity);

    int capacity = table.capacity();
    assertEquals(requestedCapacity, capacity);

    for (int i = 0; i < capacity + 1; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
    }

    assertEquals(capacity + 1, table.size());
    int newCapacity = capacity * 2 + capacity;
    assertEquals(newCapacity, table.capacity());
  }

  @Test
  public void growTagTable() {
    final int requestedCapacity = 128;
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY,
        requestedCapacity);

    String[][] tagSets = Deencapsulation.getField(table, "tagSets");
    assertEquals(requestedCapacity, tagSets.length);

    for (int i = 0; i < requestedCapacity + 1; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
    }

    int oldLength = tagSets.length;
    tagSets = Deencapsulation.getField(table, "tagSets");
    assertEquals(oldLength * 2, tagSets.length);
  }

  @Test
  public void testTagsetsMaxSize() {

    final int maxCapacity = 4096;
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, maxCapacity,
        128
    );

    for (int i = 0; i < maxCapacity + 2; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
    }

    assertEquals(maxCapacity, table.size());
    assertEquals(maxCapacity, table.capacity());
  }

  @Test
  public void testTagsetsConcurrent() throws InterruptedException {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, 128);
    final Object lock = new Object();
    synchronized (table) {
      new Thread(() -> {
        for (int i = 0; i < 64; i++) {
          table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
        }
        synchronized (lock) {
          lock.notify();
        }
        for (int i = 64; i < 128; i++) {
          table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
        }
      }).start();
      synchronized (lock) {
        lock.wait(500L);
      }
      Thread.sleep(100L);
      for (int i = 0; i < 128; i++) {
        table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
      }
    }
    assertEquals(128, table.size());
  }

  @Test
  public void testReadLongInvalidFieldIndex() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertThrows(IndexOutOfBoundsException.class, () -> cursor.readLong(cursor.getFields().length));
  }

  @Test
  public void testReadLongInvalidCursorIndex() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertThrows(IndexOutOfBoundsException.class, () -> cursor.readLong(0));
  }

  @Test
  public void testReadAndResetLongInvalidFieldIndex() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertThrows(IndexOutOfBoundsException.class,
        () -> cursor.readAndResetLong(cursor.getFields().length));
  }

  @Test
  public void testReadAndResetLongInvalidCursorIndex() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertThrows(IndexOutOfBoundsException.class, () -> cursor.readAndResetLong(0));
  }

  @Test
  public void testGetTagsInvalidCursorIndex() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertThrows(IndexOutOfBoundsException.class, cursor::getTags);
  }

  @Test
  public void testLastUpdatedInvalidCursorIndex() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    final Cursor cursor = table.cursor();
    assertThrows(IndexOutOfBoundsException.class, cursor::lastUpdated);
  }

  @Test
  public void tableGrowthIsSynchronized() throws InterruptedException {

    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 2);
    table.apply(new String[]{}, 1, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 1, CURRENT_TIME);

    assertEquals(2, table.size());
    assertEquals(2, table.capacity());

    Thread t1 = new Thread(() -> {
      synchronized (table) {
        try {
          Thread.sleep(100);
          table.index(new String[]{"testTag", "value2"}, false);
        } catch (InterruptedException ignored) {
        }
      }
    }, "t1");

    Thread t2 = new Thread(() -> {
      try {
        Thread.sleep(50);
        table.index(new String[]{"testTag", "value3"}, false);
      } catch (InterruptedException ignored) {
      }
      table.index(new String[]{"testTag", "value3"}, false);
    }, "t2");

    t1.start();
    t2.start();

    t1.join();
    t2.join();

    assertEquals(4, table.size());
    assertEquals(6, table.capacity());
  }

  @Test
  public void testConcurrentGrowTableWithMaxCapacity() throws InterruptedException {

    int requestedCapacity = 128;
    int maxCapacity = requestedCapacity * 2;
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket,
        maxCapacity, requestedCapacity);

    for (int i = 0; i < requestedCapacity; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
    }

    Thread t1 = new Thread(() -> {
      synchronized (table) {
        try {
          Thread.sleep(100);
          table.apply(new String[]{"testTag", String.valueOf(128)}, 1, CURRENT_TIME);
        } catch (InterruptedException ignored) {
        }
      }
    }, "t1");

    Thread t2 = new Thread(() -> {
      table.apply(new String[]{"testTag", String.valueOf(129)}, 1, CURRENT_TIME);
    }, "t2");

    t1.start();
    Thread.sleep(5);
    t2.start();

    t1.join();
    t2.join();

    assertEquals(130, table.size());
    assertEquals(maxCapacity, table.capacity()); // capped at the max capacity.

    for (int i = 130; i < maxCapacity + 2; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
    }

    assertEquals(maxCapacity,
        table.size()); // silently dropped 2 data points after the max capacity
    assertEquals(maxCapacity, table.capacity()); // still capped at the max capacity.
  }

  @Test
  public void tagTableGrowthIsSynchronized() throws InterruptedException {

    int requestedCapacity = 131072;
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket,
        1_048_576, requestedCapacity);

    for (int i = 0; i < requestedCapacity * 2; i++) {
      table.apply(new String[]{"testTag", String.valueOf(i)}, 1, CURRENT_TIME);
    }

    String[][] tagSets = Deencapsulation.getField(table, "tagSets");
    assertEquals(requestedCapacity * 2, tagSets.length);

    Thread t1 = new Thread(() -> {
      synchronized (table) {
        try {
          Thread.sleep(100);
          table.apply(new String[]{"testTag", String.valueOf(262145)}, 1, CURRENT_TIME);
        } catch (InterruptedException ignored) {
        }
      }
    }, "t1");

    Thread t2 = new Thread(() -> {
      table.apply(new String[]{"testTag", String.valueOf(262146)}, 1, CURRENT_TIME);
    }, "t2");

    t1.start();
    Thread.sleep(5);
    t2.start();

    t1.join();
    t2.join();

    int oldLength = tagSets.length;
    tagSets = Deencapsulation.getField(table, "tagSets");
    assertEquals(oldLength + requestedCapacity, tagSets.length);
    assertEquals(262146, table.size()); // ensures nothing is dropped.
  }

  @Test
  public void testConcurrentSlotReservation() throws InterruptedException {

    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 5);

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

    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 0);
    assertNotNull(table);
    assertEquals(16, table.capacity());

    table.apply(new String[]{}, 1, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 1, CURRENT_TIME);

    table.apply(new String[]{"testTag", "value2"}, 1, CURRENT_TIME);
  }

  @Test
  public void createWithNegativeSize() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    assertThrows(IllegalArgumentException.class,
        () -> new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, -1));
  }

  @Test
  public void testInvalidMaxCapacity() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    assertThrows(IllegalArgumentException.class,
        () -> new BasicHistogramAggregator("test", bucket, 9, 10));
  }

  @Test
  public void readsFromNextTableWhenCurrentSlotIsEmpty() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 3);
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
      assertEquals(1, cursor.readLong(0));
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
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 3);
    String[] tagSet1 = new String[]{"key1", "value1"};
    String[] tagSet2 = new String[]{"key2", "value2"};

    table.apply(tagSet1, 1, CURRENT_TIME);

    Cursor cursor = table.cursor();
    Deencapsulation.setField(cursor, "tagSets", new String[][]{tagSet2});

    assertFalse(cursor.next());
  }

  @Test
  void doubleValuesAreNotSupported() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket, DEFAULT_MAX_CARDINALITY, 3);
    String[] tagSet1 = new String[]{"key1", "value1"};
    String[] tagSet2 = new String[]{"key2", "value2"};

    table.apply(tagSet1, 1, CURRENT_TIME);

    Cursor cursor = table.cursor();
    cursor.next();
    assertThrows(UnsupportedOperationException.class,
        () -> cursor.readDouble(0));
    assertThrows(UnsupportedOperationException.class,
        () -> cursor.readAndResetDouble(0));
  }

  @Test
  void testSortedCursor() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket);

    final String[] tagset1 = new String[]{"host", "h1", "colo", "c1"};
    final String[] tagset2 = {"host", "h1"};

    table.apply(tagset1, -1, CURRENT_TIME);
    table.apply(tagset1, 0, CURRENT_TIME);
    table.apply(tagset1, 1L, CURRENT_TIME);
    table.apply(tagset1, 10, CURRENT_TIME);
    table.apply(tagset2, 50, CURRENT_TIME);
    table.apply(tagset2, 100, CURRENT_TIME);
    table.apply(tagset2, 101, CURRENT_TIME);
    table.apply(tagset2, 150, CURRENT_TIME);

    Cursor cursor = table.sortedCursor();
    assertTrue(cursor.next());
    assertArrayEquals(tagset2, cursor.getTags());
    assertEquals("test", cursor.getMetricId());

    assertTrue(cursor.next());
    assertArrayEquals(tagset1, cursor.getTags());
    assertEquals("test", cursor.getMetricId());
  }

  @Test
  public void testTagSetReuse() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 100});
    final BasicHistogramAggregator table = new BasicHistogramAggregator("test", bucket);

    String[] tagset = {"testTag", "value"};
    String[] tagset1 = Arrays.copyOf(tagset, tagset.length);
    table.apply(tagset, -1, CURRENT_TIME);
    table.apply(tagset, 0, CURRENT_TIME);
    table.apply(tagset, 1L, CURRENT_TIME);
    table.apply(tagset, 10, CURRENT_TIME);
    table.apply(tagset, 50, CURRENT_TIME);
    table.apply(tagset, 100, CURRENT_TIME);
    table.apply(tagset, 150, CURRENT_TIME);
    table.apply(tagset, 101, CURRENT_TIME);

    tagset[1] = "value1";
    String[] tagset2 = Arrays.copyOf(tagset, tagset.length);
    table.apply(tagset, 0, CURRENT_TIME);
    table.apply(tagset, 1L, CURRENT_TIME);
    table.apply(tagset, 20, CURRENT_TIME);
    table.apply(tagset, 90, CURRENT_TIME);
    table.apply(tagset, 100, CURRENT_TIME);
    table.apply(tagset, 250, CURRENT_TIME);
    table.apply(tagset, 20, CURRENT_TIME);

    assertEquals(2, table.size());

    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(
            new String[]{"count", "sum", "min", "max", "lastValue", "0_10", "10_100", "overflow", "underflow"},
            cursor.getFields());
    assertArrayEquals(tagset1, cursor.getTags());
    assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
    assertEquals(8, cursor.readLong(0)); // count
    assertEquals(411, cursor.readLong(1)); // sum
    assertEquals(-1, cursor.readLong(2)); // min
    assertEquals(150, cursor.readLong(3)); // max
    assertEquals(101, cursor.readLong(4)); // lastValue
    assertEquals(2, cursor.readLong(5)); // [0,10)
    assertEquals(2, cursor.readLong(6)); // [10,100)
    assertEquals(3, cursor.readLong(7)); // overflow
    assertEquals(1, cursor.readLong(8)); // underflow

    assertTrue(cursor.next());
    assertArrayEquals(tagset2, cursor.getTags());
    assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
    assertEquals(7, cursor.readLong(0)); // count
    assertEquals(481, cursor.readLong(1)); // sum
    assertEquals(0, cursor.readLong(2)); // min
    assertEquals(250, cursor.readLong(3)); // max
    assertEquals(20, cursor.readLong(4)); // lastValue
    assertEquals(2, cursor.readLong(5)); // [0,10)
    assertEquals(3, cursor.readLong(6)); // [10,100)
    assertEquals(2, cursor.readLong(7)); // overflow
    assertEquals(0, cursor.readLong(8)); // underflow

  }
}
