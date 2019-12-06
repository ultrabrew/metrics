// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import mockit.Deencapsulation;
import org.junit.jupiter.api.Test;

public class BasicGaugeAggregatorTest {

  private long CURRENT_TIME = System.currentTimeMillis();

  @Test
  public void testAggregation() {
    final BasicGaugeAggregator table = new BasicGaugeAggregator("test", DEFAULT_MAX_CARDINALITY, 10);

    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(new String[]{"count", "sum", "min", "max", "lastValue"}, cursor.getFields());
    assertArrayEquals(new String[]{"testTag", "value"}, cursor.getTags());
    assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
    assertEquals(2L, cursor.readLong(0)); // count
    assertEquals(110L, cursor.readLong(1)); // sum
    assertEquals(10L, cursor.readLong(2)); // min
    assertEquals(100L, cursor.readLong(3)); // max
    assertEquals(10L, cursor.readLong(4)); // lastValue
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

    cursor = table.cursor();
    while (cursor.next()) {
      final int hash = Arrays.hashCode(cursor.getTags());
      assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
      if (hash == Arrays.hashCode(tagSet1)) {
        assertEquals(3L, cursor.readLong(0)); // count
        assertEquals(111L, cursor.readLong(1)); // sum
        assertEquals(1L, cursor.readLong(2)); // min
        assertEquals(100L, cursor.readLong(3)); // max
        assertEquals(1L, cursor.readLong(4)); // lastValue
      } else if (hash == Arrays.hashCode(tagSet2)) {
        assertEquals(2L, cursor.readLong(0)); // count
        assertEquals(109L, cursor.readLong(1)); // sum
        assertEquals(10L, cursor.readLong(2)); // min
        assertEquals(99L, cursor.readLong(3)); // max
        assertEquals(99L, cursor.readLong(4)); // lastValue
      } else if (hash == Arrays.hashCode(tagSet3)) {
        assertEquals(1L, cursor.readLong(0)); // count
        assertEquals(2L, cursor.readLong(1)); // sum
        assertEquals(2L, cursor.readLong(2)); // min
        assertEquals(2L, cursor.readLong(3)); // max
        assertEquals(2L, cursor.readLong(4)); // lastValue
      } else {
        fail("Unknown hashcode");
      }
    }
  }

  @Test
  public void testReadAndReset() {
    final BasicGaugeAggregator table = new BasicGaugeAggregator("test", DEFAULT_MAX_CARDINALITY, 10);

    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    assertTrue(cursor.next());

    assertEquals(2L, cursor.readAndResetLong(0)); // count
    assertEquals(110L, cursor.readAndResetLong(1)); // sum
    assertEquals(10L, cursor.readAndResetLong(2)); // min
    assertEquals(100L, cursor.readAndResetLong(3)); // max
    assertEquals(10L, cursor.readAndResetLong(4)); // lastValue

    // Assert that identity is set
    assertEquals(0L, cursor.readLong(0)); // count
    assertEquals(0L, cursor.readLong(1)); // sum
    assertEquals(Long.MAX_VALUE, cursor.readLong(2)); // min
    assertEquals(Long.MIN_VALUE, cursor.readLong(3)); // max
    assertEquals(0L, cursor.readLong(4)); // lastValue
  }
  
