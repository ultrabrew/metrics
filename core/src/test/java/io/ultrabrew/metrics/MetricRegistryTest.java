// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import mockit.Deencapsulation;
import org.junit.jupiter.api.Test;

public class MetricRegistryTest {

  public static class TestKlass extends Metric {

    public TestKlass(final String id) {
      super(null, id);
    }
  }

  @Test
  public void testReturnSameInstance() {
    MetricRegistry metricRegistry = new MetricRegistry();
    Counter c = metricRegistry.counter("test");
    assertEquals(c, metricRegistry.counter("test"));
  }

  @Test
  public void testAlreadyDefined() {
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.counter("test");
    assertThrows(IllegalStateException.class, () -> metricRegistry.timer("test"));
  }

  @Test
  public void testNoConstructor() {
    MetricRegistry metricRegistry = new MetricRegistry();
    assertThrows(IllegalStateException.class, () -> metricRegistry.custom("test", TestKlass.class));
  }

  @Test
  public void testAlreadyDefinedSynchronized() throws InterruptedException {
    MetricRegistry metricRegistry = new MetricRegistry();
    final Map<String, Metric> measurements = Deencapsulation
        .getField(metricRegistry, "measurements");

    final AtomicBoolean success = new AtomicBoolean(false);
    final AtomicBoolean completed = new AtomicBoolean(false);

    synchronized (measurements) {
      Thread t = new Thread(() -> {
        try {
          metricRegistry.counter("test");
          success.set(true);
        } catch (IllegalStateException e) {
          fail("No exception is thrown");
        } finally {
          synchronized (completed) {
            completed.set(true);
            completed.notify();
          }
        }
      });
      t.start();

      // wait until thread reaches the synchronized block. This test could be flaky if we don't wait long enough
      while (t.getState() != Thread.State.BLOCKED) {
        Thread.sleep(10);
      }

      measurements.put("test", new Counter(metricRegistry, "test"));
    }

    synchronized (completed) {
      if (!completed.get()) {
        completed.wait(10_000);
      }
    }

    if (!completed.get()) {
      fail("Test thread did not complete!");
    }

    if (!success.get()) {
      fail("Test thread succeeded in creating instance");
    }
  }

  @Test
  public void testAlreadyDefinedSynchronizedWrongType() throws InterruptedException {
    MetricRegistry metricRegistry = new MetricRegistry();
    final Map<String, Metric> measurements = Deencapsulation
        .getField(metricRegistry, "measurements");

    final AtomicBoolean success = new AtomicBoolean(false);
    final AtomicBoolean completed = new AtomicBoolean(false);

    synchronized (measurements) {
      Thread t = new Thread(() -> {
        try {
          metricRegistry.counter("test");
          fail("No exception is thrown");
        } catch (IllegalStateException e) {
          success.set(true);
        } finally {
          synchronized (completed) {
            completed.set(true);
            completed.notify();
          }
        }
      });
      t.start();

      // wait until thread reaches the synchronized block. This test could be flaky if we don't wait long enough
      while (t.getState() != Thread.State.BLOCKED) {
        Thread.sleep(10);
      }

      measurements.put("test", new TestKlass("test"));
    }

    synchronized (completed) {
      if (!completed.get()) {
        completed.wait(10_000);
      }
    }

    if (!completed.get()) {
      fail("Test thread did not complete!");
    }

    if (!success.get()) {
      fail("Test thread succeeded in creating instance");
    }
  }
}
