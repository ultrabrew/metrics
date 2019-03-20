package io.ultrabrew.metrics;

import io.ultrabrew.metrics.util.DistributionBucket;

public class Histogram extends Gauge {

  public final DistributionBucket bucket;

  Histogram(final MetricRegistry registry, final String id, final DistributionBucket bucket) {
    super(registry, id);
    this.bucket = bucket;
  }

  //TODO: add support for double values

}
