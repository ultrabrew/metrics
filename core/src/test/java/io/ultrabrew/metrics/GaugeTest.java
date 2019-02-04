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

public class GaugeTest {

  @Test
  public void testGauge() {
    MetricRegistry metricRegistry = new MetricRegistry();
    Gauge g = metricRegistry.gauge("test");

    new Expectations(g) {{
      g.emit(anyLong, (String[]) any);
    }};

    g.set(100L);

    new Verifications() {{
      String[] tags;
      g.emit(100L, tags = withCapture());
      assertEquals(0, tags.length);
    }};

    g.set(-101L, "TEST-key", "test-value");

    new Verifications() {{
      String[] tags;
      g.emit(-101L, tags = withCapture());
      assertThat(tags, arrayContaining("TEST-key", "test-value"));
    }};
  }
}
