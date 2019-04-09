// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters;

import static io.ultrabrew.metrics.reporters.AggregatingReporter.DEFAULT_AGGREGATORS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.Gauge;
import io.ultrabrew.metrics.GaugeDouble;
import io.ultrabrew.metrics.Metric;
import io.ultrabrew.metrics.MetricRegistry;
import io.ultrabrew.metrics.Timer;
import io.ultrabrew.metrics.data.Aggregator;
import io.ultrabrew.metrics.data.Cursor;
import io.ultrabrew.metrics.data.DistributionBucket;
import java.util.ArrayList;
import java.util.List;
import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Injectable;
import mockit.Verifications;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

public class SLF4JReporterTest {

  private SLF4JReporter reporter;

  @AfterEach
  public void tearDown() {
    if (reporter != null) {
      reporter.stop();
    }
  }

  public static class TestMetric extends Metric {

    public TestMetric(final MetricRegistry metricRegistry, final String name) {
      super(metricRegistry, name);
    }

    public void send(final long value, final String... tags) {
      emit(value, tags);
    }
  }

  @Test
  public void testReport(@Injectable Logger logger) throws InterruptedException {

    reporter = new SLF4JReporter("testReport", 1);
    Deencapsulation.setField(reporter, "reporter", logger);
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);

    long start = System.currentTimeMillis();

    Counter counter = metricRegistry.counter("counter");
    counter.inc("testTag", "value");
    counter.inc("testTag", "value");
    counter.inc("testTag", "value2");
    counter.inc("testTag", "value", "testTag2", "value");
    counter.inc();

    Timer timer = metricRegistry.timer("timer");
    timer.update(100L, "testTag", "value");
    timer.update(90L, "testTag", "value");
    timer.update(91L, "testTag", "value");
    timer.update(101L, "testTag", "value");
    timer.update(95L, "testTag", "value");
    timer.update(2000L, "testTag2", "value");
    timer.update(2020L, "testTag2", "value");
    timer.update(2010L, "testTag2", "value");

    Gauge gauge = metricRegistry.gauge("gauge");
    gauge.set(12345L, "tag2", "100");
    gauge.set(12505L, "tag2", "100");
    gauge.set(10005L, "tag2", "100");
    gauge.set(105L, "tag2", "101");

    GaugeDouble gaugeDouble = metricRegistry.gaugeDouble("gaugeDouble");
    gaugeDouble.set(329180.483740, "tag2", "100");
    gaugeDouble.set(102.87, "tag2", "100");
    gaugeDouble.set(87.1, "tag2", "100");
    gaugeDouble.set(83749.09098, "tag2", "101");

    Thread.sleep(calculateDelay(1000, start) + 150);

