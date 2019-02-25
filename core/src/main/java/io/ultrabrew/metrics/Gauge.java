// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

/**
 * Gauge measures a long value at given time.
 *
 * <pre>{@code
 *     public class TestResource {
 *         private static final String TAG_HOST = "host";
 *         private final Gauge cacheSizeGauge;
 *         private final String hostName;
 *         private final Map<String,String> cache;
 *
 *         public TestResource(final MetricRegistry metricRegistry,
 *                             final String hostName) {
 *             cacheSizeGauge = metricRegistry.gauge("cacheSize");
 *             this.hostName = hostName;
 *             cache = new java.util.Map<>();
 *         }
 *
 *         public void doSomething() {
 *             cacheSizeGauge.set(cache.size(), TAG_HOST, hostName);
 *         }
 *     }
 * }</pre>
 *
 * Note: The tag key-value array must always be sorted in the same order.
 *
 * <p>This class is thread-safe.</p>
 */
public class Gauge extends Metric {

  Gauge(final MetricRegistry registry, final String id) {
    super(registry, id);
  }

  /**
   * Measure the gauge's value.
   *
   * @param value set value of gauge
   * @param tags a sorted array of tag key-value pairs
   */
  public void set(final long value, final String... tags) {
    emit(value, tags);
  }
}
