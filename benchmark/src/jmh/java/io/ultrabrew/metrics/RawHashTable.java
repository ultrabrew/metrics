// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.lang.invoke.VarHandle;

public class RawHashTable {

  private static final int RECORD_SIZE = 8; // align to 64-byte cache line
  private static final VarHandle USED;
  private static final VarHandle SCAN_LENGTH;
  private static final VarHandle TABLE;


  static {
    try {
      MethodHandles.Lookup lookup = MethodHandles.lookup();
      USED = MethodHandles.privateLookupIn(RawHashTable.class, lookup)
          .findVarHandle(RawHashTable.class, "used", int.class);
      SCAN_LENGTH = MethodHandles.privateLookupIn(RawHashTable.class, lookup)
          .findVarHandle(RawHashTable.class, "scanLength", int.class);
      TABLE = MethodHandles.arrayElementVarHandle(long[].class);
    } catch (NoSuchFieldException | IllegalAccessException e) {
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
    final int base = index(hashCode);

    TABLE.getAndAdd(table, base + 1, 1L);
    TABLE.getAndAdd(table, base + 2, value);
    min(base + 3, value);
    max(base + 4, value);
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
    return (int) USED.getVolatile(this);
  }

  public int capacity() {
    return capacity;
  }

  public int lastScanLength() {
    return (int) SCAN_LENGTH.getVolatile(this);
  }

  private long getLong(final String[] tags, int offset) {
    final long hashCode = Arrays.hashCode(tags);
    final int base = index(hashCode);

    return (long) TABLE.getVolatile(table, base + offset);
  }

  private void min(final long offset, final long value) {
    long old;
    do {
      old = (long) TABLE.getVolatile(table, offset);
      if (value >= old) {
        return;
      }
      ///CLOVER:OFF
      // No reliable way to test without being able to mock unsafe
    } while (!TABLE.compareAndSet(table, offset, old, value));
    ///CLOVER:ON
  }

  private void max(final long offset, final long value) {
    long old;
    do {
      old = (long) TABLE.getVolatile(table, offset);
      if (value <= old) {
        return;
      }
      ///CLOVER:OFF
      // No reliable way to test without being able to mock unsafe
    } while (!TABLE.compareAndSet(table, offset, old, value));
    ///CLOVER:ON
  }

  int index(final long key) {
    int start = (Math.abs((int) key) % capacity);
    int i = start < 0 ? 0 : start * RECORD_SIZE;
    boolean failSafe = false;

    for (int counter = 1; ; counter++) {

      final long offset = i;

      // check if we found our key
      final long candidate = (long) TABLE.getVolatile(table, offset);
      if (key == candidate) {
        SCAN_LENGTH.setVolatile(this, counter);
        return i;
      }

      // check if we found empty slot
      if (0L == candidate) {
        // try to reserve it

        ///CLOVER:OFF
        // No reliable way to test without being able to mock unsafe
        if (TABLE.compareAndSet(table, offset, 0L, key)) {
          ///CLOVER:ON

          final int unused = (int) USED.getAndAdd(this, 1) + 1;
          TABLE.setVolatile(table, offset + 3, Long.MAX_VALUE);
          TABLE.setVolatile(table, offset + 4, Long.MIN_VALUE);

          SCAN_LENGTH.setVolatile(this, counter);
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
