// Copyright 2019, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

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
