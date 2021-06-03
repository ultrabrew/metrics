// Copyright 2019, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * A distribution bucket specification used for histograms. A distribution bucket is an sorted array
 * of unique numeric values. Each bucket represents a distribution range of the measurement.
 *
 * <P>For a given distribution array: [0, 10, 100, 500, 1000], the buckets would be like:</P>
 * <ul>
 * <li>[0,10) for values 0-9</li>
 * <li>[10,100) for values 10-99</li>
 * <li>[100,500) for values 100-499</li>
 * <li>[500,1000) for values 500-999</li>
 * <li>overflow  for values {@literal >}= 1000</li>
 * <li>underflow for values {@literal <} 0</li>
 * </ul>
 *
 * @see BasicHistogramAggregator
 * @see NameSpec
 */
public class DistributionBucket implements DistributionBucketIF<DistributionBucket> {

  private final long[] buckets;
  private final NameSpec nameSpec;

  /**
   * Creates a distribution for given bucket spec.
   *
   * @param buckets sorted array of unique values
   */
  public DistributionBucket(final long[] buckets) {
    this(buckets, new DefaultNameSpec());
  }

  public DistributionBucket(final long[] buckets, final NameSpec nameSpec) {
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
    this.nameSpec = nameSpec;
  }

  /**
   * @return count of buckets including {@link #OVERFLOW} and {@link #UNDERFLOW} buckets.
   */
  @Override
  public int getCount() {
    return buckets.length + 1;
  }

  /**
   * retrieves the corresponding bucket index for a given measurement value.
   *
   * <p>It searches the array of buckets for a specified value using the binary search
   * algorithm. The implementation of the algorithm is slightly different from {@link Arrays#binarySearch(long[],
   * long)} as each element in the array represents a range of values. So, it has to do a range
   * check instead of the equality check.
   *
   * @param value measurement value
   * @return bucket index
   */
  public int getBucketIndex(long value) {

    int low = 0;
    int high = buckets.length - 1;

    long minVal = buckets[low];
    long maxVal = buckets[high];

    if (value >= maxVal) {
      return high; // overflow
    }
    if (value < minVal) {
      return high + 1; // underflow
    }

    while (low < high) {
      int mid = (low + high) >>> 1;
      long midVal = buckets[mid];
      long nextToMidVal = buckets[mid + 1];
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
   * <p>For a given spec: [0, 10, 100, 500, 1000], the names would look like:
   *
   * <ul>
   *   <li>0_10
   *   <li>10_100
   *   <li>100_500
   *   <li>500_1000
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
      names[i] = nameSpec.getBucketName(buckets[i]) + '_' + nameSpec.getBucketName(buckets[i + 1]);
    }
    names[i++] = OVERFLOW;
    names[i++] = UNDERFLOW;
    return names;
  }

  @Override
  public Aggregator buildAggregator(String metricId, DistributionBucket bucket, int maxCardinality) {
    return new BasicHistogramAggregator(metricId, bucket, maxCardinality);
  }

  private static boolean isSorted(final long[] buckets) {
    return !matchAny(buckets, i -> buckets[i] > buckets[i + 1]);
  }

  private static boolean hasDuplicate(final long[] buckets) {
    return matchAny(buckets, i -> buckets[i] == buckets[i + 1]);
  }

  /**
   * It creates garbage because of box and unbox of the integers. It's fine as long as only used in
   * the constructor.
   */
  private static boolean matchAny(final long[] buckets, Predicate<Integer> predicate) {
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
   * <p>For a given spec: [0, 10, 100, 500, 1000], the names would look like:
   *
   * <ul>
   *   <li>0_10
   *   <li>10_100
   *   <li>100_500
   *   <li>500_1000
   *   <li>overflow
   *   <li>underflow
   * </ul>
   *
   * For a measurement unit of nano seconds, the default naming scheme would look ugly. User can use
   * a custom spec to prettify the bucket names. For example the bucket the names can milliseconds
   * even though internally it tracks the values in nanoseconds.
   */
  public interface NameSpec {
    String getBucketName(long bucket);
  }

  /**
   * Uses the String representation fo the bucket value as the name.
   */
  public static class DefaultNameSpec implements NameSpec {
    @Override
    public String getBucketName(long bucket) {
      return Long.toString(bucket);
    }
  }
}
