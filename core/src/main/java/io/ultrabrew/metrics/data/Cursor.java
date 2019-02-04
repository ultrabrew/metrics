// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

/**
 * A Cursor object used to iterate the contents of an aggregator.
 *
 * <p>Initially the cursor is positioned before the first row. The next method moves the cursor to
 * the next row, and
 * because it returns false when there are no more rows in the Cursor object, it can be used in a
 * while loop to iterate through the contents.</p>
 *
 * <p>A cursor instance <b>IS NOT</b> thread-safe and should be used only from a single thread.
 * Multiple cursor instances may
 * be used in their own threads.</p>
 */
public interface Cursor extends CursorEntry {

  /**
   * Moves the cursor forward one row from its current position.
   *
   * <p>A cursor is initially positioned before the first row; the first call to the method next
   * makes the
   * first row the current row; the second call makes the second row the current row, and so
   * on.</p>
   *
   * @return true if the new current row is valid; false if there are no more rows
   */
  boolean next();
}
