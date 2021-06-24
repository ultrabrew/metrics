// Copyright 2021, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class DoubleValuedDistributionBucketTest {
  @Test
  void testBucketCount() {
    DoubleValuedDistributionBucket bucket =
        new DoubleValuedDistributionBucket(new double[] {0.0, 0.5, 1.0, 50.0, 100.0});
    assertEquals(6, bucket.getCount());
  }

  @Test
  void testGetIndex() {
    DoubleValuedDistributionBucket bucket =
        new DoubleValuedDistributionBucket(new double[] {0.0, 0.5, 1.0, 50.0, 100.0});
    assertEquals(0, bucket.getBucketIndex(0.0));
    assertEquals(0, bucket.getBucketIndex(0.2));
    assertEquals(1, bucket.getBucketIndex(0.5));
    assertEquals(1, bucket.getBucketIndex(0.75));
    assertEquals(2, bucket.getBucketIndex(1.0));
    assertEquals(2, bucket.getBucketIndex(20));
    assertEquals(3, bucket.getBucketIndex(50));
    assertEquals(4, bucket.getBucketIndex(100.0));
    assertEquals(4, bucket.getBucketIndex(1000000));
    assertEquals(5, bucket.getBucketIndex(-10000));
    assertEquals(5, bucket.getBucketIndex(-1));
  }

  @ParameterizedTest
  @MethodSource("bucketNames")
  void testGetBucketNames(final double[] buckets, final String[] expected) {
    DoubleValuedDistributionBucket bucket = new DoubleValuedDistributionBucket(buckets);
    assertArrayEquals(expected, bucket.getBucketNames());
  }

  private static Stream<Arguments> bucketNames() {
    String[] expected = {"0.0_0.5", "0.5_1.0", "1.0_50.0", "50.0_100.0", "overflow", "underflow"};
    return Stream.of(
        arguments(new double[] {0.0, 0.5, 1.0, 50.0, 100.0}, expected),
        arguments(new double[] {0, 0.5, 1.0, 50.0, 100}, expected),
        arguments(new double[] {0.0, 0.5, 1, 50, 100.00}, expected),
        arguments(new double[] {0.0, 000.5, 1, 050.0, 00100.00}, expected));
  }

  @ParameterizedTest
  @MethodSource("minBucketlength")
  void minimumBucketLength(final double[] invalidBuckets) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DoubleValuedDistributionBucket(invalidBuckets),
        "Minimum bucket length is 2");
  }

  private static Stream<Arguments> minBucketlength() {
    return Stream.of(arguments(new double[] {}), arguments(new double[] {-10.0}));
  }

  @ParameterizedTest
  @MethodSource("bucketsNotSorted")
  void bucketsShouldBeSortedInAscendingOrder(final double[] invalidBuckets) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DoubleValuedDistributionBucket(invalidBuckets),
        "Bucket should be sorted in ascending order");
  }

  private static Stream<Arguments> bucketsNotSorted() {
    return Stream.of(
        arguments(new double[] {100.0, 50.0, 10.0, 1.0, 0.0}),
        arguments(new double[] {10.0, 5.0, -1.0, 99.9}));
  }

  @ParameterizedTest
  @MethodSource("duplicateEntries")
  void duplicateEntriesNotAllowed(final double[] invalidBuckets) {
    assertThrows(
        IllegalArgumentException.class,
        () -> new DoubleValuedDistributionBucket(invalidBuckets),
        "Bucket should not have duplicate entries");
  }

  private static Stream<Arguments> duplicateEntries() {
    return Stream.of(
        arguments(new double[] {0.0, 0.0, 10.0, 50.0, 100.0, 1000.0}),
        arguments(new double[] {0.0, 10.5, 10.5, 50.0, 100.0, 1000}),
        arguments(new double[] {0, 10, 50, 100, 1000, 1000}),
        arguments(new double[] {0, 10, 5.0, 100, 100, 1000}));
  }

  @Test
  void testCustomNamespec() {
    DoubleValuedDistributionBucket bucket =
        new DoubleValuedDistributionBucket(
            new double[] {0.0, 1.25, 5.0, 100.7, 1000.2}, (v) -> Long.toString((long) v));
    String[] expected = {"0_1", "1_5", "5_100", "100_1000", "overflow", "underflow"};
    assertArrayEquals(expected, bucket.getBucketNames());
  }
}
