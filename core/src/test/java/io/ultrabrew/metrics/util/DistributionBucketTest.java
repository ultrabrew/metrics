package io.ultrabrew.metrics.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class DistributionBucketTest {

    @Test
    void testBucketCount() {
        DistributionBucket bucket = new DistributionBucket(new int[]{0, 10, 50, 100, 1000});
        assertEquals(6, bucket.getCount());
    }

    @Test
    void testGetIndex() {
        DistributionBucket bucket = new DistributionBucket(new int[]{0, 10, 50, 100, 1000});
        assertEquals(0, bucket.getBucketIndex(-10000));
        assertEquals(0, bucket.getBucketIndex(-1));
        assertEquals(1, bucket.getBucketIndex(0));
        assertEquals(1, bucket.getBucketIndex(1));
        assertEquals(2, bucket.getBucketIndex(10));
        assertEquals(3, bucket.getBucketIndex(50));
        assertEquals(4, bucket.getBucketIndex(100));
        assertEquals(5, bucket.getBucketIndex(1000));
        assertEquals(5, bucket.getBucketIndex(1001));
        assertEquals(5, bucket.getBucketIndex(1000000));
    }

    @Test
    void testGetBucketNames() {
        DistributionBucket bucket = new DistributionBucket(new int[]{0, 10, 50, 100, 1000});
        String[] expected = {"underflow", "0_10", "10_50", "50_100", "100_1000", "overflow"};
        assertArrayEquals(expected, bucket.getBucketNames());
    }
}
