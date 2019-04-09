// Copyright 2019, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

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
 */
public class DistributionBucket {

  private static final String UNDERFLOW = "underflow";
  private static final String OVERFLOW = "overflow";

  private final long[] buckets;

  /**
   * Creates a distribution for given bucket spec.
   *
   * @param buckets sorted array of unique values
   */
  public DistributionBucket(final long[] buckets) {

    assert buckets.length >= 2 : "Minimum bucket length is 2";
    assert isSorted(buckets) : "Bucket should be sorted in ascending order";
    assert !hasDuplicate(buckets) : "Bucket should not have duplicate entries";

    this.buckets = buckets.clone();
  }

  public int getCount() {
    return buckets.length + 1; // includes the underflow and overflow buckets
  }

  public int getBucketIndex(long value) {
    return binarySearch(value);
  }

  /**
   * Generates the bucket names for representation purpose.
   *
   * <P>For a given spec: [0, 10, 100, 500, 1000], the names would look like:</P>
   * <ul>
   * <li>0_10</li>
   * <li>10_100</li>
   * <li>100_500</li>
   * <li>500_1000</li>
   * <li>overflow</li>
   * <li>underflow</li>
   * </ul>
   *
   * @return array of bucket names
   */
  public String[] getBucketNames() {
    int bucketCount = buckets.length;
    String[] names = new String[bucketCount + 1];
    int i = 0;
    for (; i < bucketCount - 1; i++) {
      names[i] = Long.toString(buckets[i]) + '_' + buckets[i + 1];
    }
    names[i++] = OVERFLOW;
    names[i++] = UNDERFLOW;
    return names;
  }

  /**
   * retrieves the corresponding bucket index for a given measurement value.
   *
   * @param key measurement value
   * @return bucket index
   */
  private int binarySearch(final long key) {

    int low = 0;
    int high = buckets.length - 1;

    long minVal = buckets[low];
    long maxVal = buckets[high];

    if (key >= maxVal) {
      return high; // overflow
    }
    if (key < minVal) {
      return high + 1; // underflow
    }

    while (low < high) {
      int mid = (low + high) >>> 1;
      long midVal = buckets[mid];
      long nextToMidVal = buckets[mid + 1];
      if (midVal <= key && key < nextToMidVal) {
        return mid;
      } else if (key < midVal) {
        high = mid - 1;
      } else {
        low = mid + 1;
      }
    }
    return low;
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

}
