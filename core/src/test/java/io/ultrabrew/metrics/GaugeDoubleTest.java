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

public class GaugeDoubleTest {

  @Test
  public void testGaugeDouble() {

    MetricRegistry metricRegistry = new MetricRegistry();
    GaugeDouble gaugeDouble = metricRegistry.gaugeDouble("cpuUsage");

    new Expectations(gaugeDouble) {{
      gaugeDouble.emit(anyLong, (String[]) any);
    }};

    double v1 = 98.99;
    gaugeDouble.set(v1);

    new Verifications() {{
      String[] tags;
      gaugeDouble.emit(Double.doubleToRawLongBits(v1), tags = withCapture());
      assertEquals(0, tags.length);
    }};

    double v2 = -10.505;
    gaugeDouble.set(v2, "TEST-key", "test-v1");

    new Verifications() {{
      String[] tags;
      gaugeDouble.emit(Double.doubleToRawLongBits(v2), tags = withCapture());
      assertThat(tags, arrayContaining("TEST-key", "test-v1"));
    }};
  }
}
