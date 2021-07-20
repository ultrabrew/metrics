// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import io.ultrabrew.metrics.util.Intervals;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Collector of statistics related to the running Java Virtual Machine.
 * <p>The statistics are reported as {@link Gauge} values with following identifiers:</p>
 * <dl>
 * <dt>jvm.classloading.loaded</dt>
 * <dd>Number of classes currently loaded into the JVM.</dd>
 * <dt>jvm.classloading.unloaded</dt>
 * <dd>Total number of classes unloaded since JVM started.</dd>
 * <dt>jvm.classloading.totalLoaded</dt>
 * <dd>Total number of classes loaded since JVM started.</dd>
 * <dt>jvm.compilation.totalTime</dt>
 * <dd>Approximate total time used for JIT compilation in milliseconds.</dd>
 * <dt>jvm.thread.daemonCount</dt>
 * <dd>Number of currently running daemon threads.</dd>
 * <dt>jvm.thread.count</dt>
 * <dd>Number of currently running threads.</dd>
 * <dt>jvm.thread.totalStartedCount</dt>
 * <dd>Total number of threads started in this JVM.</dd>
 * <dt>jvm.memorypool.&lt;poolname&gt;.committed</dt>
 * <dd>Amount of memory allocated from the OS for this memory pool in bytes.</dd>
 * <dt>jvm.memorypool.&lt;poolname&gt;.used</dt>
 * <dd>Amount of memory used for this memory pool in bytes.</dd>
 * <dt>jvm.bufferpool.&lt;poolname&gt;.count</dt>
 * <dd>Estimated number of buffers in this pool.</dd>
 * <dt>jvm.bufferpool.&lt;poolname&gt;.totalCapacity</dt>
 * <dd>Estimated total capacity of this pool in bytes.</dd>
 * <dt>jvm.bufferpool.&lt;poolname&gt;.memoryUsed</dt>
 * <dd>Estimated total memory used for this pool in bytes.</dd>
 * <dt>jvm.gc.&lt;collectorname&gt;.count</dt>
 * <dd>Total number of garbage collections for this collector.</dd>
 * <dt>jvm.gc.&lt;collectorname&gt;.time</dt>
 * <dd>Approximate total time used by this collector in milliseconds.</dd>
 * </dl>
 *
 * @see ClassLoadingMXBean
 * @see CompilationMXBean
 * @see ThreadMXBean
 * @see MemoryPoolMXBean
 * @see BufferPoolMXBean
 * @see GarbageCollectorMXBean
 */
public class JvmStatisticsCollector {

  private final MetricRegistry registry;
  private final Map<String, Gauge> gaugeCache = new HashMap<>();
  private final Semaphore exitRequest = new Semaphore(0);
  private Thread reportingThread = null;

  /**
   * Create a JvmStatisticsCollector that emits statistics to the given {@link MetricRegistry}.
   *
   * @param registry registry to report statistics to
   */
  public JvmStatisticsCollector(final MetricRegistry registry) {
    this.registry = registry;
  }

  /**
   * Start collecting statistics at given intervals.
   *
   * @param intervalMillis data collection interval in milliseconds
   * @throws IllegalStateException if already running
   */
  public void start(long intervalMillis) {
    if (reportingThread != null) {
      throw new IllegalStateException("Already started");
    }
    reportingThread = new Thread(() -> run(intervalMillis));
    reportingThread.setDaemon(true);
    reportingThread.start();
  }

  private void run(long intervalMillis) {
    while (true) {
      try {
        // Offset the collection time to middle of interval to avoid racing with TimeWindowReporter
        // based reporters as they use the interval end
        long delay = Intervals
            .calculateDelay(intervalMillis, System.currentTimeMillis() + intervalMillis / 2);
        if (exitRequest.tryAcquire(delay, TimeUnit.MILLISECONDS)) {
          break;
        }
        collect();
      } catch (InterruptedException e) {
        // Ignore, should not happen and just looping again is fine
      }
    }
  }

  /**
   * Stop collecting statistics.
   *
   * @throws IllegalStateException if not running
   */
  public void stop() {
    if (reportingThread == null) {
      throw new IllegalStateException("Not started");
    }
    try {
      exitRequest.release();
      reportingThread.join();
    } catch (InterruptedException e) {
      // CLOVER:OFF
      // Unreachable unless something completely unexpected happens
      throw new RuntimeException("Unexpected exception", e);
      // CLOVER:ON
    }
  }

  private void setGauge(String id, long value) {
    Gauge gauge = gaugeCache.computeIfAbsent(id, registry::gauge);
    gauge.set(value);
  }

  private void collect() {
    ClassLoadingMXBean loadingBean = ManagementFactory.getClassLoadingMXBean();
    setGauge("jvm.classloading.loaded", loadingBean.getLoadedClassCount());
    setGauge("jvm.classloading.unloaded", loadingBean.getUnloadedClassCount());
    setGauge("jvm.classloading.totalLoaded", loadingBean.getTotalLoadedClassCount());

    CompilationMXBean compilationBean = ManagementFactory.getCompilationMXBean();
    setGauge("jvm.compilation.totalTime", compilationBean.getTotalCompilationTime());

    ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    setGauge("jvm.thread.daemonCount", threadBean.getDaemonThreadCount());
    setGauge("jvm.thread.count", threadBean.getThreadCount());
    setGauge("jvm.thread.totalStartedCount", threadBean.getTotalStartedThreadCount());

    for (MemoryPoolMXBean poolBean : ManagementFactory.getMemoryPoolMXBeans()) {
      MemoryUsage usage = poolBean.getUsage();
      String prefix = "jvm.memorypool." + sanitizeName(poolBean.getName());
      setGauge(prefix + ".committed", usage.getCommitted());
      setGauge(prefix + ".used", usage.getUsed());
    }

    for (BufferPoolMXBean bufferPoolBean : ManagementFactory
        .getPlatformMXBeans(BufferPoolMXBean.class)) {
      String prefix = "jvm.bufferpool." + sanitizeName(bufferPoolBean.getName());
      setGauge(prefix + ".count", bufferPoolBean.getCount());
      setGauge(prefix + ".totalCapacity", bufferPoolBean.getTotalCapacity());
      setGauge(prefix + ".memoryUsed", bufferPoolBean.getMemoryUsed());
    }

    for (GarbageCollectorMXBean collectorBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      String prefix = "jvm.gc." + collectorBean.getName().replace(' ', '_');
      setGauge(prefix + ".count", collectorBean.getCollectionCount());
      setGauge(prefix + ".time", collectorBean.getCollectionTime());
    }
  }

  private String sanitizeName(String name) {
    return name.replace(' ', '_').replace('\'', '_');
  }
}
