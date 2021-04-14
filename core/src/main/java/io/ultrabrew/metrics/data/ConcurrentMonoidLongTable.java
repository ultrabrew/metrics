// Copyright 2018, Oath Inc.
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

/**
 * A simple thread-safe linear probing hash table to be used for a monoid operation to aggregate
 * measurement values.
 *
 * <p>The hash table stores each tag set as a separate record within the hash table. When {@link
 * #apply(String[], long, long)} is called, the hash table will use the {@link #index(String[],
 * boolean)} method to find any existing record or create a new record if none found. If a new
 * record is created it will be initialized with initialized {@link #identity} to store the monoid
 * identity in the left hand value, before applying the monoid binary operation with {@link
 * #combine(long[], long, long)}.</p>
 *
 * <p>For performance reasons, the monoid implementations may choose to avoid atomic operation on
 * the whole record, and may apply the binary operation or setting the identity for each field in
 * the record separately. This may cause small inaccuracies in the combined values, if two or more
 * threads are simultaneously setting the identity and applying the binary operation. It is
 * guaranteed that only one thread sets the identity at a time, but simultaneous applications of
 * binary operations are not prevented during setting the identity. Similarly, when iterating the
 * hash table with cursor, the reading and writing may interleave, which may cause slight
 * inaccuracy.</p>
 *
 * <p>The monoid implementation may choose to ignore some tags by overriding the {@link
 * #hashCode(String[])} method to skip any tag key-value pairs the aggregator is not interested
 * on.</p>
 *
 * <p>All implementing classes <b>MUST</b> be thread-safe.</p>
 */
