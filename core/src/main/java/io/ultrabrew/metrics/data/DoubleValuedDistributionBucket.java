// Copyright 2021, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * A distribution bucket specification used for histograms of a double valued metric. A distribution
 * bucket is an sorted array of unique numeric values. Each bucket represents a distribution range
 * of the measurement.
 *
 * <p>For a given distribution array: [0.0, 0.5, 1.0, 1.25, 50.0, 100.0], the buckets would be like:
 *
 * <ul>
 *   <li>[0.0, 0.5) for  0.0 {@literal <=} value {@literal <} 0.5
 *   <li>[0.5, 1.0) for 0.5 {@literal <=} value {@literal <} 1.0
 *   <li>[1.0, 1.25) for 1.0 {@literal <=} value {@literal <} 1.25
 *   <li>[1.25, 50.0) for 1.25 {@literal <=} value {@literal <} 50.0
 *   <li>[50.0, 100.0) for 50.0 {@literal <=} value {@literal <} 100.0
 *   <li>overflow for values {@literal >}= 100.0
 *   <li>underflow for values {@literal <} 0.0
 * </ul>
 *
 * @see BasicDoubleValuedHistogramAggregator
 * @see NameSpec
 */
public class DoubleValuedDistributionBucket implements DistributionBucketIF<DoubleValuedDistributionBucket> {

  private final double[] buckets;
  private final NameSpec spec;

  /**
   * Creates a {@link DoubleValuedDistributionBucket} for the given bucket values.
   *
   * @param buckets sorted array of unique values
   */
  public DoubleValuedDistributionBucket(final double[] buckets) {
    this(buckets, new DefaultNameSpec());
  }

  /**
   * Creates a {@link DoubleValuedDistributionBucket} for the given bucket values and custom {@link
   * NameSpec}.
   *
   * @param buckets sorted array of unique values
   * @param spec Custom {@link NameSpec}
   */
  public DoubleValuedDistributionBucket(final double[] buckets, final NameSpec spec) {

    if (buckets.length < 2) {
      throw new IllegalArgumentException("Minimum bucket length is 2");
    }
    if (!isSorted(buckets)) {
      throw new IllegalArgumentException("Bucket should be sorted in ascending order");
    }
    if (hasDuplicate(buckets)) {
      throw new IllegalArgumentException("Bucket should not have duplicate entries");
    }

    this.buckets = buckets.clone();
    this.spec = spec;
  }

  /**
   * @return count of buckets including {@link DistributionBucketIF#OVERFLOW} and {@link
   *     DistributionBucketIF#UNDERFLOW} buckets.
   */
  @Override
  public int getCount() {
    return buckets.length + 1;
  }

  @Override
  public Aggregator buildAggregator(String metricId, DoubleValuedDistributionBucket bucket, int maxCardinality) {
    return new BasicDoubleValuedHistogramAggregator(metricId, bucket, maxCardinality);
  }

  /**
   * retrieves the corresponding bucket index for a given measurement value.
   *
   * <p>It searches the array of buckets for a specified value using the binary search algorithm.
   * The implementation of the algorithm is slightly different from {@link
   * Arrays#binarySearch(long[], long)} as each element in the array represents a range of values.
   * So, it has to do a range check instead of the equality check.
   *
   * @param value measurement value
   * @return bucket index
   */
  public int getBucketIndex(double value) {

    int low = 0;
    int high = buckets.length - 1;

    double minVal = buckets[low];
    double maxVal = buckets[high];

    if (value >= maxVal) {
      return high; // overflow
    }
    if (value < minVal) {
      return high + 1; // underflow
    }

    while (low < high) {
      int mid = (low + high) >>> 1;
      double midVal = buckets[mid];
      double nextToMidVal = buckets[mid + 1];
      if (midVal <= value && value < nextToMidVal) {
        return mid;
      } else if (value < midVal) {
        high = mid - 1;
      } else {
        low = mid + 1;
      }
    }
    return low;
  }

  /**
   * Generates the bucket names for representation purpose.
   *
   * <p>For a given spec: [0.0, 0.5, 1.0, 50.0, 100.0], the names would look like:
   *
   * <ul>
   *   <li>0.0_0.5
   *   <li>0.5_1.0
   *   <li>1.0_50.0
   *   <li>50.0_100.0
   *   <li>overflow
   *   <li>underflow
   * </ul>
   *
   * @return array of bucket names
   */
  @Override
  public String[] getBucketNames() {
    int bucketCount = buckets.length;
    String[] names = new String[bucketCount + 1];
    int i = 0;
    for (; i < bucketCount - 1; i++) {
      names[i] = spec.getBucketName(buckets[i]) + '_' + spec.getBucketName(buckets[i + 1]);
    }
    names[i++] = OVERFLOW;
    names[i++] = UNDERFLOW;
    return names;
  }

  private static boolean isSorted(final double[] buckets) {
    return !matchAny(buckets, i -> buckets[i] > buckets[i + 1]);
  }

  private static boolean hasDuplicate(final double[] buckets) {
    return matchAny(buckets, i -> buckets[i] == buckets[i + 1]);
  }

  /**
   * It creates garbage because of box and unbox of the integers. It's fine as long as only used in
   * the constructor.
   */
  private static boolean matchAny(final double[] buckets, Predicate<Integer> predicate) {
    for (int i = 0; i < buckets.length - 1; i++) {
      if (predicate.test(i)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The bucket name spec. The default spec uses the String representation fo the bucket value as
   * the name.
   *
   * <p>For a given spec: [0.0, 10.5, 100.25, 500.0, 1000.0], the names would look like:
   *
   * <ul>
   *   <li>0.0_10.5
   *   <li>10.5_100.25
   *   <li>100.25_500.0
   *   <li>500.0_1000.0
   *   <li>overflow
   *   <li>underflow
   * </ul>
   *
   * For a measurement unit of nano seconds, the default naming scheme would look ugly. User can use
   * a custom spec to prettify the bucket names. For example the bucket the names can milliseconds
   * even though internally it tracks the values in nanoseconds.
   */
  public interface NameSpec {
    String getBucketName(double bucket);
  }

  /** Uses the String representation fo the bucket value as the name. */
  public static class DefaultNameSpec implements NameSpec {
    @Override
    public String getBucketName(double bucket) {
      return Double.toString(bucket);
    }
  }
}
