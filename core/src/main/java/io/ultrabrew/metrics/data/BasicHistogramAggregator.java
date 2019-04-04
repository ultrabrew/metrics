// Copyright 2019, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import static io.ultrabrew.metrics.Metric.DEFAULT_CARDINALITY;
import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;

import io.ultrabrew.metrics.util.DistributionBucket;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

/**
 * A monoid to generate histogram buckets along with the common aggregation functions for a given
 * metric.
 *
 * <p>Performs the following aggregation functions on the measurements:</p>
 * <ul>
 * <li>count of measurements</li>
 * <li>sum of the measurement values</li>
 * <li>minimum measured value</li>
 * <li>maximum measured value</li>
 * <li>last measured value</li>
 * </ul>
 *
 * The histogram buckets are derived from {@link DistributionBucket}.
 * <p>A sample histogram would look like:</p>
 * <ul>
 * <li>[0_10)</li>
 * <li>[10_100)</li>
 * <li>[100_500)</li>
 * <li>overflow</li>
 * <li>underflow</li>
 * </ul>
 *
 * @see DistributionBucket
 */
public class BasicHistogramAggregator extends ConcurrentMonoidIntTable implements Aggregator {

  private static final Logger LOGGER = LoggerFactory.getLogger(BasicHistogramAggregator.class);
  private static final String[] AGG_FIELDS = {"count", "sum", "min", "max", "lastValue"};
  private static final long[] IDENTITY = {0L, 0L, Long.MAX_VALUE, Long.MIN_VALUE, 0L};

  private final String metricId;
  private final DistributionBucket buckets;
  private final int bucketCount;

  private final String[] fields;
  private final Type[] types;
  private final long[] identity;

  /**
   * Creates a monoid for the histogram buckets for a metric.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param buckets distribution bucket spec
   */
  public BasicHistogramAggregator(final String metricId, final DistributionBucket buckets) {
    this(metricId, buckets, DEFAULT_CARDINALITY, DEFAULT_MAX_CARDINALITY);
  }

  /**
   * Creates a monoid for the histogram buckets for a metric.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param buckets distribution bucket spec
   * @param cardinality requested capacity of table in records, actual capacity may be higher
   */
  public BasicHistogramAggregator(final String metricId, final DistributionBucket buckets,
      final int cardinality) {
    this(metricId, buckets, cardinality, DEFAULT_MAX_CARDINALITY);
  }

  /**
   * Creates a monoid for the histogram buckets for a metric.
   *
   * @param metricId identifier of the metric associated with this aggregator
   * @param buckets distribution bucket spec
   * @param cardinality requested capacity of table in records, actual capacity may be higher
   * @param maxCardinality requested max capacity of table in records. Table doesn't grow beyond
   * this value.
   */
  public BasicHistogramAggregator(final String metricId, final DistributionBucket buckets,
      final int cardinality, final int maxCardinality) {
    super(AGG_FIELDS.length + buckets.getCount(), cardinality, maxCardinality, IDENTITY);
    this.metricId = metricId;
    this.buckets = buckets;
    this.bucketCount = buckets.getCount();
    this.fields = buildFields();
    this.types = buildTypes(fields.length);
    this.identity = buildIdentity(fields.length);
  }

  @Override
  public void apply(final String[] tags, final long value, final long timestamp) {
    super.apply(tags, value, timestamp);
  }

  @Override
  protected void combine(final int[] table, final long baseOffset, final long value) {
    addToAggField(table, baseOffset, 0, 1);
    addToAggField(table, baseOffset, 1, value);
    minAggField(table, baseOffset, 2, value);
    maxAggField(table, baseOffset, 3, value);
    setAggField(table, baseOffset, 4, value);

    //Increments the bucket counter by 1 responding to the given value
    int bucketIndex = buckets.getBucketIndex(value);
    addToDataField(table, baseOffset, AGG_FIELDS.length + bucketIndex, 1);
  }

  @Override
  public Cursor cursor() {
    return newCursor(false);
  }

  @Override
  public Cursor sortedCursor() {
    return newCursor(true);
  }

