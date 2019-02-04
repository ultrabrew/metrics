// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * A collection of metrics, which may be subscribed by a reporter. Each metric is always associated
 * only with a single metric registry, but reporters may subscribe to multiple metric registries.
 * Generally you only need one metric registry, although you may choose to use more if you need to
 * organize your metrics in particular reporting groups or subscribe with different reporters.
 *
 * <p>MetricRegistry will always return you the same instance of a metric if it already exists for
 * same identifier. Trying to create two different types of metrics for the same identifier will
 * result into a {@link IllegalStateException}. You should create all the metric instances you need
 * at the start of your application, and share them between your threads.</p>
 *
 * <p>This class is thread-safe.</p>
 */
public class MetricRegistry {

  private final Map<String, Metric> measurements;
  private final List<Reporter> reporters;

  /**
   * Create a registry of metrics.
   */
  public MetricRegistry() {
    measurements = new java.util.HashMap<>();
    reporters = new java.util.ArrayList<>();
  }

  /**
   * Return the {@link Counter} registered under this id; or create and register a new {@link
   * Counter}.
   *
   * @param id identifier of the measurement
   * @return a new or pre-existing {@link Counter}
   * @throws IllegalStateException measurement with different type, but same identifier already
   * exists
   */
  public Counter counter(final String id) {
    return getOrCreate(id, Counter.class);
  }

  /**
   * Return the {@link Gauge} registered under this id; or create and register a new {@link Gauge}.
   *
   * @param id identifier of the measurement
   * @return a new or pre-existing {@link Gauge}
   * @throws IllegalStateException measurement with different type, but same identifier already
   * exists
   */
  public Gauge gauge(final String id) {
    return getOrCreate(id, Gauge.class);
  }

  /**
   * Return the {@link GaugeDouble} registered under this id; or create and register a new {@link
   * GaugeDouble}.
   *
   * @param id identifier of the measurement
   * @return a new or pre-existing {@link GaugeDouble}
   * @throws IllegalStateException measurement with different type, but same identifier already
   * exists
   */
  public GaugeDouble gaugeDouble(final String id) {
    return getOrCreate(id, GaugeDouble.class);
  }

  /**
   * Return the {@link Timer} registered under this id; or create and register a new {@link Timer}.
   *
   * @param id identifier of the measurement
   * @return a new or pre-existing {@link Timer}
   * @throws IllegalStateException measurement with different type, but same identifier already
   * exists
   */
  public Timer timer(final String id) {
    return getOrCreate(id, Timer.class);
  }

  /**
   * Return a custom measurement registered under this id; or create and register a new custom
   * measurement of given class. The class must have an accessible constructor that takes
   * MetricRegistry and String as parameters.
   *
   * @param id identifier of the measurement
   * @param klass custom measurement class extending Metric
   * @param <T> custom measurement class extending Metric
   * @return a new or pre-existing custom measurement
   * @throws IllegalStateException measurement with different type, but same identifier already
   * exists
   */
  public <T extends Metric> T custom(final String id, final Class<T> klass) {
    return getOrCreate(id, klass);
  }

  /**
   * Subscribe a reporter to all measurement events produced by the metrics in the metric registry.
   *
   * @param reporter reporter to subscribe
   */
  public void addReporter(final Reporter reporter) {
    reporters.add(reporter);
  }

  private <T extends Metric> T getOrCreate(final String id, final Class<T> klass) {
    Metric m = measurements.get(id);
    if (m != null) {
      return tryCast(klass, m);
    }
    synchronized (measurements) {
      m = measurements.get(id);
      if (m != null) {
        return tryCast(klass, m);
      }
      try {
        T instance = klass.getDeclaredConstructor(MetricRegistry.class, String.class)
            .newInstance(this, id);
        measurements.put(id, instance);
        return instance;
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
          NoSuchMethodException e) {
        throw new IllegalStateException("Could not create measurement: " + id, e);
      }
    }
  }

  private <T extends Metric> T tryCast(Class<T> klass, Metric m) throws IllegalStateException {
    if (klass.isInstance(m)) {
      return klass.cast(m);
    }
    throw new IllegalStateException(
        "Metric '" + m.id + "' is already defined with different type: " + m.getClass());
  }

  //--- INTERNAL METHODS ---

  void emit(final Metric metric, final long timestamp, final long value, final String[] tags) {
    for (final Reporter r : reporters) {
      r.emit(metric, timestamp, value, tags);
    }
  }
}
