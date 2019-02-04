// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import io.ultrabrew.metrics.data.UnsafeHelper;
import java.util.Arrays;
import sun.misc.Unsafe;

public class RawHashTable {

  private static final Unsafe unsafe = UnsafeHelper.unsafe;
  private static final int RECORD_SIZE = 8; // align to 64-byte cache line
  private static final long usedOffset;
  private static final long scanLengthOffset;

  static {
    try {
      usedOffset = unsafe.objectFieldOffset(RawHashTable.class.getDeclaredField("used"));
      scanLengthOffset = unsafe
          .objectFieldOffset(RawHashTable.class.getDeclaredField("scanLength"));
    } catch (NoSuchFieldException e) {
      throw new Error(e);
    }
  }

  private final long[] table;
  private final int capacity;
  private int used = 0;
  private int scanLength = 0;

  public RawHashTable(final int capacity) {
    this.capacity = capacity;
    table = new long[this.capacity * RECORD_SIZE];
  }

  public void put(final String[] tags, final long value) {
    final long hashCode = Arrays.hashCode(tags);
    final int i = index(hashCode);
    final long base = Unsafe.ARRAY_LONG_BASE_OFFSET + i * Unsafe.ARRAY_LONG_INDEX_SCALE;

    unsafe.getAndAddLong(table, base + Unsafe.ARRAY_LONG_INDEX_SCALE, 1L);
    unsafe.getAndAddLong(table, base + 2 * Unsafe.ARRAY_LONG_INDEX_SCALE, value);
    min(base + 3 * Unsafe.ARRAY_LONG_INDEX_SCALE, value);
    max(base + 4 * Unsafe.ARRAY_LONG_INDEX_SCALE, value);
  }

  public long getCount(final String[] tags) {
    return getLong(tags, 1);
  }

  public long getSum(final String[] tags) {
    return getLong(tags, 2);
  }

  public long getMin(final String[] tags) {
    return getLong(tags, 3);
  }

  public long getMax(final String[] tags) {
    return getLong(tags, 4);
  }

  public int size() {
    return unsafe.getIntVolatile(this, usedOffset);
  }

  public int capacity() {
    return capacity;
  }

  public int lastScanLength() {
    return unsafe.getIntVolatile(this, scanLengthOffset);
  }

  private long getLong(final String[] tags, int offset) {
    final long hashCode = Arrays.hashCode(tags);
    final int i = index(hashCode);
    final long base = Unsafe.ARRAY_LONG_BASE_OFFSET + i * Unsafe.ARRAY_LONG_INDEX_SCALE;

    return unsafe.getLongVolatile(table, base + offset * Unsafe.ARRAY_LONG_INDEX_SCALE);
  }

  private void min(final long offset, final long value) {
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

  private void max(final long offset, final long value) {
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

  int index(final long key) {
    int start = (Math.abs((int) key) % capacity);
    int i = start < 0 ? 0 : start * RECORD_SIZE;
    boolean failSafe = false;

    for (int counter = 1; ; counter++) {

      final long offset = Unsafe.ARRAY_LONG_BASE_OFFSET + i * Unsafe.ARRAY_LONG_INDEX_SCALE;

      // check if we found our key
      final long candidate = unsafe.getLongVolatile(table, offset);
      if (key == candidate) {
        unsafe.putIntVolatile(this, scanLengthOffset, counter);
        return i;
      }

      // check if we found empty slot
      if (0L == candidate) {
        // try to reserve it

        ///CLOVER:OFF
        // No reliable way to test without being able to mock unsafe
        if (unsafe.compareAndSwapLong(table, offset, 0L, key)) {
          ///CLOVER:ON

          final int localUsed = unsafe.getAndAddInt(this, usedOffset, 1) + 1;
          unsafe.putLongVolatile(table, offset + 3 * Unsafe.ARRAY_LONG_INDEX_SCALE, Long.MAX_VALUE);
          unsafe.putLongVolatile(table, offset + 4 * Unsafe.ARRAY_LONG_INDEX_SCALE, Long.MIN_VALUE);

          unsafe.putIntVolatile(this, scanLengthOffset, counter);
          return i;
        }
      } else {
        // go to next record
        i += RECORD_SIZE;
        if (i >= table.length) {
          if (failSafe) {
            throw new IllegalStateException("No more space in linear probing table");
          } else {
            i = 0;
            failSafe = true;
          }
        }
      }
    }
  }
}
