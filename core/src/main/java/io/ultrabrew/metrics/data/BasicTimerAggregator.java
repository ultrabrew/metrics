// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static io.ultrabrew.metrics.Metric.DEFAULT_CARDINALITY;
import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;

import io.ultrabrew.metrics.Gauge;
import io.ultrabrew.metrics.Timer;

/**
 * A monoid for common aggregation functions for a Timer metric class.
 *
 * <p>Performs the following aggregation functions on the measurements:</p>
 * <ul>
 * <li>count of measurements</li>
 * <li>sum of the measurement values</li>
 * <li>minimum measured value</li>
 * <li>maximum measured value</li>
 * </ul>
 *
 * @see Gauge
 */
public class BasicTimerAggregator extends ConcurrentMonoidLongTable {

  private static final String[] FIELDS = {"count", "sum", "min", "max"};
  private static final Type[] TYPES = {Type.LONG, Type.LONG, Type.LONG, Type.LONG};
  private static final long[] IDENTITY = {0L, 0L, Long.MAX_VALUE, Long.MIN_VALUE};


  /**
   * Create a monoid for common aggregation functions for a Timer.
   *
   * @param timer metric
   */
  public BasicTimerAggregator(final Timer timer) {
    this(timer.id, timer.maxCardinality, timer.cardinality);
  }

  /**
   * Create a monoid for common aggregation functions for a Timer.
   *
   * @param metricId identifier of the metric associated with this aggregator
   */
  public BasicTimerAggregator(final String metricId) {
    this(metricId, DEFAULT_MAX_CARDINALITY);
  }

  /**
   * Create a monoid for common aggregation functions for a Timer with requested capacity.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param maxCardinality requested max capacity of table in records. Table doesn't grow beyond
   * this
   */
  public BasicTimerAggregator(final String metricId, final int maxCardinality) {
    this(metricId, maxCardinality, DEFAULT_CARDINALITY);
  }

  /**
   * Create a monoid for common aggregation functions for a Timer with requested initial capacity
   * and max capacity.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param maxCardinality requested max capacity of table in records. Table doesn't grow beyond
   * this
   * @param cardinality requested capacity of table in records, actual capacity may be higher
   */
  public BasicTimerAggregator(final String metricId, final int maxCardinality,
      final int cardinality) {
    super(metricId, maxCardinality, cardinality, FIELDS, TYPES, IDENTITY);
  }

  @Override
  public void combine(final long[] table, final int slotStartIndex, final long value) {
    add(table, slotStartIndex, 0, 1L);
    add(table, slotStartIndex, 1, value);
    min(table, slotStartIndex, 2, value);
    max(table, slotStartIndex, 3, value);
  }
}
