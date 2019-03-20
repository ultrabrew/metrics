package io.ultrabrew.metrics.util;

import static io.ultrabrew.metrics.util.Commons.checkArgument;

import java.util.function.Predicate;

public class DistributionBucket {

  private static final String UNDERFLOW = "underflow";
  private static final String OVERFLOW = "overflow";

  private final long[] buckets;

  public DistributionBucket(final long[] buckets) {

    checkArgument(buckets.length > 1, "Minimum bucket length is 2");
    checkArgument(isSorted(buckets), "Bucket should be sorted in ascending order");
    checkArgument(hasDuplicate(buckets), "Bucket should not have duplicate entries");

    this.buckets = buckets.clone();
  }

  public int getCount() {
    return buckets.length + 1; // includes the underflow and overflow buckets
  }

  public int getBucketIndex(long value) {
    return binarySearch(value);
  }

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

  private boolean isSorted(final long[] buckets) {
    return checkCondition(buckets, i -> buckets[i] > buckets[i + 1]);
  }

  private boolean hasDuplicate(final long[] buckets) {
    return checkCondition(buckets, i -> buckets[i] == buckets[i + 1]);
  }

  private boolean checkCondition(final long[] buckets, Predicate<Integer> predicate) {
    for (int i = 0; i < buckets.length - 1; i++) {
      if (predicate.test(i)) {
        return false;
      }
    }
    return true;
  }

}
