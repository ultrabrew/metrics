package io.ultrabrew.metrics;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.ultrabrew.metrics.util.DistributionBucket;
import mockit.Expectations;
import mockit.Verifications;
import org.junit.jupiter.api.Test;

public class HistogramTest {

  @Test
  void testHistoGram() {
    MetricRegistry metricRegistry = new MetricRegistry();
    DistributionBucket bucket = new DistributionBucket(new int[]{0, 10, 100});
    Histogram histogram = metricRegistry.histogram("latency", bucket);

    new Expectations(histogram) {{
      histogram.emit(anyLong, (String[]) any);
    }};

    final long startTime = histogram.start();
    histogram.stop(startTime);

    new Verifications() {{
      String[] tags;
      long l;
      histogram.emit(l = withCapture(), tags = withCapture());
      assertThat(l, greaterThan(0L));
      assertEquals(0, tags.length);
    }};

    histogram.stop(startTime, "TEST-key", "test-value");

    new Verifications() {{
      String[] tags;
      long l;
      histogram.emit(l = withCapture(), tags = withCapture());
      assertThat(l, greaterThan(0L));
      assertThat(tags, arrayContaining("TEST-key", "test-value"));
    }};

    histogram.update(100L);

    new Verifications() {{
      String[] tags;
      histogram.emit(100L, tags = withCapture());
      assertEquals(0, tags.length);
    }};

  }

}