  @Test
  public void testFreeCurrentRowAndPurge() {
    final BasicGaugeAggregator table = new BasicGaugeAggregator("test", DEFAULT_MAX_CARDINALITY, 3);

    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(new String[]{"count", "sum", "min", "max", "lastValue"}, cursor.getFields());
    assertArrayEquals(new String[]{"testTag", "value"}, cursor.getTags());
    assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
    assertEquals(2L, cursor.readLong(0)); // count
    assertEquals(110L, cursor.readLong(1)); // sum
    assertEquals(10L, cursor.readLong(2)); // min
    assertEquals(100L, cursor.readLong(3)); // max
    assertEquals(10L, cursor.readLong(4)); // lastValue
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
        assertEquals(3L, cursor.readAndResetLong(0)); // count
        assertEquals(111L, cursor.readAndResetLong(1)); // sum
        assertEquals(1L, cursor.readAndResetLong(2)); // min
        assertEquals(100L, cursor.readAndResetLong(3)); // max
        assertEquals(1L, cursor.readAndResetLong(4)); // lastValue
      } else if (hash == Arrays.hashCode(tagSet2)) {
        assertEquals(2L, cursor.readLong(0));
        assertEquals(109L, cursor.readLong(1));
        assertEquals(10L, cursor.readLong(2));
        assertEquals(99L, cursor.readLong(3));
        assertEquals(99L, cursor.readLong(4));
      } else if (hash == Arrays.hashCode(tagSet3)) {
        assertEquals(1L, cursor.readLong(0));
        assertEquals(3L, cursor.readLong(1));
        assertEquals(3L, cursor.readLong(2));
        assertEquals(3L, cursor.readLong(3));
        assertEquals(3L, cursor.readLong(4));
      } else {
        fail("Unknown hashcode");
      }
    }
  }
  
  @Test
  public void testFreeCurrentRow() {
 
    final BasicGaugeAggregator table = new BasicGaugeAggregator("test", DEFAULT_MAX_CARDINALITY, 10);

    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(new String[]{"count", "sum", "min", "max", "lastValue"}, cursor.getFields());
    assertArrayEquals(new String[]{"testTag", "value"}, cursor.getTags());
    assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
    assertEquals(2L, cursor.readLong(0)); // count
    assertEquals(110L, cursor.readLong(1)); // sum
    assertEquals(10L, cursor.readLong(2)); // min
    assertEquals(100L, cursor.readLong(3)); // max
    assertEquals(10L, cursor.readLong(4)); // lastValue
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
    String[] tagSet3 = new String[]{"testTag", "value3"};
    
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
        assertEquals(3L, cursor.readAndResetLong(0)); // count
        assertEquals(111L, cursor.readAndResetLong(1)); // sum
        assertEquals(1L, cursor.readAndResetLong(2)); // min
        assertEquals(100L, cursor.readAndResetLong(3)); // max
        assertEquals(1L, cursor.readAndResetLong(4)); // lastValue
      } else if (hash == Arrays.hashCode(tagSet2)) {
        assertEquals(2L, cursor.readLong(0));
        assertEquals(109L, cursor.readLong(1));
        assertEquals(10L, cursor.readLong(2));
        assertEquals(99L, cursor.readLong(3));
        assertEquals(99L, cursor.readLong(4));
      } else if (hash == Arrays.hashCode(tagSet3)) {
        assertEquals(1L, cursor.readLong(0));
        assertEquals(3L, cursor.readLong(1));
        assertEquals(3L, cursor.readLong(2));
        assertEquals(3L, cursor.readLong(3));
        assertEquals(3L, cursor.readLong(4));
      } else {
        fail("Unknown hashcode");
      }
    }
  }

  @Test
  public void testGrowTableWithMaxCapacity() {
    final BasicGaugeAggregator aggregator = new BasicGaugeAggregator("test", 3, 1);
    aggregator.apply(new String[]{}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value2"}, 1L, CURRENT_TIME);

    // Should be silently ignored, over capacity
    aggregator.apply(new String[]{"testTag", "value3"}, 1L, CURRENT_TIME);

    assertEquals(3, aggregator.size());
    assertEquals(3, aggregator.capacity()); // caped at the max capacity.
  }

  @Test
  void testDefaultCardinality() {
    BasicGaugeAggregator aggregator = new BasicGaugeAggregator("test");
    int maxCapacity = Deencapsulation.getField(aggregator, "maxCapacity");
    assertEquals(128, aggregator.capacity());
    assertEquals(4096, maxCapacity);
  }
}
