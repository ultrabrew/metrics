// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.util;

public class Intervals {

  public static long calculateDelay(final long interval, final long currentTime) {
    return interval - (currentTime % interval);

  }
}
