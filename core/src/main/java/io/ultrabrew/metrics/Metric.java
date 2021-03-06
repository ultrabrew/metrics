// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

/**
 * The base class of a system of measurement producing a single reportable metric. The system may
 * consist of related measures that facilitates the quantification of some particular
 * characteristic, but the end result is a single metric that is reported. Each measurement event is
 * emitted to all subscribers through associated metric registry.
 *
 * Each metric may have dozens (10+) of tag dimensions, each with hundreds (100+) of tag values and
 * a few (5+) fields. The combined time-series cardinality in a JVM can be more than a million (1,000,000). The
 * default max cardinality of a metric is 4k (4096) beyond which, any new dimension will be dropped.
 * Clients can pass a custom value to create metric with higher cardinality.
 *
 * @see MetricRegistry
 */
public abstract class Metric {

  public static final int DEFAULT_CARDINALITY = 128;
  public static final int DEFAULT_MAX_CARDINALITY = 4096; //4k

  /**
   * Identifier of the metric
   */
  public final String id;

  public final int cardinality;

  /**
   * Max cardinality for this metric. New dimensions will be dropped beyond this value.
   */
  public final int maxCardinality;

  private final MetricRegistry registry;

  /**
   * Create a metric associated with a metric registry.
   *
   * @param registry metric registry the metric is associated with
   * @param id identifier of the metric
   */
  protected Metric(final MetricRegistry registry, final String id) {
    this(registry, id, DEFAULT_MAX_CARDINALITY);
  }


  /**
   * Create a metric associated with a metric registry.
   *
   * @param registry metric registry the metric is associated with
   * @param id identifier of the metric
   * @param maxCardinality max cardinality. New dimensions will dropped beyond this value.
   */
  protected Metric(final MetricRegistry registry, final String id, final int maxCardinality) {
    this.registry = registry;
    this.id = id;
    this.cardinality = DEFAULT_CARDINALITY;
    this.maxCardinality = maxCardinality;
  }

  /**
   * Publish a measured value to all subscribers through the metric registry.
   *
   * @param value measurement value
   * @param tags a sorted and flattened array of tag key-value pairs
   */
  protected void emit(final long value, final String[] tags) {
    registry.emit(this, System.currentTimeMillis(), value, tags);
  }
}
