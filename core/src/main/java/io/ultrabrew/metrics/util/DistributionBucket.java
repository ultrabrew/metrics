package io.ultrabrew.metrics.util;

public class DistributionBucket {

    private static final String UNDERFLOW = "underflow";
    private static final String OVERFLOW = "overflow";

    private int[] buckets;

    public DistributionBucket(final int[] buckets) {
        this.buckets = buckets;
    }

    public int getCount() {
        return buckets.length + 1; // includes the underflow and overflow buckets
    }

    public int getBucketIndex(int latency) {
        return binarySearch(latency);
    }

    public String[] getBucketNames() {
        int bucketCount = buckets.length;
        String[] names = new String[bucketCount + 1];
        int i = 0;
        names[i] = UNDERFLOW;
        for (; i < bucketCount - 1; i++) {
            names[i + 1] = Integer.toString(buckets[i]) + '_' + Integer.toString(buckets[i + 1]);
        }
        names[i + 1] = OVERFLOW;
        return names;
    }

    private int binarySearch(final int key) {

        int low = 0;
        int high = buckets.length - 1;

        int minVal = buckets[low];
        int maxVal = buckets[high];

        if (key >= maxVal) {
            return high + 1; // align with underflow
        }
        if (key < minVal) {
            return low;
        }

        int index = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = buckets[mid];
            int nextToMidVal = buckets[mid + 1];
            if (midVal <= key && key < nextToMidVal) {
                index = mid;
                break;
            } else if (key < midVal) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return index + 1; // align with underflow
    }

}
