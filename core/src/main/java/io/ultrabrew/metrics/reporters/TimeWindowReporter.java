// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters;

import io.ultrabrew.metrics.Metric;
import io.ultrabrew.metrics.Reporter;
import io.ultrabrew.metrics.data.Aggregator;
import io.ultrabrew.metrics.data.BasicDoubleValuedHistogramAggregator;
import io.ultrabrew.metrics.data.BasicHistogramAggregator;
import io.ultrabrew.metrics.data.DistributionBucket;
import io.ultrabrew.metrics.data.DoubleValuedDistributionBucket;
import io.ultrabrew.metrics.util.Intervals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static io.ultrabrew.metrics.Metric.DEFAULT_MAX_CARDINALITY;
import static io.ultrabrew.metrics.reporters.AggregatingReporter.DEFAULT_AGGREGATORS;

/**
 * A base reporter that tracks the state over two time-intervals to prevent contention between
 * writes and reads
 */
public abstract class TimeWindowReporter implements Reporter, AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(TimeWindowReporter.class);

  public static final int DEFAULT_WINDOW_STEP_SIZE_SEC = 60;

  private static final int PADDING_MILLIS = 100;

  private final String name;
  private final long windowStepSizeMillis;

  private AtomicInteger threadId;
  private volatile Thread reportingThread;

  private AggregatingReporter[] reporters = new AggregatingReporter[2];

  public TimeWindowReporter(final String name) {
    this(name, DEFAULT_WINDOW_STEP_SIZE_SEC);
  }

  public TimeWindowReporter(final String name, final int windowStepSizeSeconds) {
    this(name, windowStepSizeSeconds, DEFAULT_AGGREGATORS);
  }

  public TimeWindowReporter(final String name, final int windowStepSizeSeconds,
      final Map<Class<? extends Metric>, Function<Metric, ? extends Aggregator>> defaultAggregators) {
    this(name, windowStepSizeSeconds, defaultAggregators, Collections.EMPTY_MAP);
  }

  public TimeWindowReporter(final String name, final int windowStepSizeSeconds,
      final Map<Class<? extends Metric>, Function<Metric, ? extends Aggregator>> defaultAggregators,
      final Map<String, Function<Metric, ? extends Aggregator>> metricAggregators) {
    this.name = name;
    this.windowStepSizeMillis = windowStepSizeSeconds * 1000;
    this.reporters[0] = new AggregatingReporter(defaultAggregators, metricAggregators) {
    };
    this.reporters[1] = new AggregatingReporter(defaultAggregators, metricAggregators) {
    };
    this.threadId = new AtomicInteger(1);
  }


  @Override
  public void emit(final Metric metric, final long timestamp, final long value,
      final String[] tags) {
    AggregatingReporter writer = reporters[getWriterIndex(timestamp)];
    writer.emit(metric, timestamp, value, tags);
  }

  protected void report() {
    long currentTimeMillis = System.currentTimeMillis();
    AggregatingReporter reader = reporters[getReaderIndex(currentTimeMillis)];
    doReport(reader.aggregators);
  }

  /**
   * Shut down the reporter and release resources.
   */
  @Override
  public void close() {
    stop();
  }

  /**
   * The subclass has to provide the implementation to read the state of all the aggregators and
   * report.
   *
   * @param aggregators mapping from metric id to aggregator
   */
  protected abstract void doReport(Map<String, Aggregator> aggregators);

  private void run() {
    int id = this.threadId.get();
    String threadName = getThreadName(id);
    logger.info("Starting {}", threadName);

    while (id == this.threadId.get()) {
      try {
        long startTimeInMillis = System.currentTimeMillis();
        // adding few extra millis to make sure it doesn't start reporting on a window that's still
        // being written to
        long delayMillis =
            Intervals.calculateDelay(windowStepSizeMillis, startTimeInMillis) + PADDING_MILLIS;

        try {
          Thread.sleep(delayMillis);
        } catch (InterruptedException ignored) {
          if ((System.currentTimeMillis() - startTimeInMillis) < delayMillis) {
            continue;
          }
        }
        report();
      } catch (Throwable t) {
        logger.error("Error reporting metrics", t);
      }
    }

    logger.info("Ending {}", threadName);
  }

  protected void start() {
    synchronized (this) {
      if (!isRunning()) {
        reportingThread = new Thread(this::run, getThreadName(threadId.get()));
        reportingThread.setDaemon(true);
        reportingThread.start();
      } else {
        throw new IllegalStateException("Already started");
      }
    }
  }

  protected void stop() {
    synchronized (this) {
      if (isRunning()) {
        this.threadId.incrementAndGet();
      }
    }
  }

  protected boolean isRunning() {
    return null != reportingThread
        && reportingThread.getName().equals(getThreadName(threadId.get()));
  }

  private String getThreadName(int threadId) {
    return name + "-" + threadId;
  }

  private int getWriterIndex(final long milliseconds) {
    return ((milliseconds / windowStepSizeMillis) & 1) == 0 ? 0 : 1;
  }

  private int getReaderIndex(final long milliseconds) {
    return getWriterIndex(milliseconds) == 0 ? 1 : 0;
  }

  /**
   * A base class for the reporter builder
   *
   * @param <B> builder
   * @param <R> reporter
   */
  public abstract static class TimeWindowReporterBuilder<
      B extends TimeWindowReporterBuilder, R extends TimeWindowReporter> {

    protected Map<Class<? extends Metric>, Function<Metric, ? extends Aggregator>>
        defaultAggregators = DEFAULT_AGGREGATORS;
    protected Map<String, Function<Metric, ? extends Aggregator>> metricAggregators =
        new HashMap<>();

    /**
     * Set the default aggregator for each metric type
     *
     * @param defaultAggregators a map of a metric class to a supplier creating a new aggregator
     */
    public B withDefaultAggregators(
        final Map<Class<? extends Metric>, Function<Metric, ? extends Aggregator>> defaultAggregators) {
      this.defaultAggregators = defaultAggregators;
      return (B) this;
    }

    /**
     * Add histograms to a specific metric
     *
     * @param metricId identifier of the metric
     * @param bucket distribution bucket
     * @param maxCardinality maximum cardinality of data in the histogram
     */
    public B addHistogram(final String metricId, final DistributionBucket bucket, final int maxCardinality) {
      this.metricAggregators
          .put(metricId, (metric) -> new BasicHistogramAggregator(metricId, bucket, maxCardinality));
      return (B) this;
    }

    /**
     * Add histograms to a specific metric
     *
     * @param metricId identifier of the metric
     * @param bucket distribution bucket
     * @param maxCardinality maximum cardinality of data in the histogram
     */
    public B addHistogram(final String metricId, final DoubleValuedDistributionBucket bucket, final int maxCardinality) {
      this.metricAggregators
          .put(metricId, (metric) -> new BasicDoubleValuedHistogramAggregator(metricId, bucket, maxCardinality));
      return (B) this;
    }

    /**
     * Add histograms to a specific metric
     *
     * @param metricId identifier of the metric
     * @param bucket distribution bucket
     */
    public B addHistogram(final String metricId, final DistributionBucket bucket) {
      return addHistogram(metricId, bucket, DEFAULT_MAX_CARDINALITY);
    }

    /**
     * Add histograms to a specific metric
     *
     * @param metricId identifier of the metric
     * @param bucket distribution bucket
     */
    public B addHistogram(final String metricId, final DoubleValuedDistributionBucket bucket) {
      return addHistogram(metricId, bucket, DEFAULT_MAX_CARDINALITY);
    }

    public abstract R build();

  }

}
