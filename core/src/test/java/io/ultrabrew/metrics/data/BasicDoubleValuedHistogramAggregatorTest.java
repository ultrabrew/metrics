// Copyright 2021, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BasicDoubleValuedHistogramAggregatorTest {

  private long CURRENT_TIME = System.currentTimeMillis();

  @Test
  void testAggregation() {
    DoubleValuedDistributionBucket bucket =
        new DoubleValuedDistributionBucket(new double[] {0.5, 10.0, 100.0});
    final BasicDoubleValuedHistogramAggregator table =
        new BasicDoubleValuedHistogramAggregator("test", bucket);

    double d1 = -1;
    double d2 = 0.0;
    double d3 = 0.25;
    double d4 = 0.5;
    double d5 = 1.0;
    double d6 = 5.0;
    double d7 = 10.0;
    double d8 = 50;
    double d9 = 100.0;
    double d10 = 200.0;

    long l1 = Double.doubleToRawLongBits(d1);
    long l2 = Double.doubleToRawLongBits(d2);
    long l3 = Double.doubleToRawLongBits(d3);
    long l4 = Double.doubleToRawLongBits(d4);
    long l5 = Double.doubleToRawLongBits(d5);
    long l6 = Double.doubleToRawLongBits(d6);
    long l7 = Double.doubleToRawLongBits(d7);
    long l8 = Double.doubleToRawLongBits(d8);
    long l9 = Double.doubleToRawLongBits(d9);
    long l10 = Double.doubleToRawLongBits(d10);

    String[] tagset = {"testTag", "value"};
    table.apply(tagset, l1, CURRENT_TIME);
    table.apply(tagset, l2, CURRENT_TIME);
    table.apply(tagset, l3, CURRENT_TIME);
    table.apply(tagset, l4, CURRENT_TIME);
    table.apply(tagset, l5, CURRENT_TIME);
    table.apply(tagset, l6, CURRENT_TIME);
    table.apply(tagset, l7, CURRENT_TIME);
    table.apply(tagset, l8, CURRENT_TIME);
    table.apply(tagset, l9, CURRENT_TIME);
    table.apply(tagset, l10, CURRENT_TIME);

    Cursor cursor = table.cursor();
    assertTrue(cursor.next());
    assertArrayEquals(
        new String[] {
          "count",
          "sum",
          "min",
          "max",
          "lastValue",
          "0.5_10.0",
          "10.0_100.0",
          "overflow",
          "underflow"
        },
        cursor.getFields());
    assertArrayEquals(tagset, cursor.getTags());
    assertEquals(CURRENT_TIME, cursor.lastUpdated()); // last updated timestamp
    assertEquals(10, cursor.readLong(0)); // count
    assertEquals(d1 + d2 + d3 + d4 + d5 + d6 + d7 + d8 + d9 + d10, cursor.readDouble(1)); // sum
    assertEquals(d1, cursor.readDouble(2)); // min
    assertEquals(d10, cursor.readDouble(3)); // max
    assertEquals(d10, cursor.readDouble(4)); // lastValue
    assertEquals(3, cursor.readLong(5)); // [0.5,10.0)
    assertEquals(2, cursor.readLong(6)); // [10.0,100.0)
    assertEquals(2, cursor.readLong(7)); // overflow
    assertEquals(3, cursor.readLong(8)); // underflow

    assertEquals(1, table.size());
    assertEquals(128, table.capacity());
  }
}
