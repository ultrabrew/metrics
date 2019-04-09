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
import org.junit.jupiter.api.Test;

public class BasicGaugeDoubleAggregatorTest {

  private long CURRENT_TIME = System.currentTimeMillis();

  @Test
  public void testAggregation() {

    double d1 = 10.19;
    double d2 = 5179.0003;
    double d3 = 59.5003;
    double d4 = 100.3947;

    long l1 = Double.doubleToRawLongBits(d1);
    long l2 = Double.doubleToRawLongBits(d2);
    long l3 = Double.doubleToRawLongBits(d3);
    long l4 = Double.doubleToRawLongBits(d4);

    final BasicGaugeDoubleAggregator aggregator = new BasicGaugeDoubleAggregator("test", DEFAULT_MAX_CARDINALITY, 10);

    aggregator.apply(new String[]{"testTag", "value"}, l1, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value"}, l2, CURRENT_TIME);
    Cursor cursor = aggregator.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(new String[]{"count", "sum", "min", "max", "lastValue"},
        cursor.getFields());
    assertArrayEquals(new String[]{"testTag", "value"}, cursor.getTags());
    assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
    assertEquals(2L, cursor.readLong(0)); // count
    assertEquals(d1 + d2, cursor.readDouble(1), 0.0001); // sum
    assertEquals(d1, cursor.readDouble(2), 0.0001); // min
    assertEquals(d2, cursor.readDouble(3), 0.0001); // max
    assertEquals(d2, cursor.readDouble(4), 0.0001); // lastValue
    assertEquals(1, aggregator.size());

    aggregator.apply(new String[]{"testTag", "value", "testTag2", "value2"}, l2, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value"}, l3, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value", "testTag2", "value2"}, l4, CURRENT_TIME);
    assertEquals(2, aggregator.size());

    aggregator.apply(new String[]{"testTag", "value2"}, l1, CURRENT_TIME);
    assertEquals(3, aggregator.size());

    String[] tagSet1 = new String[]{"testTag", "value"};
    String[] tagSet2 = new String[]{"testTag", "value", "testTag2", "value2"};
    String[] tagSet3 = new String[]{"testTag", "value2"};

    assertEquals(10, aggregator.capacity());

    cursor = aggregator.cursor();
    while (cursor.next()) {
      final int hash = Arrays.hashCode(cursor.getTags());
      assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
      if (hash == Arrays.hashCode(tagSet1)) {
        assertEquals(3L, cursor.readLong(0)); // count
        assertEquals(d1 + d2 + d3, cursor.readDouble(1), 0.0001); // sum
        assertEquals(d1, cursor.readDouble(2), 0.0001); // min
        assertEquals(d2, cursor.readDouble(3), 0.0001); // max
        assertEquals(d3, cursor.readDouble(4), 0.0001); // lastValue
      } else if (hash == Arrays.hashCode(tagSet2)) {
        assertEquals(2L, cursor.readLong(0)); // count
        assertEquals(d2 + d4, cursor.readDouble(1), 0.0001); // sum
        assertEquals(d4, cursor.readDouble(2), 0.0001); // min
        assertEquals(d2, cursor.readDouble(3), 0.0001); // max
        assertEquals(d4, cursor.readDouble(4), 0.0001); // lastValue
      } else if (hash == Arrays.hashCode(tagSet3)) {
        assertEquals(1L, cursor.readLong(0)); // count
        assertEquals(d1, cursor.readDouble(1), 0.0001); // sum
        assertEquals(d1, cursor.readDouble(2), 0.0001); // min
        assertEquals(d1, cursor.readDouble(3), 0.0001); // max
        assertEquals(d1, cursor.readDouble(4), 0.0001); // lastValue
      } else {
        fail("Unknown hashcode");
      }
    }
  }

  @Test
  public void testReadAndReset() {

    double d1 = 5179.0003;
    double d2 = 100.3947;

    long l1 = Double.doubleToRawLongBits(d1);
    long l2 = Double.doubleToRawLongBits(d2);

    final BasicGaugeDoubleAggregator aggregator = new BasicGaugeDoubleAggregator("test");

    aggregator.apply(new String[]{"testTag", "value"}, l1, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value"}, l2, CURRENT_TIME);
    Cursor cursor = aggregator.cursor();
    assertTrue(cursor.next());

    assertEquals(2L, cursor.readAndResetLong(0)); // count
    assertEquals(d1 + d2, cursor.readAndResetDouble(1), 0.0001); // sum
    assertEquals(d2, cursor.readAndResetDouble(2), 0.0001); // min
    assertEquals(d1, cursor.readAndResetDouble(3), 0.0001); // max
    assertEquals(d2, cursor.readAndResetDouble(4), 0.0001); // lastValue

    // Assert that identity is set
    assertEquals(0L, cursor.readLong(0)); // count
    assertEquals(0L, cursor.readDouble(1), 0.0001); // sum
    assertEquals(Double.NaN, cursor.readDouble(2), 0.0001); // min
    assertEquals(-0.0, cursor.readDouble(3), 0.0001); // max
    assertEquals(0.0, cursor.readDouble(4), 0.0001); // lastValue
  }

  @Test
  public void setLongValue() {

    double d = 10.19;

    long l = Double.doubleToRawLongBits(d);

    final BasicGaugeDoubleAggregator aggregator = new BasicGaugeDoubleAggregator("test");

    aggregator.apply(new String[]{"testTag", "value"}, l, CURRENT_TIME);
    Cursor cursor = aggregator.cursor();
    assertTrue(cursor.next());

    assertEquals(1L, cursor.readLong(0)); // count
    assertEquals(d, cursor.readAndResetDouble(1), 0.0001); // sum
    assertEquals(d, cursor.readAndResetDouble(2), 0.0001); // min
    assertEquals(d, cursor.readAndResetDouble(3), 0.0001); // max
    assertEquals(d, cursor.readAndResetDouble(4), 0.0001); //lastValue
  }

  @Test
  public void testGrowTableWithMaxCapacity() {
    final BasicGaugeDoubleAggregator aggregator = new BasicGaugeDoubleAggregator("test", 3, 1);
    aggregator.apply(new String[]{}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value"}, 1L, CURRENT_TIME);
    aggregator.apply(new String[]{"testTag", "value2"}, 1L, CURRENT_TIME);

    // Should be silently ignored, over capacity
    aggregator.apply(new String[]{"testTag", "value3"}, 1L, CURRENT_TIME);

    assertEquals(3, aggregator.size());
    assertEquals(3, aggregator.capacity()); // caped at the max capacity.
  }
}
