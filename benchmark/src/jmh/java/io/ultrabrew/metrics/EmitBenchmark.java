// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class EmitBenchmark {

  private static final String ENDPOINT = "endpoint";
  private static final String CLIENT = "client_id";
  private static final String STATUS = "status_code";

  @State(Scope.Benchmark)
  public static class BenchmarkState {

    volatile long foo = 0;
  }

  @Benchmark
  public void testEmitHashMap(final Blackhole bh, BenchmarkState state) {
    emit(bh, "measurement1", new java.util.HashMap<String, String>() {{
      put(ENDPOINT, "/api/v1/ping");
      put(CLIENT, "domain.testservice");
      put(STATUS, String.valueOf(state.foo++));
    }}, 1L);
  }

  @Benchmark
  public void testEmitArray(final Blackhole bh, BenchmarkState state) {
    emit(bh, "measurement1", new String[]{
            ENDPOINT, "/api/v1/ping",
            CLIENT, "domain.testservice",
            STATUS, String.valueOf(state.foo++)},
        1L);
  }

  @Benchmark
  public void testEmitVarargs(final Blackhole bh, BenchmarkState state) {
    emit(bh, "measurement1", 1L,
        ENDPOINT, "/api/v1/ping",
        CLIENT, "domain.testservice",
        STATUS, String.valueOf(state.foo++));
  }

  private void emit(final Blackhole bh, final String name, final String[] tags, final long value) {
    bh.consume(name);
    bh.consume(tags);
    bh.consume(value);
  }

  private void emit(final Blackhole bh, final String name, final Map<String, String> tags,
      final long value) {
    bh.consume(name);
    bh.consume(tags);
    bh.consume(value);
  }

  private void emit(final Blackhole bh, final String name, final long value, String... tags) {
    bh.consume(name);
    bh.consume(value);
    bh.consume(tags);
  }
}
