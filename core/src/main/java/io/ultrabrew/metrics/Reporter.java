// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import io.ultrabrew.metrics.reporters.AggregatingReporter;

/**
 * Reporter is a thread-safe consumer subscribing to measurement events in {@link MetricRegistry}.
 * It may forward events as-is to external aggregation via, e.g., UDP datagrams, or choose to
 * aggregate the events in-process.
 *
 * @see AggregatingReporter
 */
public interface Reporter {

  /**
   * Consume subscribed measurement event.
   *
   * @param metric metric instance emitting the event
   * @param timestamp update time, measured in milliseconds since midnight, January 1, 1970 UTC.
   * @param value measurement value
   * @param tags a sorted and flattened array of tag key-value pairs
   */
  void emit(final Metric metric, final long timestamp, final long value, final String[] tags);
}
