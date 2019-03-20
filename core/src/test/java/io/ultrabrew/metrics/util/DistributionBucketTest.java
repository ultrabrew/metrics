package io.ultrabrew.metrics.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DistributionBucketTest {

  @Test
  void testBucketCount() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 50, 100, 1000});
    assertEquals(6, bucket.getCount());
  }

  @Test
  void testGetIndex() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 50, 100, 1000});
    assertEquals(0, bucket.getBucketIndex(0));
    assertEquals(0, bucket.getBucketIndex(1));
    assertEquals(1, bucket.getBucketIndex(10));
    assertEquals(2, bucket.getBucketIndex(50));
    assertEquals(3, bucket.getBucketIndex(100));
    assertEquals(4, bucket.getBucketIndex(1000));
    assertEquals(4, bucket.getBucketIndex(1001));
    assertEquals(4, bucket.getBucketIndex(1000000));
    assertEquals(5, bucket.getBucketIndex(-10000));
    assertEquals(5, bucket.getBucketIndex(-1));
  }

  @Test
  void testGetBucketNames() {
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 50, 100, 1000});
    String[] expected = {"0_10", "10_50", "50_100", "100_1000", "overflow", "underflow"};
    assertArrayEquals(expected, bucket.getBucketNames());
  }

  private static Stream<Arguments> minBucketlength() {
    return Stream.of(
        arguments(new long[]{}),
        arguments(new long[]{-100})
    );
  }

  @ParameterizedTest
  @MethodSource("minBucketlength")
  void minimumBucketLength(final long[] invalidBuckets) {
    assertThrows(IllegalArgumentException.class, () -> new DistributionBucket(invalidBuckets),
        "Minimum bucket length is 2");
  }

  private static Stream<Arguments> bucketsNotSorted() {
    return Stream.of(
        arguments(new long[]{1000, 500, 100, 10, 0}),
        arguments(new long[]{100, 5, -1, 999})
    );
  }

  @ParameterizedTest
  @MethodSource("bucketsNotSorted")
  void bucketsShouldBeSortedInAscendingOrder(final long[] invalidBuckets) {
    assertThrows(IllegalArgumentException.class, () -> new DistributionBucket(invalidBuckets),
        "Bucket should be sorted in ascending order");
  }

  private static Stream<Arguments> duplicateEntries() {
    return Stream.of(
        arguments(new long[]{0, 0, 10, 50, 100, 1000}),
        arguments(new long[]{0, 10, 10, 50, 100, 1000}),
        arguments(new long[]{0, 10, 50, 100, 1000, 1000}),
        arguments(new long[]{0, 10, 50, 100, 100, 1000})
    );
  }

  @ParameterizedTest
  @MethodSource("duplicateEntries")
  void duplicateEntriesNotAllowed(final long[] invalidBuckets) {
    assertThrows(IllegalArgumentException.class, () -> new DistributionBucket(invalidBuckets),
        "Bucket should not have duplicate entries");
  }
}
