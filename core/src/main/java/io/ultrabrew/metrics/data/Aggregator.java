// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

/**
 * Aggregator is a thread-safe aggregator for a single metric's measurement values. It may use
 * multiple different aggregation functions to calculate multiple fields on the given values. For
 * example, same aggregator can calculate the min, max, average and sum of given values. Aggregator
 * may be dependent on the time interval of reporter, i.e. when reading a field, it may reset it, or
 * the implementation might update the current time interval values, but allow read only to the last
 * time interval's aggregates.
 */
public interface Aggregator {

  /**
   * Apply aggregation functions to given value identified with a given a tag set. An aggregator may
   * ignore some or all of the tag key-value pairs to reduce cardinality.
   *
   * @param tags a sorted array of tag key-value pairs in a flattened array or an empty array
   * @param value measurement value
   * @param timestamp update time, measured in milliseconds since midnight, January 1, 1970 UTC.
   */
  void apply(final String[] tags, final long value, final long timestamp);

  /**
   * Retrieve a cursor to iterate all rows in the aggregator.
   *
   * @return a cursor
   */
  Cursor cursor();

  /**
   * Retrieve a sorted cursor to iterate all rows in the aggregator.
   *
   * @return a cursor sorted lexically by the tag sets
   */
  Cursor sortedCursor();
}
