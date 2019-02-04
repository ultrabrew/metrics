// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

/**
 * Counter increments or decrements a long value.
 *
 * <pre>{@code
 *     public class TestResource {
 *         private static final String TAG_HOST = "host";
 *         private static final String TAG_CLIENT = "client";
 *         private final Counter errorCounter;
 *         private final String hostName;
 *
 *         public TestResource(final MetricRegistry metricRegistry,
 *                             final String hostName) {
 *             errorCounter = metricRegistry.counter("errors");
 *             this.hostName = hostName;
 *         }
 *
 *         public void handleError(final String clientId) {
 *             errorCounter.inc(TAG_CLIENT, clientId, TAG_HOST, hostName);
 *
 *             // .. do something ..
 *         }
 *     }
 * }</pre>
 *
 * Note: The tag key-value array must always be sorted in the same order.
 *
 * <p>This class is thread-safe.</p>
 */
public class Counter extends Metric {

  Counter(final MetricRegistry registry, final String id) {
    super(registry, id);
  }

  /**
   * Increment the counter by 1.
   *
   * @param tags a sorted array of tag key-value pairs in a flattened array
   */
  public void inc(final String... tags) {
    emit(1L, tags);
  }

  /**
   * Decrement the counter by 1.
   *
   * @param tags a sorted array of tag key-value pairs in a flattened array
   */
  public void dec(final String... tags) {
    emit(-1L, tags);
  }

  /**
   * Increment the counter by given change value.
   *
   * @param change value by which to increment the counter
   * @param tags a sorted array of tag key-value pairs in a flattened array
   */
  public void inc(long change, final String... tags) {
    emit(change, tags);
  }

  /**
   * Decrement the counter by given change value.
   *
   * @param change value by which to decrement the counter
   * @param tags a sorted array of tag key-value pairs in a flattened array
   */
  public void dec(long change, final String... tags) {
    emit(-change, tags);
  }
}
