// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters;

import io.ultrabrew.metrics.data.Aggregator;
import io.ultrabrew.metrics.data.Cursor;
import io.ultrabrew.metrics.data.CursorEntry;
import io.ultrabrew.metrics.data.Type;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An aggregating SLF4J Logger reporter. This reporter uses SLF4J Logger with given name to log the
 * aggregated values of the metrics.
 *
 * <p>The log message format is: {@code "{tags}{tagFieldDelimiter}{fields} {metricId}"}, where</p>
 * <ul>
 * <li>{@code tags} is a list of {@code tagKey '=' tagValue} pairs delimited by given tag delimiter
 * (default is
 * {@code " "}).</li>
 * <li>{@code tagFieldDelimiter} separates the tags and fields by given delimiter (default is {@code
 * " "}.</li>
 * <li>{@code fields} is a list of {@code fieldName '=' fieldValue} pairs delimited by given field
 * delimiter
 * (default is {@code " "}).</li>
 * <li>{@code metricId} is the identifier of the metric.</li>
 * </ul>
 *
 * <p>This reporter <b>IS NOT</b> intended to be used in production environments, and is only
 * provided for debugging
 * purposes.</p>
 */
public class SLF4JReporter extends TimeWindowReporter {

  private Logger reporter;
  private CharSequence tagDelimiter;
  private CharSequence fieldDelimiter;
  private CharSequence tagFieldDelimiter;
  private long lastSeenTimestamp;

  /**
   * Create a SLF4J Logger reporter.
   *
   * @param name name of the logger
   */
  public SLF4JReporter(final String name) {
    this(name, " ", " ", " ", DEFAULT_WINDOW_STEP_SIZE_SEC);
  }

  /**
   * Create a SLF4J Logger reporter with a custom window size
   *
   * @param name name of the logger.
   * @param windowSizeSeconds window size in seconds
   */
  public SLF4JReporter(final String name, final int windowSizeSeconds) {
    this(name, " ", " ", " ", windowSizeSeconds);
  }

  /**
   * Create a SLF4J Logger reporter with given delimiters.
   *
   * @param name name of the logger
   * @param tagDelimiter delimiter to be used to join tag key-value pairs
   * @param fieldDelimiter delimiter to be used to join field name-value pairs
   * @param tagFieldDelimiter delimiter to be used to separate tags and fields
   * @param windowSizeSeconds window size in seconds
   */
  public SLF4JReporter(final String name, final CharSequence tagDelimiter,
      final CharSequence fieldDelimiter,
      final CharSequence tagFieldDelimiter, final int windowSizeSeconds) {

    super(name, windowSizeSeconds);

    reporter = LoggerFactory.getLogger(name);
    this.tagDelimiter = tagDelimiter;
    this.fieldDelimiter = fieldDelimiter;
    this.tagFieldDelimiter = tagFieldDelimiter;
    this.lastSeenTimestamp = 0;
    this.start();
  }


  /**
   * Manually force the reporter to output the current state of all the aggregators into the SLF4J
   * Logger.
   */
  @Override
  protected void doReport(Map<String, Aggregator> aggregators) {
    long newestTimestamp = 0;
    for (final Map.Entry<String, Aggregator> entry : aggregators.entrySet()) {
      final Aggregator aggregator = entry.getValue();
      final Cursor cursor = aggregator.cursor();
      final String metricName = entry.getKey();
      while (cursor.next()) {
        if (cursor.lastUpdated() > lastSeenTimestamp) {
          reporter.info("lastUpdated={} {}{}{} {}",
              cursor.lastUpdated(),
              formatTags(cursor.getTags()),
              tagFieldDelimiter,
              formatFields(cursor),
              metricName);
          newestTimestamp = Math.max(newestTimestamp, cursor.lastUpdated());
        }
      }
    }
    if (newestTimestamp > 0) {
      lastSeenTimestamp = newestTimestamp;
    }
  }

  private String formatTags(final String[] tags) {
    StringBuilder sb = null;

    for (int i = 0; i < tags.length; i += 2) {
      if (sb == null) {
        sb = new StringBuilder();
      } else {
        sb.append(tagDelimiter);
      }
      sb.append(tags[i]);
      sb.append('=');
      sb.append(tags[i + 1]);
    }
    return sb == null ? "" : sb.toString();
  }

  private String formatFields(final CursorEntry cursor) {
    final String[] fields = cursor.getFields();
    final Type[] types = cursor.getTypes();
    StringBuilder sb = null;

    for (int i = 0; i < fields.length; i++) {
      if (sb == null) {
        sb = new StringBuilder();
      } else {
        sb.append(fieldDelimiter);
      }
      sb.append(fields[i]);
      sb.append('=');
      sb.append(types[i].readAndReset(cursor, i));
    }
    return sb == null ? "" : sb.toString();
  }
}
