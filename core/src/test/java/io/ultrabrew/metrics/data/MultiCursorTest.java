// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.ultrabrew.metrics.reporters.AggregatingReporter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiCursorTest {

  private static final Logger log = LoggerFactory.getLogger(MultiCursorTest.class);

  private long CURRENT_TIME = System.currentTimeMillis();

  @Test
  public void testMultiCursor() {
    final BasicGaugeAggregator gaugeAggregator = new BasicGaugeAggregator("gauge", DEFAULT_MAX_CARDINALITY, 10);
    final BasicTimerAggregator timerAggregator = new BasicTimerAggregator("timer", DEFAULT_MAX_CARDINALITY, 10);
    final BasicCounterAggregator counterAggregator = new BasicCounterAggregator("counter", DEFAULT_MAX_CARDINALITY, 10);

    final String[] tagSet1 = new String[]{"testTag", "value"};
    final String[] tagSet2 = new String[]{"testTag", "value", "testTag2", "value2"};
    // Test that duplicate string arrays are de-duped by multi-cursor
    final String[] tagSet2b = new String[]{"testTag", "value", "testTag2", "value2"};
    final String[] tagSet3 = new String[]{"testTag", "value2"};
    final String[] tagSet4 = new String[]{"testTag3", "value"};

    gaugeAggregator.apply(tagSet3, 1L, CURRENT_TIME);
    gaugeAggregator.apply(tagSet3, 2L, CURRENT_TIME);
    gaugeAggregator.apply(tagSet3, 3L, CURRENT_TIME);
    gaugeAggregator.apply(tagSet2, 10L, CURRENT_TIME);
    gaugeAggregator.apply(tagSet1, 0L, CURRENT_TIME);
    gaugeAggregator.apply(tagSet4, -1L, CURRENT_TIME);

    timerAggregator.apply(tagSet2b, 110L, CURRENT_TIME);
    timerAggregator.apply(tagSet2b, 100L, CURRENT_TIME);

    counterAggregator.apply(tagSet4, 1L, CURRENT_TIME);
    counterAggregator.apply(tagSet1, 1L, CURRENT_TIME);

    final MultiCursor multiCursor =
        new MultiCursor(
            Arrays.asList(timerAggregator, gaugeAggregator, counterAggregator,
                AggregatingReporter.NOOP));

    final Map<String[], List<String>> records = new java.util.HashMap<>();

    while (multiCursor.next()) {
      CursorEntry entry = multiCursor.nextCursorEntry();
      while (entry != null) {
        final List<String> metricIds = records
            .getOrDefault(multiCursor.getTags(), new java.util.ArrayList<>());
        metricIds.add(entry.getMetricId());
        records.put(multiCursor.getTags(), metricIds);
        log.info("{} {}", multiCursor.getTags(), entry.getMetricId());

        entry = multiCursor.nextCursorEntry();
      }
      assertNull(multiCursor.nextCursorEntry());
    }
    assertFalse(multiCursor.next());

    assertEquals(4, records.size());
    assertThat(records.get(tagSet1), containsInAnyOrder("gauge", "counter"));
    // Because timerAggregator is first in the list, its tagset2b is used for both
    assertThat(records.get(tagSet2b), containsInAnyOrder("gauge", "timer"));
    assertThat(records.get(tagSet3), containsInAnyOrder("gauge"));
    assertThat(records.get(tagSet4), containsInAnyOrder("gauge", "counter"));
  }
}