public abstract class ConcurrentMonoidLongTable implements Aggregator {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentMonoidLongTable.class);

  /**
   * Unsafe class for atomic operations on the hash table.
   */
  private static final Unsafe unsafe = UnsafeHelper.unsafe;

  private static final int TAGSETS_MAX_INCREMENT = 131072; // 128k

  private static final int RESERVED_FIELDS = 2;
  private static final long usedOffset;

  private static final int DEFAULT_INITIAL_CAPACITY = 16;
  private static final float DEFAULT_LOAD_FACTOR = 0.7f;

  // Certainty required to meet the spec of probablePrime
  private static final int DEFAULT_PRIME_CERTAINTY = 100;

  private static final long TABLE_MASK = 0x0FFFFFFF00000000L;
  private static final long SLOT_MASK = 0x00000000FFFFFFFFL;
  private static final long NOT_FOUND = 0x1000000000000000L;


  ///CLOVER:OFF
  // Turning off clover because Unsafe can't be safely mocked without crashing or otherwise
  // hindering JVM, and Class can't be mocked
  static {
    try {
      usedOffset = unsafe
          .objectFieldOffset(ConcurrentMonoidLongTable.class.getDeclaredField("used"));
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
  }
  ///CLOVER:ON


  /**
   * Identifier of the metric this hash table is associated with.
   */
  public final String metricId;

  /**
   * The multiple long array objects to manipulate with the unsafe atomic operations.
   */
  private List<long[]> tables;

  /**
   * Number of entries in the corresponding table in {@link #tables}
   */
  private List<AtomicInteger> recordCounts;

  /**
   * Max number of entries allowed in the corresponding table in {@link #tables}.
   */
  private List<Integer> tableCapacities;

  /**
   * The monoid's identity
   */
  private final long[] identity;

  /**
   * An array of tag sets currently contained within the hash table.
   */
  private volatile String[][] tagSets;

  private final int recordSize;
  private final String[] fields;
  private final Type[] types;
  private final int maxCapacity;
  private volatile int capacity;

  private volatile int used = 0;

  /**
   * Create a simple linear probing hash table for a monoid operation.
   * @param metricId identifier of the metric
   * @param maxCapacity maximum capacity of table in records.
   * @param initialCapacity requested capacity of table in records
   * @param fields sorted array of field names used in reporting
   * @param types type of each corresponding field
   * @param identity monoid's identity, where each value is corresponding to the given fields names
   */
  protected ConcurrentMonoidLongTable(final String metricId, final int maxCapacity,
      int initialCapacity, final String[] fields, final Type[] types, final long[] identity) {

    if (fields.length == 0 || fields.length != identity.length) {
      throw new IllegalArgumentException(
          "Fields and Identity must match in length and be non-zero");
    }
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("Illegal initial capacity");
    }
    if (maxCapacity < initialCapacity) {
      throw new IllegalArgumentException(
          "max capacity should be greater than the initial capacity");
    }
    if (initialCapacity == 0) {
      initialCapacity = DEFAULT_INITIAL_CAPACITY;
    }

    this.metricId = metricId;
    // Align to L1 cache line (64-byte)
    this.recordSize = (((fields.length + RESERVED_FIELDS) >> 3) + 1) << 3;
    this.fields = fields;
    this.types = types;
    this.identity = identity;
    this.maxCapacity = maxCapacity;
    this.capacity = initialCapacity;
    this.tables = new ArrayList<>();
    this.recordCounts = new ArrayList<>();
    this.tableCapacities = new ArrayList<>();
    this.tagSets = new String[initialCapacity][];
    addTable(initialCapacity, sizeTableFor(initialCapacity));
  }

  @Override
  public void apply(final String[] tags, final long value, final long timestamp) {

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
    long[] table = tables.get(tableIndex);

    final long base = Unsafe.ARRAY_LONG_BASE_OFFSET + slotIndex * Unsafe.ARRAY_LONG_INDEX_SCALE;
    unsafe.putLongVolatile(table, base + Unsafe.ARRAY_LONG_INDEX_SCALE, timestamp);

    combine(table, base, value);
  }

  @Override
  public Cursor cursor() {
    return new CursorImpl(tagSets, fields, types, false);
  }

  @Override
  public Cursor sortedCursor() {
    return new CursorImpl(tagSets, fields, types, true);
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

  /**
   * Execute the monoid binary operation on given value to the record with the given base offset in
   * the table.
   *
   * <p>The following methods have been provided to allow atomic thread-safe modification of fields
   * in the record</p>
   *
   * <ul>
   * <li>{@link #set(long[], long, long, long)} - Replace field's value with given value</li>
   * <li>{@link #add(long[], long, long, long)} - Add given value to field's existing value</li>
   * <li>{@link #min(long[], long, long, long)} - Replace field's value, if its larger than given
   * value</li>
   * <li>{@link #max(long[], long, long, long)} - Replace field's value, if its smaller than given
   * value</li>
   * </ul>
   *
   * @param table data container to be passed to the modification method
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param value right hand value to apply the monoid binary operation
   */
  protected abstract void combine(long[] table, final long baseOffset, final long value);


  /**
   * Find index of the record for the given key in the linear probing table.
   *
   * <p>When a slot in the table is taken, it will never be released nor changed.</p>
   *
   * @param tags key to use for table
   * @return index position in the table for the record. The 64 bit long value has two int values
   * encoded into it. The higher 32 bits represent the table index and the lower 32 bits represent
   * the slot index. And returns {@link #NOT_FOUND} if record not found.
   */
  long index(final String[] tags, final boolean isReading) {
    final long key = hashCode(tags);
    for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
      long[] table = tables.get(tableIndex);
      AtomicInteger recordCount = recordCounts.get(tableIndex);
      int tableCapacity = tableCapacities.get(tableIndex);
      final int slot = getSlot(key, table.length / recordSize);
      final int startIndex = slot * recordSize;
      int slotIndex = startIndex;
      for (; ; ) {
        long offset = Unsafe.ARRAY_LONG_BASE_OFFSET + slotIndex * Unsafe.ARRAY_LONG_INDEX_SCALE;
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

              // reset update timestamp
              unsafe.putLongVolatile(table, offset + Unsafe.ARRAY_LONG_INDEX_SCALE, 0L);
              // It is ok if we lose some data from other threads while writing identity
              for (int j = 0; j < identity.length; j++) {
                unsafe.putLongVolatile(table,
                    offset + (RESERVED_FIELDS + j) * Unsafe.ARRAY_LONG_INDEX_SCALE, identity[j]);
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
              tagSets[tagIndex] = Arrays.copyOf(tags, tags.length);

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
      LOGGER.error(
          "Maximum cardinality reached for metric: {} cardinality: {}", metricId, capacity);
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

  /**
   * @param tableCapacity, max number of entries allowed in this table
   * @param tableSize, actual table length. Which is around 30% more than the capacity.
   */
  private void addTable(final int tableCapacity, final int tableSize) {
    tables.add(new long[tableSize * recordSize]);
    recordCounts.add(new AtomicInteger());
    tableCapacities.add(tableCapacity);
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

  private int getSlot(final long key, final int tableSize) {
    int slot = Math.abs((int) key) % tableSize;
    return slot < 0 ? 0 : slot;
  }

  /**
   * Adds a new value to the existing value in the given field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value value to be added
   * @return new accumulated value of the field index
   */
  protected long add(final long[] table, final long baseOffset, final long index,
      final long value) {
    final long offset = baseOffset + (RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE;
    return unsafe.getAndAddLong(table, offset, value) + value;
  }

  /**
   * Adds a new double value to the existing value in the given field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value value to be added
   * @return new accumulated value of the field index
   */
  protected double add(final long[] table, final long baseOffset, final long index,
      final double value) {
    final long offset = ((RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE) + baseOffset;
    long old;
    double old_d, new_d;
    do {
      old = unsafe.getLongVolatile(table, offset);
      old_d = Double.longBitsToDouble(old);
      new_d = old_d + value;
      ///CLOVER:OFF
      // No reliable way to test without being able to mock unsafe
    } while (!unsafe.compareAndSwapLong(table, offset, old, Double.doubleToRawLongBits(new_d)));
    ///CLOVER:ON
    return new_d;
  }

  /**
   * Replaces the existing value in the given field index with the given value.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void set(final long[] table, final long baseOffset, final long index,
      final long value) {
    final long offset = baseOffset + (RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE;
    unsafe.putLongVolatile(table, offset, value);
  }

  /**
   * Replaces the existing double value in the given field index with the given value.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void set(final long[] table, final long baseOffset, final long index,
      final double value) {
    final long offset = baseOffset + (RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE;
    unsafe.putLongVolatile(table, offset, Double.doubleToLongBits(value));
  }

  /**
   * Set a new value as minimum if its lower than existing value in the given field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void min(final long[] table, final long baseOffset, final long index,
      final long value) {
    final long offset = baseOffset + (RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE;
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

  /**
   * Set a new double value as minimum if its lower than existing value in the given field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void min(final long[] table, final long baseOffset, final long index,
      final double value) {
    final long offset = baseOffset + (RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE;
    long old;
    double old_d;
    do {
      old = unsafe.getLong(table, offset);
      old_d = Double.longBitsToDouble(old);
      if (value >= old_d) {
        return;
      }
      ///CLOVER:OFF
      // No reliable way to test without being able to mock unsafe
    } while (!unsafe.compareAndSwapLong(table, offset, old, Double.doubleToRawLongBits(value)));
    ///CLOVER:ON
  }

  /**
   * Set a new value as maximum if its higher than existing value in the given field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void max(final long[] table, final long baseOffset, final long index,
      final long value) {
    final long offset = baseOffset + (RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE;
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
   * Set a new double value as maximum if its higher than existing value in the given field index.
   *
   * @param table the table containing the values
   * @param baseOffset base offset of the record in the table containing the left hand value
   * @param index index of the field
   * @param value new value
   */
  protected void max(final long[] table, final long baseOffset, final long index,
      final double value) {
    final long offset = baseOffset + (RESERVED_FIELDS + index) * Unsafe.ARRAY_LONG_INDEX_SCALE;
    long old;
    double old_d;
    do {
      old = unsafe.getLong(table, offset);
      old_d = Double.longBitsToDouble(old);
      if (value <= old_d) {
        return;
      }
      ///CLOVER:OFF
      // No reliable way to test without being able to mock unsafe
    } while (!unsafe.compareAndSwapLong(table, offset, old, Double.doubleToRawLongBits(value)));
    ///CLOVER:ON
  }

  /**
   * Filter any given tags and return a hash code
   *
   * @param tags a flattened array of tag key-value pairs
   * @return hash code
   */
  long hashCode(final String[] tags) {
    return Arrays.hashCode(tags);
  }

  private class CursorImpl implements Cursor {

    final private String[] fields;
    final private Type[] types;
    final private String[][] tagSets;
    private int i = -1;
    private long base = 0;
    private long[] table;

    private CursorImpl(final String[][] tagSets, final String[] fields, final Type[] types,
        final boolean sorted) {
      this.fields = fields;
      this.types = types;
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
      base = Unsafe.ARRAY_LONG_BASE_OFFSET + slotIndex * Unsafe.ARRAY_LONG_INDEX_SCALE;
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
      return unsafe.getLongVolatile(table, base + Unsafe.ARRAY_LONG_INDEX_SCALE);
    }

    @Override
    public long readLong(final int index) {
      if (i < 0 || i >= tagSets.length || tagSets[i] == null) {
        throw new IndexOutOfBoundsException("Not a valid row index: " + i);
      }
      if (index < 0 || index >= fields.length) {
        throw new IndexOutOfBoundsException("Not a valid field index: " + index);
      }
      return unsafe
          .getLongVolatile(table, base + (index + RESERVED_FIELDS) * Unsafe.ARRAY_LONG_INDEX_SCALE);
    }

    @Override
    public double readDouble(final int index) {
      return Double.longBitsToDouble(readLong(index));
    }

    @Override
    public long readAndResetLong(final int index) {
      if (i < 0 || i >= tagSets.length || tagSets[i] == null) {
        throw new IndexOutOfBoundsException("Not a valid row index: " + i);
      }
      if (index < 0 || index >= fields.length) {
        throw new IndexOutOfBoundsException("Not a valid field index: " + index);
      }
      return unsafe
          .getAndSetLong(table, base + (index + RESERVED_FIELDS) * Unsafe.ARRAY_LONG_INDEX_SCALE,
              identity[index]);
    }

    @Override
    public double readAndResetDouble(final int index) {
      return Double.longBitsToDouble(readAndResetLong(index));
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
