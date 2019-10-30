// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.dropwizard;

import com.codahale.metrics.Slf4jReporter;
import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.Gauge;
import io.ultrabrew.metrics.MetricRegistry;
import io.ultrabrew.metrics.Timer;
import io.ultrabrew.metrics.reporters.SLF4JReporter;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.LoggerFactory;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(50)
@State(Scope.Benchmark)
public class NoTagsBenchmark {

  private static final int CONSUME_CPU = 512;

  private MetricRegistry ultrabrewRegistry;
  private Counter ultrabrewCounter;
  private Timer ultrabrewTimer;
  private Gauge ultrabrewGauge;
  private SLF4JReporter ultrabrewReporter;

  private com.codahale.metrics.MetricRegistry dropwizardRegistry;
  private com.codahale.metrics.Counter dropwizardCounter;
  private com.codahale.metrics.Timer dropwizardTimer;
  private com.codahale.metrics.Histogram dropwizardHistogram;
  private Slf4jReporter dropwizardReporter;

  private volatile long value = 0L;

  @Setup
  public void setup() {
    ultrabrewRegistry = new MetricRegistry();
    ultrabrewCounter = ultrabrewRegistry.counter("counter");
    ultrabrewTimer = ultrabrewRegistry.timer("timer");
    ultrabrewGauge = ultrabrewRegistry.gauge("gauge");
    ultrabrewReporter = SLF4JReporter.builder()
        .withName("ultrabrew")
        .build();
    ultrabrewRegistry.addReporter(ultrabrewReporter);

    dropwizardRegistry = new com.codahale.metrics.MetricRegistry();
    dropwizardCounter = dropwizardRegistry.counter("counter");
    dropwizardTimer = dropwizardRegistry.timer("timer");
    dropwizardHistogram = dropwizardRegistry.histogram("gauge");
    dropwizardReporter = Slf4jReporter.forRegistry(dropwizardRegistry)
        .outputTo(LoggerFactory.getLogger("dropwizard"))
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.NANOSECONDS)
        .build();
  }

  @Benchmark
  public void counterUltrabrew() {
    ultrabrewCounter.inc();
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void counterDropwizard() {
    dropwizardCounter.inc();
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void timerUltrabrew() {
    final long startTime = ultrabrewTimer.start();
    Blackhole.consumeCPU(CONSUME_CPU);
    ultrabrewTimer.stop(startTime);
  }

  @Benchmark
  public void timerDropwizard() {
    final com.codahale.metrics.Timer.Context context = dropwizardTimer.time();
    Blackhole.consumeCPU(CONSUME_CPU);
    context.stop();
  }

  @Benchmark
  public void gaugeUltrabrew() {
    ultrabrewGauge.set(value++ % 100L);
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void gaugeDropwizard() {
    dropwizardHistogram.update(value++ % 100L);
    Blackhole.consumeCPU(CONSUME_CPU);
  }
}
