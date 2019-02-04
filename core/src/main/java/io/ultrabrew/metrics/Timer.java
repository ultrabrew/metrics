// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

/**
 * Timer measures time in nanoseconds between two events and acts as a counter to count the
 * events.
 *
 * <pre>{@code
 *     public class TestResource {
 *         private static final String TAG_HOST = "host";
 *         private static final String TAG_CLIENT = "client";
 *         private static final String TAG_STATUS = "status";
 *         private final Timer requestTimer;
 *         private final String hostName;
 *
 *         public TestResource(final MetricRegistry metricRegistry,
 *                             final String hostName) {
 *             requestTimer = metricRegistry.timer("requests");
 *             this.hostName = hostName;
 *         }
 *
 *         public void handleRequest(final String clientId) {
 *             final long startTime = requestTimer.start();
 *             int statusCode;
 *
 *             // .. handle request ..
 *
 *             // Note: no need for separate counter for requests per sec, as count is already included
 *             requestTimer.stop(startTime, TAG_CLIENT, clientId, TAG_HOST, hostName, TAG_STATUS,
 *                               String.valueOf(statusCode));
 *         }
 *     }
 * }</pre>
 *
 * Note: The tag key-value array must always be sorted in the same order.
 *
 * <p>This class is thread-safe.</p>
 */
public class Timer extends Metric {

  Timer(final MetricRegistry registry, final String id) {
    super(registry, id);
  }

  /**
   * Start the timer.
   *
   * @return start time in nanoseconds
   */
  public long start() {
    return System.nanoTime();
  }

  /**
   * Stop and update the timer.
   *
   * @param startTime start time in nanoseconds from {@link #start()}
   * @param tags a sorted and flattened array of tag key-value pairs
   */
  public void stop(final long startTime, final String... tags) {
    final long duration = System.nanoTime() - startTime;
    emit(duration, tags);
  }

  /**
   * Update the timer.
   *
   * @param duration duration in nanoseconds
   * @param tags a sorted and flattened array of tag key-value pairs
   */
  public void update(final long duration, final String... tags) {
    emit(duration, tags);
  }
}
