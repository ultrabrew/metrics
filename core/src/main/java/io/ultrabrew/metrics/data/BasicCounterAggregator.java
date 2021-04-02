// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static io.ultrabrew.metrics.Metric.DEFAULT_CARDINALITY;
import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;

import io.ultrabrew.metrics.Counter;

/**
 * A monoid for common aggregation functions for a Counter metric class.
 *
 * <p>Performs the following aggregation functions on the measured values:</p>
 * <ul>
 * <li>sum of the values</li>
 * </ul>
 *
 * @see Counter
 */
public class BasicCounterAggregator extends ConcurrentMonoidLongTable {

  private static final String[] FIELDS = {"sum"};
  private static final Type[] TYPES = {Type.LONG};
  private static final long[] IDENTITY = {0L};

  /**
   * Create a monoid for common aggregation functions for a Counter.
   *
   * @param counter metric
   */
  public BasicCounterAggregator(final Counter counter) {
    this(counter.id, counter.maxCardinality, counter.cardinality);
  }

  /**
   * Create a monoid for common aggregation functions for a Counter.
   *
   * @param metricId identifier of the metric associated with this aggregator
   */
  public BasicCounterAggregator(final String metricId) {
    this(metricId, DEFAULT_MAX_CARDINALITY);
  }

  /**
   * Create a monoid for common aggregation functions for a Counter with requested capacity.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param maxCardinality requested max capacity of table in records. Table doesn't grow beyond
   * this
   */
  public BasicCounterAggregator(final String metricId, final int maxCardinality) {
    this(metricId, maxCardinality, DEFAULT_CARDINALITY);
  }

  /**
   * Create a monoid for common aggregation functions for a Counter with requested initial capacity
   * and max capacity.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param maxCardinality requested max capacity of table in records. Table doesn't grow beyond
   * this
   * @param cardinality requested capacity of table in records, actual capacity may be higher
   */
  public BasicCounterAggregator(final String metricId, final int maxCardinality,
      final int cardinality) {
    super(metricId, maxCardinality, cardinality, FIELDS, TYPES, IDENTITY);
  }

  @Override
  public void combine(final long[] table, final int slotStartIndex, final long value) {
    add(table, slotStartIndex, 0, value);
  }
}
