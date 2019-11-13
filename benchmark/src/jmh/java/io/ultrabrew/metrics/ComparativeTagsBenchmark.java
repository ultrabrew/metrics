// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics;

import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.Gauge;
import io.ultrabrew.metrics.MetricRegistry;
import io.ultrabrew.metrics.Timer;
import io.ultrabrew.metrics.reporters.SLF4JReporter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
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
public class ComparativeTagsBenchmark {
  private static final int CONSUME_CPU = 512;
  private static final String TAGNAME = "tagName";
  private static final String DROPWIZARD_COUNTER_TAGNAME_PREFIX = "counter.tagName.";
  private static final String DROPWIZARD_TIMER_TAGNAME_PREFIX = "timer.tagName.";
  private static final String DROPWIZARD_GAUGE_TAGNAME_PREFIX = "gauge.tagName.";

  private MetricRegistry ultrabrewRegistry;
  private Counter ultrabrewCounter;
  private Timer ultrabrewTimer;
  private Gauge ultrabrewGauge;
  private SLF4JReporter ultrabrewReporter;

  private com.codahale.metrics.MetricRegistry dropwizardRegistry;
  private com.codahale.metrics.Slf4jReporter dropwizardReporter;

  // You will not see this again. It is the old way.
  private io.opencensus.tags.Tagger opencensusTagger;
  private io.opencensus.tags.TagKey opencensusKey;
  private io.opencensus.stats.StatsRecorder opencensusRecorder;
  private io.opencensus.stats.Aggregation opencensusSumAggregator;
  private io.opencensus.stats.Aggregation opencensusCountAggregator;
  private io.opencensus.stats.Measure.MeasureLong opencensusCounterMeasure;
  private io.opencensus.stats.View opencensusCounterSum;
  private io.opencensus.stats.Measure.MeasureLong opencensusGaugeMeasure;
  private io.opencensus.stats.View opencensusGaugeSum;
  private io.opencensus.stats.View opencensusGaugeCount;
  private io.opencensus.stats.ViewManager opencensusViewManager;

  // Warning! Experimental!
  private io.opencensus.metrics.MetricRegistry opencensusRegistry;
  private io.opencensus.metrics.LabelKey opencensusLabelKey;
  private io.opencensus.metrics.MetricOptions opencensusOptions;
  private io.opencensus.metrics.LongCumulative opencensusCounter;
  private io.opencensus.metrics.LongGauge opencensusGauge;

  private volatile long value = 0L;
  private volatile long tagValue = 0L;

