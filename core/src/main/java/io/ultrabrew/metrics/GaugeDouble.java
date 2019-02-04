// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

/**
 * GaugeDouble measures a double value at given time.
 *
 * <pre>{@code
 *     public class TestResource {
 *         private static final String TAG_HOST = "host";
 *         private final GaugeDouble cpuUsageGauge;
 *         private final String hostName;
 *
 *         public TestResource(final MetricRegistry metricRegistry, final String hostName) {
 *             this.cpuUsageGauge = metricRegistry.gaugeDouble("cpuUsage");
 *             this.hostName = hostName;
 *         }
 *
 *         public void doSomething() {
 *             double d = getCpuUsage();
 *             cpuUsageGauge.set(d, TAG_HOST, hostName);
 *         }
 *     }
 * }</pre>
 *
 * Note: The tag key-value array must always be sorted in the same order.
 *
 * <p>This class is thread-safe.</p>
 */
public class GaugeDouble extends Metric {

  GaugeDouble(final MetricRegistry registry, final String id) {
    super(registry, id);
  }

  /**
   * Measure the gauge's value.
   *
   * @param value set value of gauge
   * @param tags a sorted array of tag key-value pairs
   */
  public void set(final double value, final String... tags) {
    final long l = Double.doubleToRawLongBits(value);
    emit(l, tags);
  }
}
