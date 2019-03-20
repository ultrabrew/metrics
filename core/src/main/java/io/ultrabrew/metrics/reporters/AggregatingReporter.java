// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters;

import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.Gauge;
import io.ultrabrew.metrics.GaugeDouble;
import io.ultrabrew.metrics.Histogram;
import io.ultrabrew.metrics.Metric;
import io.ultrabrew.metrics.Reporter;
import io.ultrabrew.metrics.Timer;
import io.ultrabrew.metrics.data.Aggregator;
import io.ultrabrew.metrics.data.BasicCounterAggregator;
import io.ultrabrew.metrics.data.BasicGaugeAggregator;
import io.ultrabrew.metrics.data.BasicGaugeDoubleAggregator;
import io.ultrabrew.metrics.data.BasicHistogramAggregator;
import io.ultrabrew.metrics.data.BasicTimerAggregator;
import io.ultrabrew.metrics.data.Cursor;
import io.ultrabrew.metrics.data.Type;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A base class of a reporter that aggregates in-process the measurement events. This base class
 * allows different {@link Aggregator}s for different metric classes and allows overriding the
 * default aggregator based on the metric's identifier.
 *
 * <p>Default aggregators are</p>
 * <ul>
 * <li>{@link BasicCounterAggregator} for {@link Counter}</li>
 * <li>{@link BasicGaugeAggregator} for {@link Gauge}</li>
 * <li>{@link BasicGaugeDoubleAggregator} for {@link GaugeDouble}</li>
 * <li>{@link BasicTimerAggregator} for {@link Timer}</li>
 * </ul>
 *
 * <p>All unknown metric classes will get a {@link #NOOP} no-op aggregation that ignores the
 * metric.</p>
 */
public abstract class AggregatingReporter implements Reporter {

  private static final String[] NO_TAGS = new String[]{};

  private static final Cursor EMPTY_CURSOR = new Cursor() {
    @Override
    public boolean next() {
      return false;
    }

    // CLOVER:OFF
    @Override
    public String getMetricId() {
      throw new IllegalStateException("Cursor has no valid data");
    }

    @Override
    public String[] getTags() {
      throw new IllegalStateException("Cursor has no valid data");
    }

    @Override
    public long lastUpdated() {
      throw new IllegalStateException("Cursor has no valid data");
    }

    @Override
    public long readLong(int index) {
      throw new IllegalStateException("Cursor has no valid data");
    }

    @Override
    public double readDouble(int index) {
      throw new IllegalStateException("Cursor has no valid data");
    }

    @Override
    public long readAndResetLong(int index) {
      throw new IllegalStateException("Cursor has no valid data");
    }

    @Override
    public double readAndResetDouble(int index) {
      throw new IllegalStateException("Cursor has no valid data");
    }

    @Override
    public String[] getFields() {
      throw new IllegalStateException("Cursor has no valid data");
    }

    @Override
    public Type[] getTypes() {
      throw new IllegalStateException("Cursor has no valid data");
    }
    // CLOVER:ON
  };

  /**
   * No-operation aggregator that ignores the measurement events and returns an empty cursor.
   */
  public static final Aggregator NOOP = new Aggregator() {
    @Override
    public void apply(String[] tags, long value, long timestamp) {
      // noop
    }

    @Override
    public Cursor cursor() {
      return EMPTY_CURSOR;
    }

    @Override
    public Cursor sortedCursor() {
      return EMPTY_CURSOR;
    }

  };

  /**
   * Default aggregators for the default metrics. When creating custom metrics and custom
   * aggregators for them, you should include these aggregators in the default aggregator list given
   * to {@link #AggregatingReporter(Map)}.
   */
  public static final Map<Class<? extends Metric>, Function<Metric, ? extends Aggregator>> DEFAULT_AGGREGATORS =
      Collections
          .unmodifiableMap(
              new java.util.HashMap<Class<? extends Metric>, Function<Metric, ? extends Aggregator>>() {{
                put(Counter.class, metric -> new BasicCounterAggregator((Counter) metric));
                put(Gauge.class, metric -> new BasicGaugeAggregator((Gauge) metric));
                put(GaugeDouble.class, metric -> new BasicGaugeDoubleAggregator((GaugeDouble) metric));
                put(Timer.class, metric -> new BasicTimerAggregator((Timer) metric));
                put(Histogram.class, metric -> new BasicHistogramAggregator((Histogram) metric));
              }});

  /**
   * Concurrent map of aggregators for each metric. The key of the map is the identifier of the
   * metric and the value is the aggregator to be used for that metric.
   */
  protected final ConcurrentHashMap<String, Aggregator> aggregators;

  private final Map<Class<? extends Metric>, Function<Metric, ? extends Aggregator>> defaultAggregators;

  /**
   * Create an aggregating reporter with default aggregators for default metrics only.
   */
  protected AggregatingReporter() {
    this(DEFAULT_AGGREGATORS);
  }

  /**
   * Create an aggregating reporter with given default aggregators.
   *
   * @param defaultAggregators a map of a metric class to a supplier creating a new aggregator
   * instance
   */
  protected AggregatingReporter(
      final Map<Class<? extends Metric>, Function<Metric, ? extends Aggregator>> defaultAggregators) {
    aggregators = new ConcurrentHashMap<>();
    this.defaultAggregators = Collections.unmodifiableMap(defaultAggregators);
  }

  @Override
  public void emit(final Metric metric, final long timestamp, final long value,
      final String[] tags) {
    Aggregator aggregator = aggregators.get(metric.id);
    if (aggregator == null) {
      aggregator = aggregators.computeIfAbsent(metric.id, (k) -> createAggregator(metric));
    }
    aggregator.apply(tags != null ? tags : NO_TAGS, value, timestamp);
  }

  /**
   * Create a new aggregator for a metric. Called when an measurement event from previously unseen
   * metric is received in this aggregator.
   *
   * @param metric metric instance producing the measurement event
   * @return a new aggregator for the given metric. If metric should not be aggregated, the NOOP
   * aggregator must be returned.
   */
  protected Aggregator createAggregator(final Metric metric) {
    // TODO: Implement override by metric name
    Function<Metric, ? extends Aggregator> supplier = defaultAggregators.get(metric.getClass());
    if (supplier == null) {
      return NOOP;
    }
    return supplier.apply(metric);
  }

}
