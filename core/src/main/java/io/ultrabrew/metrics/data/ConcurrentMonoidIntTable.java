// Copyright 2019, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.Unsafe;

public abstract class ConcurrentMonoidIntTable {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentMonoidIntTable.class);

  // Certainty required to meet the spec of probablePrime
  private static final int DEFAULT_PRIME_CERTAINTY = 100;
  private static final int DEFAULT_INITIAL_CAPACITY = 16;
  private static final float DEFAULT_LOAD_FACTOR = 0.7f;
  private static final int TAGSETS_MAX_INCREMENT = 131072; // 128k
  private static final int RESERVED_FIELDS = 2;
  protected static final long TABLE_MASK = 0x0FFFFFFF00000000L;
  protected static final long SLOT_MASK = 0x00000000FFFFFFFFL;
  protected static final long NOT_FOUND = 0x1000000000000000L;
  protected static final Unsafe unsafe = UnsafeHelper.unsafe;
  private static final long usedOffset;

  ///CLOVER:OFF
  // Turning off clover because Unsafe can't be safely mocked without crashing or otherwise
  // hindering JVM, and Class can't be mocked
  static {
    try {
      usedOffset = unsafe
          .objectFieldOffset(ConcurrentMonoidIntTable.class.getDeclaredField("used"));
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
  }
  ///CLOVER:ON

  /**
   * The multiple int array objects to manipulate with the unsafe atomic operations.
   */
  protected List<int[]> tables;
  /**
   * Number of entries in the corresponding table in {@link #tables}
   */
  private List<AtomicInteger> recordCounts;
  /**
   * Max number of entries allowed in the corresponding table in {@link #tables}.
   */
  private final List<Integer> tableCapacities;
  private final int recordSize;
  private final int maxCapacity;
  private final long[] identity;
  private final int numAggFields;

  private volatile int capacity;
  protected volatile String[][] tagSets;
  private volatile int used = 0;

  /**
   *  @param recordSize number of fields in a record
   * @param maxCapacity maximum capacity of table in records. Table doesn't grow beyond this value.
   * @param initialCapacity requested capacity of table in records
   * @param identity monoid's identity for the agg fields
   */
  protected ConcurrentMonoidIntTable(final int recordSize, final int maxCapacity, int initialCapacity,
      final long[] identity) {
    this(identity.length, recordSize - identity.length, maxCapacity, initialCapacity, identity);
  }

  private ConcurrentMonoidIntTable(final int numAggFields, final int dataSize,
      final int maxCapacity, int initialCapacity, final long[] identity) {

    assert initialCapacity >= 0 : "Illegal initial capacity";
    assert maxCapacity >= initialCapacity : "max capacity should be greater than the initial capacity";

    if (initialCapacity == 0) {
      initialCapacity = DEFAULT_INITIAL_CAPACITY;
    }
    this.numAggFields = numAggFields;
    final int numInts = (RESERVED_FIELDS + numAggFields) * 2 + dataSize;
    // Align to L1 cache line (64-byte)
    this.recordSize = ((int) Math.ceil(numInts / 16.0)) << 4;
    this.capacity = initialCapacity;
    this.maxCapacity = maxCapacity;
    this.identity = identity.clone();
    this.tables = new ArrayList<>();
    this.recordCounts = new ArrayList<>();
    this.tableCapacities = new ArrayList<>();
    this.tagSets = new String[initialCapacity][];
    addTable(initialCapacity, sizeTableFor(initialCapacity));
  }

  /**
   * Adds extra 30% and round it the next probable prime to avoid performance degradation of the
   * linear probing table.
   */
  private int sizeTableFor(final int capacity) {
    int tableSize = (int) (capacity * (1 + (1 - DEFAULT_LOAD_FACTOR)));
    BigInteger bigInteger = BigInteger.valueOf(tableSize);
    if (!bigInteger.isProbablePrime(DEFAULT_PRIME_CERTAINTY)) {
      tableSize = bigInteger.nextProbablePrime().intValueExact();
    }
    return tableSize;
  }

  /**
   * @param tableCapacity, max number of entries allowed in this table
   * @param tableSize, actual table length. Which is around 30% more than the capacity.
   */
  private void addTable(final int tableCapacity, final int tableSize) {
    tables.add(new int[tableSize * recordSize]);
    recordCounts.add(new AtomicInteger());
    tableCapacities.add(tableCapacity);
  }

  /**
   * Calculates the aggregations for the histogram
   *
   * @param table the table containing the histogram
   * @param baseOffset base offset of the record in the table
   * @param value corresponds to a specific bucket
   */
  protected abstract void combine(int[] table, final long baseOffset, final long value);

  protected void apply(final String[] tags, final long value, final long timestamp) {

    final long index = index(tags, false);

    // Failed to grow table, silently drop the measurement
    if (index == NOT_FOUND) {
      return;
    }

    // Decode table index and slot index from a long.
    // Upper 32 bits represent the table index and lower 32 bits represent the slot index.
    // This logic is replicated in multiple places for performance reasons.
    int tableIndex = (int) ((index & TABLE_MASK) >> 32);
    int slotIndex = (int) (index & SLOT_MASK);
    int[] table = tables.get(tableIndex);

    final long base = Unsafe.ARRAY_INT_BASE_OFFSET + slotIndex * Unsafe.ARRAY_INT_INDEX_SCALE;
    unsafe.putLongVolatile(table, base + Unsafe.ARRAY_LONG_INDEX_SCALE, timestamp);

    combine(table, base, value);
  }

  /**
   * Retrieves the value of the field at a given index
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table
   * @return accumulated value of the field at the given index
   */
  protected long readTime(final int[] table, final long baseOffset) {
    return unsafe.getLongVolatile(table, baseOffset + Unsafe.ARRAY_LONG_INDEX_SCALE);
  }

  /**
   * Retrieves the value of the field at a given index
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table
   * @param index index of the field
   * @return accumulated value of the field at the given index
   */
  protected long read(final int[] table, final long baseOffset, final int index) {
    if (index < numAggFields) {
      return readAggField(table, baseOffset, index);
    } else {
      return readDataField(table, baseOffset, index);
    }
  }

  /**
   * Retrieves the value of the field at a given index
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table
   * @param index index of the field
   * @param identity monoid's identity
   * @return accumulated value of the field at the given index
   */
  protected long readAndReset(final int[] table, final long baseOffset, final int index, long identity) {
    if (index < numAggFields) {
      return readAndResetAggField(table, baseOffset, index, identity);
    }else {
      return readAndResetDataField(table, baseOffset, index, (int) identity);
    }
  }

  private long getAggFieldOffset(long baseOffset, int index) {
    return baseOffset + (RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE;
  }

  private long getDataFieldOffset(long baseOffset, int index) {
    return baseOffset + (RESERVED_FIELDS + numAggFields) * Unsafe.ARRAY_LONG_INDEX_SCALE
        + (index - numAggFields) * Unsafe.ARRAY_INT_INDEX_SCALE;
  }

  private long readAggField(final int[] table, final long baseOffset, final int index) {
    final long offset = getAggFieldOffset(baseOffset, index);
    return unsafe.getLongVolatile(table, offset);
  }

  private int readDataField(final int[] table, final long baseOffset, final int index) {
    final long offset = getDataFieldOffset(baseOffset, index);
    return unsafe.getIntVolatile(table, offset);
  }

  private int readAndResetDataField(int[] table, long baseOffset, int index, int identity) {
    final long offset = getDataFieldOffset(baseOffset, index);
    return unsafe.getAndSetInt(table, offset, identity);
  }

  private long readAndResetAggField(int[] table, long baseOffset, int index, long identity) {
    final long offset = getAggFieldOffset(baseOffset, index);
    return unsafe.getAndSetLong(table, offset, identity);
  }

  /**
   * Adds a new value to the existing value in the given data field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value value to be added
   * @return new accumulated value of the field index
   */
  protected int addToDataField(final int[] table, final long baseOffset, final int index, final int value) {
    final long offset = getDataFieldOffset(baseOffset, index);
    return unsafe.getAndAddInt(table, offset, value) + value;
  }

  /**
   * Adds a new value to the existing value in the given agg field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value value to be added
   * @return new accumulated value of the field index
   */
  protected long addToAggField(final int[] table, final long baseOffset, final int index, final long value) {
    final long offset = getAggFieldOffset(baseOffset, index);
    return unsafe.getAndAddLong(table, offset, value) + value;
  }

  /**
   * Set a new value as maximum if its higher than existing value in the given agg field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void maxAggField(final int[] table, final long baseOffset, final int index, final long value) {
    final long offset = getAggFieldOffset(baseOffset, index);
    long old;
    do {
      old = unsafe.getLong(table, offset);
      if (value <= old) {
        return;
      }
      ///CLOVER:OFF
      // No reliable way to test without being able to mock unsafe
    } while (!unsafe.compareAndSwapLong(table, offset, old, value));
    ///CLOVER:ON
  }

  /**
   * Replaces the existing value in the given agg field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void setAggField(final int[] table, final long baseOffset, final int index, final long value) {
    final long offset = getAggFieldOffset(baseOffset, index);
    unsafe.putLongVolatile(table, offset, value);
  }

  /**
   * Set a new value as minimum if its lower than existing value in the given agg field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void minAggField(final int[] table, final long baseOffset, final int index, final long value) {
    final long offset = getAggFieldOffset(baseOffset, index);
    long old;
    do {
      old = unsafe.getLong(table, offset);
      if (value >= old) {
        return;
      }
      ///CLOVER:OFF
      // No reliable way to test without being able to mock unsafe
    } while (!unsafe.compareAndSwapLong(table, offset, old, value));
    ///CLOVER:ON
  }

  protected long index(String[] tags, boolean isReading) {
    final long key = hashCode(tags);
    for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
      int[] table = tables.get(tableIndex);
      AtomicInteger recordCount = recordCounts.get(tableIndex);
      int tableCapacity = tableCapacities.get(tableIndex);
      final int slot = getSlot(key, table.length / recordSize);
      final int startIndex = slot * recordSize;
      int slotIndex = startIndex;
      for (; ; ) {
        long offset = Unsafe.ARRAY_INT_BASE_OFFSET + slotIndex * Unsafe.ARRAY_INT_INDEX_SCALE;
        long candidate = unsafe.getLongVolatile(table, offset);

        // check if we found our key
        if (key == candidate) {
          // Encode table index and slot index into a long.
          // Upper 32 bits represent the table index and lower 32 bits represent the slot index.
          // This logic is replicated in multiple places for performance reasons.
          return ((long) tableIndex) << 32 | ((long) slotIndex);
        }

        boolean emptySlot = 0L == candidate;

        if (emptySlot) {

          if (isReading) {
            break; // If the slot is empty while reading, skip to the next table.
          } else if (recordCount.get() >= tableCapacity) {
            break; // we're writing but the table is 70% full
          } else {
            ///CLOVER:OFF
            // No reliable way to test without being able to mock unsafe
            if (unsafe.compareAndSwapLong(table, offset, 0L, key)) { // try to reserve it
              ///CLOVER:ON

              //increment the record count
              recordCount.incrementAndGet();

              // reset timestamp
              unsafe.putLongVolatile(table, offset + Unsafe.ARRAY_LONG_INDEX_SCALE, 0L);
              // It is ok if we lose some data from other threads while writing identity
              for (int i = 0; i < identity.length; i++) {
                unsafe.putLongVolatile(table,
                    offset + (RESERVED_FIELDS + i) * Unsafe.ARRAY_LONG_INDEX_SCALE, identity[i]);
              }

              //increment the total size;
              int tagIndex = unsafe.getAndAddInt(this, usedOffset, 1);
              if (tagIndex >= tagSets.length) {
                // grow tag set
                synchronized (this) {
                  if (tagIndex >= tagSets.length) {
                    final int oldLength = tagSets.length;
                    final int newLength =
                        oldLength > TAGSETS_MAX_INCREMENT ? oldLength + TAGSETS_MAX_INCREMENT
                            : oldLength * 2;
                    tagSets = Arrays.copyOf(tagSets, newLength);
                  }
                }
              }

              // Store tags in the tag array for iteration purposes only
              tagSets[tagIndex] = tags;

              // Encode table index and slot index into a long.
              // Upper 32 bits represent the table index and lower 32 bits represent the slot index.
              // This logic is replicated in multiple places for performance reasons.
              return ((long) tableIndex) << 32 | ((long) slotIndex);
            }
          }
        } else {
          slotIndex += recordSize;
          if (slotIndex >= table.length) {
            slotIndex = 0;
          }
          if (slotIndex == startIndex) {
            break; // check the next table
          }
        }
      }
    }
    if (isReading) {
      return NOT_FOUND;
    } else {
      if (growTable()) {
        return index(tags, isReading);
      } else {
        return NOT_FOUND;
      }
    }
  }

  private boolean growTable() {

    if (capacity >= maxCapacity) {
      LOGGER.error("Maximum table capacity reached");
      return false;
    }

    synchronized (this) {
      int lastTableIndex = tables.size() - 1;
      int lastRecordCount = recordCounts.get(lastTableIndex).get();
      int lastTableCapacity = tableCapacities.get(lastTableIndex);
      if (lastRecordCount >= lastTableCapacity && capacity < maxCapacity) {
        int nextTableCapacity = nextTableCapacity(lastTableCapacity);
        int newCapacity = capacity + nextTableCapacity;
        addTable(nextTableCapacity, sizeTableFor(nextTableCapacity));
        capacity = newCapacity;
      }
    }
    return true;
  }

  private int nextTableCapacity(final int lastTableCapacity) {
    int nextTableCapacity;
    if (capacity < maxCapacity / 2) {
      nextTableCapacity = Math
          .min(lastTableCapacity << 1, maxCapacity - capacity); // double the size;
    } else {
      nextTableCapacity = Math.min(lastTableCapacity, maxCapacity - capacity); // grow linearly
    }
    return nextTableCapacity;
  }

  /**
   * Returns the hash code for a given tag set
   *
   * @param tags a flattened array of tag key-value pairs
   * @return hash code
   */
  long hashCode(final String[] tags) {
    return Arrays.hashCode(tags);
  }

  private int getSlot(final long key, final int tableSize) {
    int slot = Math.abs((int) key) % tableSize;
    return slot < 0 ? 0 : slot;
  }

  /**
   * Returns the number of elements in this hash table
   *
   * @return the number of elements in this hash table
   */
  public int size() {
    return unsafe.getInt(this, usedOffset);
  }

  /**
   * Returns the current maximum number of elements this hash table is able to store
   *
   * @return the current capacity in elements
   */
  public int capacity() {
    return capacity;
  }

}
