// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import io.ultrabrew.metrics.Gauge;

/**
 * A monoid for common aggregation functions for a Gauge metric class.
 *
 * <p>Performs the following aggregation functions on the measurements:</p>
 * <ul>
 * <li>count of measurements</li>
 * <li>sum of the measurement values</li>
 * <li>minimum measured value</li>
 * <li>maximum measured value</li>
 * <li>last measured value</li>
 * </ul>
 *
 * @see Gauge
 */
public class BasicGaugeAggregator extends ConcurrentMonoidHashTable {

  private static final String[] FIELDS = {"count", "sum", "min", "max", "lastValue"};
  private static final Type[] TYPES = {Type.LONG, Type.LONG, Type.LONG, Type.LONG, Type.LONG};
  private static final long[] IDENTITY = {0L, 0L, Long.MAX_VALUE, Long.MIN_VALUE, 0L};
  private static final int DEFAULT_CAPACITY = 128;

  /**
   * Create a monoid for common aggregation functions for a Counter.
   *
   * @param metricId identifier of the metric associated with this aggregator
   */
  public BasicGaugeAggregator(final String metricId) {
    this(metricId, DEFAULT_CAPACITY);
  }

  /**
   * Create a monoid for common aggregation functions for a Counter with requested capacity.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param capacity requested capacity of table in records, actual capacity may be higher
   */
  public BasicGaugeAggregator(final String metricId, final int capacity) {
    super(metricId, capacity, FIELDS, TYPES, IDENTITY);
  }

  /**
   * Create a monoid for common aggregation functions for a Counter with requested initial capacity
   * and max capacity.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param capacity requested capacity of table in records, actual capacity may be higher
   * @param maxCapacity requested max capacity of table in records. Table doesn't grow beyond this
   * value.
   */
  public BasicGaugeAggregator(final String metricId, final int capacity, final int maxCapacity) {
    super(metricId, capacity, FIELDS, TYPES, IDENTITY, maxCapacity);
  }

  @Override
  public void combine(final long[] table, final long baseOffset, final long value) {
    add(table, baseOffset, 0, 1L);
    add(table, baseOffset, 1, value);
    min(table, baseOffset, 2, value);
    max(table, baseOffset, 3, value);
    set(table, baseOffset, 4, value);
  }
}
