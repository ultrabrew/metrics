// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.junit.jupiter.api.Assertions.assertEquals;

import mockit.Expectations;
import mockit.Verifications;
import org.junit.jupiter.api.Test;

public class CounterTest {

  @Test
  public void testCounter() {
    MetricRegistry metricRegistry = new MetricRegistry();
    Counter c = metricRegistry.counter("test");

    new Expectations(c) {{
      c.emit(anyLong, (String[]) any);
    }};

    c.inc();

    new Verifications() {{
      String[] tags;
      c.emit(1L, tags = withCapture());
      assertEquals(0, tags.length);
    }};

    c.inc("TEST-key", "test-value");

    new Verifications() {{
      String[] tags;
      c.emit(1L, tags = withCapture());
      assertThat(tags, arrayContaining("TEST-key", "test-value"));
    }};

    c.dec();

    new Verifications() {{
      String[] tags;
      c.emit(-1L, tags = withCapture());
      assertEquals(0, tags.length);
    }};

    c.dec("TEST-key", "test-value");

    new Verifications() {{
      String[] tags;
      c.emit(-1L, tags = withCapture());
      assertThat(tags, arrayContaining("TEST-key", "test-value"));
    }};

    c.inc(100L);

    new Verifications() {{
      String[] tags;
      c.emit(100L, tags = withCapture());
      assertEquals(0, tags.length);
    }};

    c.dec(101L);

    new Verifications() {{
      String[] tags;
      c.emit(-101L, tags = withCapture());
      assertEquals(0, tags.length);
    }};
  }

  @Test
  public void testEmit() {
    MetricRegistry metricRegistry = new MetricRegistry();
    Counter c = metricRegistry.counter("test");

    Reporter reporter = (instance, timestamp, value, tags) -> {
      assertEquals(c, instance);
      assertEquals("test", instance.id);
      assertEquals(1L, value);
      assertEquals(0, tags.length);
    };

    metricRegistry.addReporter(reporter);
    c.inc();
  }

  @Test
  public void testEmitTags() {
    MetricRegistry metricRegistry = new MetricRegistry();
    Counter c = metricRegistry.counter("test");

    Reporter reporter = (instance, timestamp, value, tags) -> {
      assertEquals(c, instance);
      assertEquals("test", instance.id);
      assertEquals(1L, value);
      assertThat(tags, arrayContaining("TEST-key", "test-value"));
    };

    metricRegistry.addReporter(reporter);
    c.inc("TEST-key", "test-value");
  }
}
