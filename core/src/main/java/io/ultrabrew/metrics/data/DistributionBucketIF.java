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

  String UNDERFLOW = "underflow";
  String OVERFLOW = "overflow";

  /** @return count of buckets including {@link #OVERFLOW} and {@link #UNDERFLOW} buckets. */
  int getCount();

  /**
   * Generates the bucket names for representation purpose.
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
