// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.Gauge;
import io.ultrabrew.metrics.GaugeDouble;
import io.ultrabrew.metrics.MetricRegistry;
import io.ultrabrew.metrics.data.Aggregator;
import io.ultrabrew.metrics.data.Cursor;
import io.ultrabrew.metrics.data.CursorEntry;
import io.ultrabrew.metrics.data.MultiCursor;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class BasicAggregatingReporterTest {

  @Test
  public void testNoAggregatorForMetric() {
    AggregatingReporter reporter = new AggregatingReporter(Collections.emptyMap()) {
    };

    MetricRegistry registry = new MetricRegistry();
    registry.addReporter(reporter);
    Gauge gauge = registry.gauge("gauge");
    String[] tagSets = new String[]{"k1", "v1"};
    gauge.set(123, tagSets);
    Aggregator aggregator = reporter.aggregators.values().iterator().next();
    assertFalse(aggregator.cursor().next());
    assertFalse(aggregator.sortedCursor().next());
  }

  @Test
  public void testNullTags() {
    AggregatingReporter reporter = new AggregatingReporter() {
    };

    MetricRegistry registry = new MetricRegistry();
    registry.addReporter(reporter);
    Gauge gauge = registry.gauge("gauge");
    gauge.set(123, (String[]) null);
    Aggregator aggregator = reporter.aggregators.values().iterator().next();
    Cursor cursor = aggregator.cursor();
    while (cursor.next()) {
      assertArrayEquals(new String[]{}, cursor.getTags());
    }
  }

  @Test
  public void testReport() throws Exception {
    AggregatingReporter reporter = new AggregatingReporter() {
    };

    MetricRegistry registry = new MetricRegistry();
    registry.addReporter(reporter);
    String[] tagSets = new String[]{"k1", "v1"};
    String counterId = "persistentCounter";
    String gaugeId = "persistentGauge";
    Counter persistentCounter = registry.counter(counterId);
    GaugeDouble persistentGauge = registry.gaugeDouble(gaugeId);

    persistentCounter.inc(tagSets);
    persistentGauge.set(1.0, tagSets);

    MultiCursor multiCursor = new MultiCursor(reporter.aggregators.values());
    while (multiCursor.next()) {
      CursorEntry cursorEntry = multiCursor.nextCursorEntry();
      String metricId = cursorEntry.getMetricId();
      if (metricId.equals(counterId)) {
        assertEquals(1, cursorEntry.readLong(0));
      } else {
        assertEquals(1, cursorEntry.readLong(0));
        assertEquals(1.0, cursorEntry.readDouble(1), 1.0);
      }
    }
  }
}