  @Param({"1", "10", "100"})
  private int cardinality;

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
    dropwizardReporter = com.codahale.metrics.Slf4jReporter.forRegistry(dropwizardRegistry)
        .outputTo(LoggerFactory.getLogger("dropwizard"))
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.NANOSECONDS)
        .build();

    opencensusTagger = io.opencensus.tags.Tags.getTagger();
    opencensusKey = io.opencensus.tags.TagKey.create(TAGNAME);
    opencensusRecorder = io.opencensus.stats.Stats.getStatsRecorder();
    opencensusSumAggregator = io.opencensus.stats.Aggregation.Sum.create();
    opencensusCountAggregator = io.opencensus.stats.Aggregation.Count.create();
    opencensusCounterMeasure = io.opencensus.stats.Measure.MeasureLong.create(
        "opencensus/counter",
        "A counter for benchmarking purposes",
        "1");
    opencensusCounterSum = io.opencensus.stats.View.create(
        io.opencensus.stats.View.Name.create("opencensus/counter/sum"),
        "A sum for benchmarking purposes",
        opencensusCounterMeasure,
        opencensusSumAggregator,
        Collections.singletonList(opencensusKey));
    opencensusGaugeMeasure = io.opencensus.stats.Measure.MeasureLong.create(
        "opencensus/gauge",
        "A gauge for benchmarking purposes",
        "1");
    opencensusGaugeSum = io.opencensus.stats.View.create(
        io.opencensus.stats.View.Name.create("opencensus/gauge/sum"),
        "A gauge sum for benchmarking purposes",
        opencensusGaugeMeasure,
        opencensusSumAggregator,
        Collections.singletonList(opencensusKey));
    opencensusGaugeCount = io.opencensus.stats.View.create(
        io.opencensus.stats.View.Name.create("opencensus/gauge/count"),
        "A gauge count for benchmarking purposes",
        opencensusGaugeMeasure,
        opencensusCountAggregator,
        Collections.singletonList(opencensusKey));
    opencensusViewManager = io.opencensus.stats.Stats.getViewManager();
    opencensusViewManager.registerView(opencensusCounterSum);
    opencensusViewManager.registerView(opencensusGaugeSum);
    opencensusViewManager.registerView(opencensusGaugeCount);

    opencensusRegistry = io.opencensus.metrics.Metrics.getMetricRegistry();
    opencensusLabelKey = io.opencensus.metrics.LabelKey.create(TAGNAME,
        "A label key for benchmarking purposes");
    opencensusOptions = io.opencensus.metrics.MetricOptions.builder().
        setLabelKeys(Collections.singletonList(opencensusLabelKey)).
        build();
    opencensusCounter = opencensusRegistry.addLongCumulative("counter",
        opencensusOptions);
    opencensusGauge = opencensusRegistry.addLongGauge("gauge",
        opencensusOptions);

    // This is not hooked up to an agent.
    io.opencensus.exporter.metrics.ocagent.OcAgentMetricsExporter.createAndRegister(
        io.opencensus.exporter.metrics.ocagent.OcAgentMetricsExporterConfiguration.builder().
            setExportInterval(io.opencensus.common.Duration.create(60L /*sec*/, 0 /*nanosec*/)).
            build());
  }

  @Benchmark
  public void counterUltrabrew() {
    ultrabrewCounter.inc(TAGNAME, String.valueOf(tagValue++ % cardinality));
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void counterDropwizard() {
    final com.codahale.metrics.Counter counter =
        dropwizardRegistry
            .counter(DROPWIZARD_COUNTER_TAGNAME_PREFIX + String.valueOf(tagValue++ % cardinality));
    counter.inc();
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void counterOpenCensusStats() {
    final io.opencensus.tags.TagContext tagContext = opencensusTagger.emptyBuilder()
        .put(opencensusKey, io.opencensus.tags.TagValue.create(String.valueOf(tagValue++ % cardinality)))
        .build();
    try (io.opencensus.common.Scope ss = opencensusTagger.withTagContext(tagContext)) {
        opencensusRecorder.newMeasureMap().put(opencensusCounterMeasure, 1L).record();
    }
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void counterOpenCensusMetrics() {
    final io.opencensus.metrics.LabelValue labelValue =
        io.opencensus.metrics.LabelValue.create(String.valueOf(tagValue++ % cardinality));
    final io.opencensus.metrics.LongCumulative.LongPoint point =
        opencensusCounter.getOrCreateTimeSeries(Collections.singletonList(labelValue));
    point.add(1L);
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void timerUltrabrew() {
    final long startTime = ultrabrewTimer.start();
    Blackhole.consumeCPU(CONSUME_CPU);
    ultrabrewTimer.stop(startTime, TAGNAME, String.valueOf(tagValue++ % cardinality));
  }

  @Benchmark
  public void timerDropwizard() {
    final com.codahale.metrics.Timer timer =
        dropwizardRegistry
            .timer(DROPWIZARD_TIMER_TAGNAME_PREFIX + String.valueOf(tagValue++ % cardinality));
    final com.codahale.metrics.Timer.Context context = timer.time();
    Blackhole.consumeCPU(CONSUME_CPU);
    context.stop();
  }

  @Benchmark
  public void gaugeUltrabrew() {
    ultrabrewGauge.set(value++ % 100L, TAGNAME, String.valueOf(tagValue++ % cardinality));
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void gaugeDropwizard() {
    final com.codahale.metrics.Histogram histogram =
        dropwizardRegistry
            .histogram(DROPWIZARD_GAUGE_TAGNAME_PREFIX + String.valueOf(tagValue++ % cardinality));

    histogram.update(value++ % 100L);
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void gaugeOpenCensusStats() {
    final io.opencensus.tags.TagContext tagContext = opencensusTagger.emptyBuilder()
        .put(opencensusKey, io.opencensus.tags.TagValue.create(String.valueOf(tagValue++ % cardinality)))
        .build();
    try (io.opencensus.common.Scope ss = opencensusTagger.withTagContext(tagContext)) {
        opencensusRecorder.newMeasureMap().put(opencensusGaugeMeasure, value++ % 100L).record();
    }
    Blackhole.consumeCPU(CONSUME_CPU);
  }

  @Benchmark
  public void gaugeOpenCensusMetrics() {
    final io.opencensus.metrics.LabelValue labelValue =
        io.opencensus.metrics.LabelValue.create(String.valueOf(tagValue++ % cardinality));
    final io.opencensus.metrics.LongGauge.LongPoint point =
        opencensusGauge.getOrCreateTimeSeries(Collections.singletonList(labelValue));
    point.set(value++ % 100L);
    Blackhole.consumeCPU(CONSUME_CPU);
  }
}