  private Cursor newCursor(boolean sorted) {
    return new CursorImpl(tagSets, fields, types, identity, sorted);
  }

  private long[] buildIdentity(int length) {
    long[] identity = new long[length];
    System.arraycopy(IDENTITY, 0, identity, 0, IDENTITY.length);
    return identity;
  }

  private String[] buildFields() {
    String[] fields = new String[AGG_FIELDS.length + bucketCount];
    String[] buckets = this.buckets.getBucketNames();
    System.arraycopy(AGG_FIELDS, 0, fields, 0, AGG_FIELDS.length);
    System.arraycopy(buckets, 0, fields, AGG_FIELDS.length, buckets.length);
    return fields;
  }

  private Type[] buildTypes(final int length) {
    Type[] types = new Type[length];
    Arrays.fill(types, Type.LONG);
    return types;
  }

  private class CursorImpl implements Cursor {

    private final String[] fields;
    private final Type[] types;
    private final long[] identity;
    private final String[][] tagSets;
    private int i = -1;
    private long base = 0;
    private int[] table;

    private CursorImpl(final String[][] tagSets, final String[] fields, final Type[] types,
        long[] identity, final boolean sorted) {
      this.fields = fields;
      this.types = types;
      this.identity = identity;
      if (sorted) {
        this.tagSets = tagSets.clone();
        Arrays.sort(this.tagSets, TagSetsHelper::compare);
      } else {
        this.tagSets = tagSets;
      }
    }

    @Override
    public String getMetricId() {
      return metricId;
    }

    @Override
    public boolean next() {
      i++;
      if (i >= tagSets.length || tagSets[i] == null) {
        return false;
      }

      long index = index(tagSets[i], true);

      if (NOT_FOUND == index) {
        LOGGER.error("Missing index on Read. Tags: {}. Concurrency error or bug",
            Arrays.asList(tagSets[i]));
        return false;
      }

      // Decode table index and slot index from long.
      // Upper 32 bits represent the table index and lower 32 bits represent the slot index.
      // This logic is replicated in multiple places for performance reasons.
      int tableIndex = (int) ((index & TABLE_MASK) >> 32);
      int slotIndex = (int) (index & SLOT_MASK);

      table = tables.get(tableIndex);
      base = Unsafe.ARRAY_INT_BASE_OFFSET + slotIndex * Unsafe.ARRAY_INT_INDEX_SCALE;
      return true;
    }

    @Override
    public String[] getTags() {
      if (i < 0 || i >= tagSets.length || tagSets[i] == null) {
        throw new IndexOutOfBoundsException("Not a valid row index: " + i);
      }
      return tagSets[i];
    }

    @Override
    public long lastUpdated() {
      if (i < 0 || i >= tagSets.length || tagSets[i] == null) {
        throw new IndexOutOfBoundsException("Not a valid row index: " + i);
      }
      return readTime(table, base);
    }

    @Override
    public long readLong(final int index) {
      if (i < 0 || i >= tagSets.length || tagSets[i] == null) {
        throw new IndexOutOfBoundsException("Not a valid row index: " + i);
      }
      if (index < 0 || index >= fields.length) {
        throw new IndexOutOfBoundsException("Not a valid field index: " + index);
      }
      return read(table, base, index);
    }

    @Override
    public double readDouble(final int index) {
      throw new UnsupportedOperationException("Invalid operation");
    }

    @Override
    public long readAndResetLong(final int index) {
      if (i < 0 || i >= tagSets.length || tagSets[i] == null) {
        throw new IndexOutOfBoundsException("Not a valid row index: " + i);
      }
      if (index < 0 || index >= fields.length) {
        throw new IndexOutOfBoundsException("Not a valid field index: " + index);
      }
      return readAndReset(table, base, index, identity[index]);
    }

    @Override
    public double readAndResetDouble(final int index) {
      throw new UnsupportedOperationException("Invalid operation");
    }

    @Override
    public String[] getFields() {
      return fields;
    }

    @Override
    public Type[] getTypes() {
      return types;
    }
  }
}
