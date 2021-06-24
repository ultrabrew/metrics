// Copyright 2021, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

/**
 * A distribution bucket specification used for histograms. A distribution bucket is an sorted array
 * of unique numeric values. Each bucket represents a distribution range of the measurement.
 *
 * @see DistributionBucket
 * @see DoubleValuedDistributionBucket
 */
public interface DistributionBucketIF<B extends DistributionBucketIF> {

  /**
   * A constant used by {@link #getBucketNames()} to name the underflow bucket.
   *
   * @see DistributionBucket#getBucketNames()
   * @see DoubleValuedDistributionBucket#getBucketNames()
   */
  String UNDERFLOW = "underflow";

  /**
   * A constant used {@link #getBucketNames()} to name the overflow bucket.
   *
   * @see DistributionBucket#getBucketNames()
   * @see DoubleValuedDistributionBucket#getBucketNames()
   */
  String OVERFLOW = "overflow";

  /** @return count of buckets including {@link #OVERFLOW} and {@link #UNDERFLOW} buckets. */
  int getCount();

  /**
   * Generates the bucket names for representation purpose.
   *
   * <p>For the bucket values: [0, 10, 100, 500, 1000], the names would look like:
   *
   * <ul>
   *   <li>0_10
   *   <li>10_100
   *   <li>100_500
   *   <li>500_1000
   *   <li>{@link #OVERFLOW}
   *   <li>{@link #UNDERFLOW}
   * </ul>
   *
   * The {@link #OVERFLOW} bucket would contain the count of all the values {@literal >}= 1000. The
   * {@link #UNDERFLOW} bucket would contain the count of all the values {@literal <} 0.
   *
   * @return array of bucket names
   */
  String[] getBucketNames();

  /**
   * Builds the histogram aggregator
   *
   * @param metricId identifier of the metric
   * @param bucket distribution bucket
   * @param maxCardinality maximum cardinality of data in the histogram
   * @return newly built aggregator
   */
  Aggregator buildAggregator(String metricId, B bucket, int maxCardinality);
}