    new Verifications() {{
      List<Object[]> objects = new ArrayList<>();
      logger.info("lastUpdated={} {}{}{} {}", withCapture(objects));

      assertEquals(10, objects.size());

      compare(objects.get(0), "tag2=100", "count=3 sum=34855 min=10005 max=12505 lastValue=10005",
          "gauge");
      compare(objects.get(1), "tag2=101", "count=1 sum=105 min=105 max=105 lastValue=105", "gauge");

      compare(objects.get(2), "testTag=value", "count=5 sum=477 min=90 max=101", "timer");
      compare(objects.get(3), "testTag2=value", "count=3 sum=6030 min=2000 max=2020", "timer");

      compare(objects.get(4), "tag2=100",
          "count=3 sum=329370.45373999997 min=87.1 max=329180.48374 lastValue=87.1", "gaugeDouble");
      compare(objects.get(5), "tag2=101",
          "count=1 sum=83749.09098 min=83749.09098 max=83749.09098 lastValue=83749.09098",
          "gaugeDouble");

      compare(objects.get(6), "testTag=value", "sum=2", "counter");
      compare(objects.get(7), "testTag=value2", "sum=1", "counter");
      compare(objects.get(8), "testTag=value testTag2=value", "sum=1", "counter");
      compare(objects.get(9), "", "sum=1", "counter");
    }};
  }

  @Test
  void testCustomDelimiter(@Injectable Logger logger) throws InterruptedException {
    String tagDelimiter = ":";
    String fieldDelimiter = "-";
    String tagFieldDelimiter = "#";
    reporter = SLF4JReporter.builder().withName("testReport").withStepSize(1)
        .withTagDelimiter(tagDelimiter).withFieldDelimiter(fieldDelimiter)
        .withTagFieldDelimiter(tagFieldDelimiter)
        .withDefaultAggregators(DEFAULT_AGGREGATORS).build();
    Deencapsulation.setField(reporter, "reporter", logger);
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);

    long start = System.currentTimeMillis();

    Gauge gauge = metricRegistry.gauge("gauge");
    gauge.set(12345L, "tag1", "100", "tag2", "200");

    Thread.sleep(calculateDelay(1000, start) + 150);

    new Verifications() {{
      List<Object[]> objects = new ArrayList<>();
      logger.info("lastUpdated={} {}{}{} {}", withCapture(objects));

      assertEquals(1, objects.size());
      Object[] o = objects.get(0);
      assertEquals(5, o.length);
      assertNotNull(o[0]);
      assertEquals("tag1=100" + tagDelimiter + "tag2=200", o[1]);
      assertEquals(tagFieldDelimiter, o[2]); // delimiter
      assertEquals("count=1" + fieldDelimiter + "sum=12345" + fieldDelimiter + "min=12345"
          + fieldDelimiter + "max=12345" + fieldDelimiter + "lastValue=12345", o[3]);
      assertEquals("gauge", o[4]);
    }};
  }

  @Test
  public void testUnknownMetric(@Injectable Logger logger) throws InterruptedException {

    reporter = new SLF4JReporter("testUnknownMetric");
    Deencapsulation.setField(reporter, "reporter", logger);
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);

    TestMetric metric = metricRegistry.custom("custom", TestMetric.class);

    long start = System.currentTimeMillis();
    metric.send(10L, "tag", "value");
    Thread.sleep(calculateDelay(1000, start) + 150);

    new Verifications() {{
      logger.info(anyString, (Object[]) any);
      times = 0;
    }};
  }

  @Test
  public void testNoFields(@Injectable Logger logger, @Capturing Cursor cursor)
      throws InterruptedException {

    new Expectations() {{
      cursor.next();
      returns(true, false);
      cursor.lastUpdated();
      returns(System.currentTimeMillis() + 2000L, 0L);
    }};

    reporter = new SLF4JReporter("testNoFields", 1);
    Deencapsulation.setField(reporter, "reporter", logger);
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);
    Counter test = metricRegistry.counter("counter");

    long start = System.currentTimeMillis();
    test.inc();
    Thread.sleep(calculateDelay(1000, start) + 150);

    new Verifications() {{
      List<Object[]> objects = new ArrayList<>();
      logger.info("lastUpdated={} {}{}{} {}", withCapture(objects));

      assertEquals(1, objects.size());

      compare(objects.get(0), "", "", "counter");
    }};
  }

  @Test
  public void testUnchangedNotReported(@Injectable final Logger logger)
      throws InterruptedException {
    reporter = new SLF4JReporter("testNoInstrumentation", 1);
    Deencapsulation.setField(reporter, "reporter", logger);
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);
    Counter counter = metricRegistry.counter("counter");

    long start = System.currentTimeMillis();
    counter.inc();

    // Sleep long enough that both buffers are reported twice
    Thread.sleep(calculateDelay(1000, start) + 3150);

    new Verifications() {{
      // Only one report should occur since the counter has only changed once
      logger.info("lastUpdated={} {}{}{} {}", (Object[]) any);
      times = 1;
    }};
  }

  @Test
  public void testNullTags(@Injectable Logger logger) throws InterruptedException {

    reporter = new SLF4JReporter("testNullTags", 1);
    Deencapsulation.setField(reporter, "reporter", logger);
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);

    Counter test = metricRegistry.counter("counter");

    long start = System.currentTimeMillis();
    reporter.emit(test, start, 1L, null);
    Thread.sleep(calculateDelay(1000, start) + 150);

    new Verifications() {{
      logger.info("lastUpdated={} {}{}{} {}", start, "", " ", "sum=1", "counter");
      times = 1;
    }};
  }

  @Test
  public void testNOOP() {
    Aggregator aggregator = AggregatingReporter.NOOP;
    aggregator.apply(null, 0L, 1L);
    assertFalse(aggregator.cursor().next());
    assertFalse(aggregator.sortedCursor().next());
  }

  @Test
  public void testGaugeDouble(@Injectable Logger logger) throws InterruptedException {

    reporter = new SLF4JReporter("testGaugeDouble", 1);
    Deencapsulation.setField(reporter, "reporter", logger);
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(reporter);

    double d1 = 329180.483740;
    long l2 = 102;

    long start = System.currentTimeMillis();
    GaugeDouble gaugeDouble = metricRegistry.gaugeDouble("gaugeDouble");
    gaugeDouble.set(d1, "tag2", "100");
    gaugeDouble.set(l2, "tag2", "101");

    Thread.sleep(calculateDelay(1000, start) + 150);

    new Verifications() {{
      List<Object[]> objects = new ArrayList<>();
      logger.info("lastUpdated={} {}{}{} {}", withCapture(objects));

      assertEquals(2, objects.size());

      compare(objects.get(0), "tag2=100",
          "count=1 sum=329180.48374 min=329180.48374 max=329180.48374 lastValue=329180.48374",
          "gaugeDouble");
      compare(objects.get(1), "tag2=101", "count=1 sum=102.0 min=102.0 max=102.0 lastValue=102.0",
          "gaugeDouble");
    }};
  }

  @Test
  void testHistogram(@Injectable Logger logger) throws InterruptedException {

    String metricId = "latency";
    DistributionBucket bucket = new DistributionBucket(new long[]{0, 10, 50, 100});

    reporter = SLF4JReporter.builder().withName("testHistogram").withStepSize(1)
        .addHistogram(metricId, bucket)
        .build();

    Deencapsulation.setField(this.reporter, "reporter", logger);
    MetricRegistry metricRegistry = new MetricRegistry();
    metricRegistry.addReporter(this.reporter);

    long start = System.currentTimeMillis();

    Gauge gauge = metricRegistry.gauge(metricId);
    gauge.set(-13, "tag", "100");
    gauge.set(-1, "tag", "100");
    gauge.set(0, "tag", "100");
    gauge.set(9, "tag", "100");
    gauge.set(10, "tag", "100");
    gauge.set(49, "tag", "100");
    gauge.set(50, "tag", "100");
    gauge.set(150, "tag", "100");

    gauge.set(15, "tag", "101");
    gauge.set(49, "tag", "101");
    gauge.set(99, "tag", "101");
    gauge.set(100, "tag", "101");
    gauge.set(75, "tag", "101");

    Thread.sleep(calculateDelay(1000, start) + 150);

    new Verifications() {{
      List<Object[]> objects = new ArrayList<>();
      logger.info("lastUpdated={} {}{}{} {}", withCapture(objects));

      assertEquals(2, objects.size());

      compare(objects.get(0), "tag=100",
          "count=8 sum=254 min=-13 max=150 lastValue=150 0_10=2 10_50=2 50_100=1 overflow=1 underflow=2",
          "latency");
      compare(objects.get(1), "tag=101",
          "count=5 sum=338 min=15 max=100 lastValue=75 0_10=0 10_50=2 50_100=2 overflow=1 underflow=0",
          "latency");
    }};
  }

  private void compare(final Object[] o, final String tags, final String fields,
      final String metric) {
    assertEquals(5, o.length);
    assertNotNull(o[0]);
    assertEquals(tags, o[1]);
    assertEquals(" ", o[2]); // delimiter
    assertEquals(fields, o[3]);
    assertEquals(metric, o[4]);

  }

  public static long calculateDelay(long windowSizeMillis, long currentTimeMillis) {
    long delay = windowSizeMillis - (currentTimeMillis % windowSizeMillis);
    return delay + 10;
  }
}
