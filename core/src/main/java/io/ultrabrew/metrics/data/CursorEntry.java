// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.data;

public interface CursorEntry {

  /**
   * Retrieve the metric identifier.
   *
   * @return identifier of the metric
   */
  String getMetricId();

  /**
   * Retrieves tags in the current row.
   *
   * @return a sorted array of tag key-value pairs in a flattened array
   */
  String[] getTags();

  /**
   * Retrieves the time when the current row was last updated.
   *
   * @return last updated time, measured in milliseconds since midnight, January 1, 1970 UTC.
   */
  long lastUpdated();
  
  /**
   * 
   * Resets the current row to default value (0) so it can be reused for a different field
   * 
   * @return the tag set that's marked for release, null if the current row can not be released
   * 
   */
  String[] freeCurrentRow();

  /**
   * Retrieves a long value for a field value at given index.
   *
   * @param index index of the field
   * @return value of the field
   */
  long readLong(final int index);

  /**
   * Retrieves a double value for a field value at given index.
   *
   * @param index index of the field
   * @return value of the field
   */
  double readDouble(final int index);

  /**
   * Retrieves a long value for a field value at given index, and resets the field's value to
   * monoid's identity.
   *
   * @param index index of the field
   * @return value of the field
   */
  long readAndResetLong(final int index);

  /**
   * Retrieves a double value for a field value at given index, and resets the field's value to
   * monoid's identity.
   *
   * @param index index of the field
   * @return value of the field
   */
  double readAndResetDouble(final int index);

  /**
   * Retrieves fields available in all rows.
   *
   * <p>The fields names and values are always in same order, and you can use the index of the
   * field name array to retrieve the corresponding value of the field.</p>
   *
   * @return a sorted array of field names
   */
  String[] getFields();

  /**
   * Retrieves types of the fields available is all rows.
   *
   * <p>The types and fields are always in same order, and you can use the index of the type array
   * to retrieve the corresponding field of the row.</p>
   *
   * @return a sorted array of type of fields
   */
  Type[] getTypes();
}
