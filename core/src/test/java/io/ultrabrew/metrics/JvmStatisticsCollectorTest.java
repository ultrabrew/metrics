// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import mockit.Mocked;
import mockit.Verifications;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JvmStatisticsCollectorTest {

  @Test
  public void testStartWhenStarted(@Mocked MetricRegistry registry) {
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);

    collector.start(100);
    try {
      collector.start(100);
      fail("Expected exception");
    } catch (IllegalStateException e) {
      collector.stop();
    }
  }

  @Test
  public void testStopWhenStopped(@Mocked MetricRegistry registry) {
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);
    assertThrows(IllegalStateException.class, collector::stop);
  }

  @Test
  public void testCollecting(@Mocked Reporter reporter) throws InterruptedException {
    MetricRegistry registry = new MetricRegistry();
    registry.addReporter(reporter);
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);

    collector.start(100);
    Thread.sleep(100);
    collector.stop();

    new Verifications() {{
      List<Metric> metrics = new ArrayList<>();
      reporter.emit(withCapture(metrics), anyLong, anyLong, (String[]) any);

      assertEquals("jvm.classloading.loaded", metrics.get(0).id);
      assertEquals("jvm.classloading.unloaded", metrics.get(1).id);
      assertEquals("jvm.classloading.totalLoaded", metrics.get(2).id);
      assertEquals("jvm.compilation.totalTime", metrics.get(3).id);
      assertEquals("jvm.thread.daemonCount", metrics.get(4).id);
      assertEquals("jvm.thread.count", metrics.get(5).id);
      assertEquals("jvm.thread.totalStartedCount", metrics.get(6).id);

      // There are some unverified items here, but they depend on the VM we're running on and are thus hard to verify
      // For the same reason, we can't verify the number of calls to reporter.emit() as it depends on the VM
    }};
  }

  @Test
  public void testCollectProcessCpuUsage(@Mocked Reporter reporter) {
    MetricRegistry registry = new MetricRegistry();
    registry.addReporter(reporter);
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);
    MethodHandle methodHandle = collector.detectMethod(Double.class, "doubleValue");
    collector.collectProcessCpuUsage(Double.valueOf(0.7d), methodHandle);

    new Verifications() {{
      List<Metric> metrics = new ArrayList<>();
      reporter.emit(withCapture(metrics), anyLong, anyLong, (String[]) any);
      assertEquals("jvm.cpu.usage", metrics.get(0).id);
    }};
  }

  @Test
  public void testCollectProcessCpuUsageNoMethodHandle(@Mocked Reporter reporter) {
    MetricRegistry registry = new MetricRegistry();
    registry.addReporter(reporter);
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);
    collector.collectProcessCpuUsage(Double.valueOf(0.7d), null);

    new Verifications() {{
      reporter.emit((Metric) any, anyLong, anyLong, (String[]) any);
      times = 0;
    }};
  }

  @Test
  public void testGetFirstClassFound(@Mocked MetricRegistry registry) {
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);
    Class<?> clazz = collector.getFirstClassFound(Arrays.asList(
            "java.lang.Double00",
            "java.lang.Double"
    ));
    assertNotNull(clazz);
  }

  @Test
  public void testGetFirstClassFoundNotFound(@Mocked MetricRegistry registry) {
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);
    Class<?> clazz = collector.getFirstClassFound(Arrays.asList(
            "java.lang.Double00"
    ));
    assertNull(clazz);
  }

  @Test
  public void testDetectMethod(@Mocked MetricRegistry registry) {
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);
    MethodHandle methodHandle = collector.detectMethod(Double.class, "doubleValue");
    assertNotNull(methodHandle);

  }

  @Test
  public void testDetectMethodNoClass(@Mocked MetricRegistry registry) {
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);
    MethodHandle methodHandle = collector.detectMethod(null, "doubleValue");
    assertNull(methodHandle);
  }

  @Test
  public void testDetectMethodNoMethod(@Mocked MetricRegistry registry) {
    JvmStatisticsCollector collector = new JvmStatisticsCollector(registry);
    MethodHandle methodHandle = collector.detectMethod(null, "doubleValue00");
    assertNull(methodHandle);
  }

}
