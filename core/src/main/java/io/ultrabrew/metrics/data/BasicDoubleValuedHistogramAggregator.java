// Copyright 2021, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import io.ultrabrew.metrics.GaugeDouble;

import java.util.Arrays;

import static io.ultrabrew.metrics.Metric.DEFAULT_CARDINALITY;
import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;

/**
 * A monoid to generate histogram buckets along with the common aggregation functions for a given
 * metric of type {@link GaugeDouble}
 *
 * <p>Performs the following aggregation functions on the measurements:
 *
 * <ul>
 *   <li>count of measurements
 *   <li>sum of the measurement values
 *   <li>minimum measured value
 *   <li>maximum measured value
 *   <li>last measured value
 * </ul>
 *
 * The histogram buckets are derived from {@link DoubleValuedDistributionBucket}.
 *
 * <p>A sample histogram would look like:
 *
 * <ul>
 *   <li>[0.0_0.1)
 *   <li>[0.1_0.5)
 *   <li>[0.5_50.0)
 *   <li>overflow
 *   <li>underflow
 * </ul>
 *
 * @see DoubleValuedDistributionBucket
 */
public class BasicDoubleValuedHistogramAggregator extends BasicGaugeDoubleAggregator {

  private final DoubleValuedDistributionBucket buckets;

  /**
   * Creates a monoid for the histogram buckets for a {@link GaugeDouble}
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param bucket distribution bucket spec
   */
  public BasicDoubleValuedHistogramAggregator(
      final String metricId, final DoubleValuedDistributionBucket bucket) {
    this(metricId, bucket, DEFAULT_MAX_CARDINALITY);
  }

  /**
   * Creates a monoid for the histogram buckets for a {@link GaugeDouble}
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param bucket distribution bucket spec
   * @param maxCardinality requested max capacity of table in records. Table doesn't grow beyond
   */
  public BasicDoubleValuedHistogramAggregator(
      final String metricId,
      final DoubleValuedDistributionBucket bucket,
      final int maxCardinality) {
    this(metricId, bucket, maxCardinality, DEFAULT_CARDINALITY);
  }

  /**
   * Creates a monoid for the histogram buckets for a {@link GaugeDouble}
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param bucket distribution bucket spec
   * @param maxCardinality requested max capacity of table in records. Table doesn't grow beyond
   * @param cardinality requested capacity of table in records, actual capacity may be higher
   */
  public BasicDoubleValuedHistogramAggregator(
      final String metricId,
      final DoubleValuedDistributionBucket bucket,
      final int maxCardinality,
      final int cardinality) {
    super(
        metricId,
        maxCardinality,
        cardinality,
        buildFields(bucket),
        buildTypes(bucket),
        buildIdentity(bucket));
    this.buckets = bucket;
  }

  /**
   * Creates an array containing the identity values of the aggregation field of {@link
   * BasicGaugeDoubleAggregator} followed by zeros for the histogram buckets. These values are used
   * to initialize and reset the field after reading.
   *
   * @param buckets distribution bucket spec
   * @return array of identity values
   * @see {@link BasicGaugeDoubleAggregator#FIELDS}
   * @see {@link BasicGaugeDoubleAggregator#IDENTITY}
   */
  private static long[] buildIdentity(DoubleValuedDistributionBucket buckets) {
    long[] identity = new long[IDENTITY.length + buckets.getCount()];
    System.arraycopy(IDENTITY, 0, identity, 0, IDENTITY.length);
    Arrays.fill(identity, IDENTITY.length, identity.length, 0L);
    return identity;
  }

  /**
   * Creates an array of {@link Type} of the aggregation field and histogram buckets.
   *
   * @param buckets distribution bucket spec
   * @return array of {@link Type}s of the fields
   * @see {@link BasicGaugeDoubleAggregator#FIELDS}
   * @see {@link BasicGaugeDoubleAggregator#IDENTITY}
   * @see {@link BasicGaugeDoubleAggregator#TYPES}
   */
  private static Type[] buildTypes(DoubleValuedDistributionBucket buckets) {
    Type[] types = new Type[TYPES.length + buckets.getCount()];
    System.arraycopy(TYPES, 0, types, 0, TYPES.length);
    Arrays.fill(types, TYPES.length, types.length, Type.LONG);
    return types;
  }

  /**
   * Creates an array of names of the aggregation field and histogram buckets.
   *
   * @param buckets distribution bucket spec
   * @return array of field names
   * @see {@link BasicGaugeDoubleAggregator#FIELDS}
   */
  private static String[] buildFields(DoubleValuedDistributionBucket buckets) {
    String[] fields = new String[FIELDS.length + buckets.getCount()];
    String[] bucketNames = buckets.getBucketNames();
    System.arraycopy(FIELDS, 0, fields, 0, FIELDS.length);
    System.arraycopy(bucketNames, 0, fields, FIELDS.length, bucketNames.length);
    return fields;
  }

  @Override
  public void combine(long[] table, long baseOffset, long value) {
    final double d = Double.longBitsToDouble(value);
    add(table, baseOffset, 0, 1L);
    add(table, baseOffset, 1, d);
    min(table, baseOffset, 2, d);
    max(table, baseOffset, 3, d);
    set(table, baseOffset, 4, d);

    // Increments the bucket counter by 1 responding to the given value
    int bucketIndex = buckets.getBucketIndex(d);
    add(table, baseOffset, FIELDS.length + bucketIndex, 1);
  }
}
