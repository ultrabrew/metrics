// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.MetricRegistry;
import io.ultrabrew.metrics.data.Aggregator;
import io.ultrabrew.metrics.data.CursorEntry;
import io.ultrabrew.metrics.data.MultiCursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

public class TimeWindowReporterTest {

  private TimeWindowReporter reporter;

  private AggregatingReporter[] reporters;

  @BeforeEach
  public void setUp() {

    reporter = new TimeWindowReporter("testReport") {
      @Override
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };

    this.reporters = Deencapsulation.getField(reporter, "reporters");
  }

  @Test
  public void writesToTheCurrentWindow() {

    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);

    long currentTimeMillis = System.currentTimeMillis();
    int writerIndex = getWriterIndex(currentTimeMillis, TimeUnit.MINUTES.toMillis(1));

    Counter counter = metricRegistry.counter("counter");
    String[] tagSet = {"testTag", "value"};
    counter.inc(tagSet);
    counter.inc();

    new Verifications() {{
      reporters[writerIndex].emit(counter, anyLong, 1, tagSet);
    }};
  }

  @Test
  public void reportsFromThePreviousWindow() throws Exception {

    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);

    long currentTimeMillis = System.currentTimeMillis();
    long windowSizeMillis = TimeUnit.MINUTES.toMillis(1);
    int writerIndex = getWriterIndex(currentTimeMillis, windowSizeMillis);
    int readerIndex = getReaderIndex(writerIndex);

    Counter counter = metricRegistry.counter("counter");
    String[] tagSet = {"testTag", "value"};
    counter.inc(tagSet);
    counter.inc();

    String[] previousTagSet = {"testTag2", "value2"};
    long recordTime = currentTimeMillis - windowSizeMillis;
    reporters[readerIndex].emit(counter, recordTime, 5, previousTagSet);

    new Expectations(reporter) {
    };

    reporter.report();

    new Verifications() {{
      List<Map<String, Aggregator>> aggregatorsList = new ArrayList<>();
      reporter.doReport(withCapture(aggregatorsList));

      assertEquals(1, aggregatorsList.size());
      Map<String, Aggregator> aggregators = aggregatorsList.get(0);
      MultiCursor multiCursor = new MultiCursor(aggregators.values());
      if (multiCursor.next()) {
        String[] tags = multiCursor.getTags();
        assertArrayEquals(previousTagSet, tags);

        CursorEntry cursorEntry = multiCursor.nextCursorEntry();
        assertEquals("counter", cursorEntry.getMetricId());
        assertArrayEquals(previousTagSet, cursorEntry.getTags());
        assertEquals(recordTime, cursorEntry.lastUpdated());
      } else {
        fail("Metrics not found");
      }
    }};
  }

  @Test
  public void defaultWindowSizeIs60Seconds() {
    long windowSizeMillis = Deencapsulation.getField(reporter, "windowStepSizeMillis");
    assertEquals(TimeUnit.MINUTES.toMillis(1), windowSizeMillis);
  }

  @Test
  public void customWindowSize() {

    reporter = new TimeWindowReporter("testReport", 17) {
      @Override
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };

    long windowSizeMillis = Deencapsulation.getField(reporter, "windowStepSizeMillis");
    assertEquals(TimeUnit.SECONDS.toMillis(17), windowSizeMillis);
  }

  @Test
  public void scheduledReportingByWindowSize() throws InterruptedException {

    reporter = new TimeWindowReporter("testReport", 1) {
      @Override
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };

    new Expectations(reporter) {{
    }};

    long start = System.currentTimeMillis();
    reporter.start();
    Thread.sleep(calculateDelay(1000, start) + 200);

    new Verifications() {{
      reporter.report();
      times = 1;
    }};
  }

  @Test
  public void stopReporting() throws Exception {

    reporter = new TimeWindowReporter("testReport", 1) {
      @Override
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };
    new Expectations(reporter) {{
    }};

    long start = System.currentTimeMillis();

    reporter.start();

    Thread reportingThread = Deencapsulation.getField(reporter, "reportingThread");
    assertTrue(reportingThread.isAlive());

    Thread.sleep(calculateDelay(1000, start));

    reporter.close();

    Thread.sleep(500);

    new Verifications() {{
      reporter.doReport(withInstanceOf(Map.class));
      times = 1;
    }};
    assertFalse(reporter.isRunning());
    assertFalse(reportingThread.isAlive());
  }

  @Test
  public void reportingThreadSleepsAgainIfInterruptedAndHasNotSleptEnough()
      throws InterruptedException {

    long start = System.currentTimeMillis();
    reporter = new TimeWindowReporter("testReport", 1) {
      @Override
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };
    reporter.start();

    new Expectations(reporter) {{
    }};

    Thread reportingThread = Deencapsulation.getField(reporter, "reportingThread");

    Thread.sleep(calculateDelay(1000, start) + 100);

    reportingThread.interrupt();

    Thread.sleep(100);

    new Verifications() {{
      reporter.doReport(withInstanceOf(Map.class));
      times = 1;
    }};
  }

  @Test
  public void reportingThreadSleepsFor100ExtraMS() throws Exception {

    reporter = new TimeWindowReporter("testReport", 1) {
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };

    new Expectations(reporter) {{
    }};

    long start = System.currentTimeMillis();
    reporter.start();
    Thread reportingThread = Deencapsulation.getField(reporter, "reportingThread");

    Thread.sleep(calculateDelay(1000, start));

    reportingThread.interrupt();

    Thread.sleep(100);

    new Verifications() {{
      reporter.report();
      times = 0;
    }};
  }

  @Test
  public void reportingThreadDoesNotSleepsAgainIfInterruptedAndHasSleptEnough()
      throws InterruptedException {
    Semaphore sem = new Semaphore(0);

    reporter = new TimeWindowReporter("testReport", 1) {
      protected void doReport(Map<String, Aggregator> aggregators) {
        sem.release();
      }
    };

    new Expectations(reporter) {{
    }};

    long start = System.currentTimeMillis();
    reporter.start();
    Thread reportingThread = Deencapsulation.getField(reporter, "reportingThread");

    // Wait until first report
    sem.acquire();

    // Ensure reportingThread is sleeping
    Thread.sleep(500);

    // Force reportingThread to sleep longer than intended followed by being interrupted
    // This is an ugly hack to avoid mocking System.currentTimeMillis() which does not work
    reportingThread.suspend();
    Thread.sleep(2000);
    reportingThread.interrupt();
    reportingThread.resume();

    Thread.sleep(100);

    new Verifications() {{
      reporter.doReport(withInstanceLike(new ConcurrentHashMap<>()));
      times = 2;
    }};
  }

  @Test
  public void logsErrorWhileReportingMetrics(@Mocked Logger logger) throws InterruptedException {

    RuntimeException exptected = new RuntimeException();

    reporter = new TimeWindowReporter("testReport", 1) {
      @Override
      protected void doReport(Map<String, Aggregator> multiCursor) {
        throw exptected;
      }
    };
    Deencapsulation.setField(reporter, logger);
    reporter.start();

    Thread.sleep(1001);

    new Verifications() {{
      logger.error("Error reporting metrics", exptected);
    }};
  }

  @Test
  public void startSchedulerIsSynchronized()
      throws InterruptedException {

    reporter = new TimeWindowReporter("testReport", 60) {
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };

    final boolean[] b1 = new boolean[1];
    final boolean[] b2 = new boolean[1];

    Thread t1 = new Thread(() -> {
        try {
          reporter.start();
          b1[0] = true;
        } catch (IllegalStateException ignored) {
          b1[0] = false;
        }
    });

    Thread t2 = new Thread(() -> {
      try {
        reporter.start();
        b2[0] = true;
      } catch (IllegalStateException ignored) {
        b2[0] = false;
      }
    });

    t1.start();
    t2.start();

    t1.join();
    t2.join();

    assertTrue(b1[0] ^ b2[0]);
    assertTrue(reporter.isRunning());
  }

  @Test
  public void stopSchedulerIsSynchronized() throws InterruptedException {

    reporter = new TimeWindowReporter("testReport", 60) {
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };

    reporter.start();

    AtomicInteger threadId = Deencapsulation.getField(reporter, "threadId");

    assertEquals(1, threadId.get());

    Thread t1 = new Thread(() -> {
      synchronized (reporter) {
        try {
          Thread.sleep(100);
          reporter.stop();
        } catch (InterruptedException ignored) {
        }
      }
    });
    Thread t2 = new Thread(() -> {
      reporter.stop();
    });

    t1.start();
    Thread.sleep(5);
    t2.start();

    t1.join();
    t2.join();

    reporter.stop();

    assertFalse(reporter.isRunning());
    assertEquals(2, threadId.get());
  }

  @Test
  public void testStopStartUnderHighConcurrency() throws InterruptedException {
    reporter = new TimeWindowReporter("testReport", 1) {
      protected void doReport(Map<String, Aggregator> aggregators) {
      }
    };

    final Thread[] rt1 = new Thread[1];
    final Thread[] rt2 = new Thread[1];

    long now = System.currentTimeMillis();
    Thread t1 = new Thread(() -> {
      try {
        reporter.start();
        rt1[0] = Deencapsulation.getField(reporter, "reportingThread");
        Thread.sleep(calculateDelay(1000, now) + 100); // padding of 100 ms
      } catch (InterruptedException ignored) {
      }
    });
    Thread t2 = new Thread(() -> {
      try {
        Thread.sleep(200);
        reporter.stop();
        reporter.start();
        rt2[0] = Deencapsulation.getField(reporter, "reportingThread");
      } catch (InterruptedException ignored) {
      }
    });

    t1.start();
    t2.start();
    t1.join();

    // FIXME: This is broken, TimeWindowReporter.stop() does not wait for the thread to exit
    assertFalse(rt1[0].isAlive());
    assertTrue(rt2[0].isAlive());
  }

  @Test
  public void doesnotStartTheSchedulerByDefault() throws Exception {
    assertFalse(reporter.isRunning());
  }

  @Test
  public void doesnotStartSchedulerIfStartedAlready() {
    reporter.start();
    assertThrows(IllegalStateException.class, () -> reporter.start());
    assertTrue(reporter.isRunning());
  }

  @Test
  public void startScheduler() {
    reporter.start();
    assertTrue(reporter.isRunning());
  }

  private int getWriterIndex(long milliseconds, long windowSizeMillis) {
    return ((milliseconds / windowSizeMillis) & 1) == 0 ? 0 : 1;
  }

  private int getReaderIndex(int writerIndex) {
    return writerIndex == 0 ? 1 : 0;
  }


  public static long calculateDelay(long windowSizeMillis, long currentTimeMillis) {
    long delay = windowSizeMillis - (currentTimeMillis % windowSizeMillis);
    return delay + 10;
  }
}
