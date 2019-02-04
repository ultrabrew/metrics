// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import mockit.Expectations;
import mockit.Verifications;
import org.junit.jupiter.api.Test;

public class TimerTest {

  @Test
  public void testTimer() {
    MetricRegistry metricRegistry = new MetricRegistry();
    Timer t = metricRegistry.timer("test");

    new Expectations(t) {{
      t.emit(anyLong, (String[]) any);
    }};

    final long startTime = t.start();
    t.stop(startTime);

    new Verifications() {{
      String[] tags;
      long l;
      t.emit(l = withCapture(), tags = withCapture());
      assertThat(l, greaterThan(0L));
      assertEquals(0, tags.length);
    }};

    t.stop(startTime, "TEST-key", "test-value");

    new Verifications() {{
      String[] tags;
      long l;
      t.emit(l = withCapture(), tags = withCapture());
      assertThat(l, greaterThan(0L));
      assertThat(tags, arrayContaining("TEST-key", "test-value"));
    }};

    t.update(100L);

    new Verifications() {{
      String[] tags;
      t.emit(100L, tags = withCapture());
      assertEquals(0, tags.length);
    }};
  }
}
