// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class TypeTest {

  private long CURRENT_TIME = System.currentTimeMillis();

  @Test
  public void readAndResetLong() {

    final BasicCounterAggregator table = new BasicCounterAggregator("test", DEFAULT_MAX_CARDINALITY, 10);

    table.apply(new String[]{"testTag", "value"}, 100L, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, 10L, CURRENT_TIME);
    Cursor cursor = table.cursor();
    cursor.next();

    Type[] types = cursor.getTypes();
    String s = types[0].readAndReset(cursor, 0);
    assertEquals(String.valueOf(100L + 10L), s);
  }

  @Test
  public void readAndResetDouble() {

    double d1 = 10.19;
    double d2 = 5179.0003;

    long l1 = Double.doubleToLongBits(d1);
    long l2 = Double.doubleToLongBits(d2);

    final BasicGaugeDoubleAggregator table = new BasicGaugeDoubleAggregator("test");

    table.apply(new String[]{"testTag", "value"}, l1, CURRENT_TIME);
    table.apply(new String[]{"testTag", "value"}, l2, CURRENT_TIME);
    Cursor cursor = table.cursor();
    cursor.next();

    Type[] types = cursor.getTypes();
    assertEquals(String.valueOf(2), types[0].readAndReset(cursor, 0));
    assertEquals(String.valueOf(d2), types[1].readAndReset(cursor, 3));
  }
}
