package io.ultrabrew.metrics;

import io.ultrabrew.metrics.util.DistributionBucket;

public class Histogram extends Timer {

  private final DistributionBucket bucket;

  Histogram(final MetricRegistry registry, final String id, final DistributionBucket bucket) {
    super(registry, id);
    this.bucket = bucket;
  }

  DistributionBucket getBucket() {
    return bucket;
  }
}
