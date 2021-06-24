# Ultrabrew Metrics

[![Build Status](https://travis-ci.com/ultrabrew/metrics.svg?branch=master)][Travis]

> A lightweight, high-performance Java library to measure correctly the behavior
> of critical components in production.

Ultrabrew Metrics is a high-performance instrumentation library designed for use
in large-scale JVM applications. It provides rich features such as metrics with
dynamic dimensions (or tags), ability to manage multiple reporters and encourages
accuracy over large deployments.

## Table of Contents

- [Background](#background)
- [Install](#install)
- [Usage](#usage)
- [Contribute](#contribute)
- [License](#license)

## Background

Existing metrics libraries such as [Dropwizard Metrics][Dropwizard]
previously served us well. Unfortunately, those libraries are starting to show
their age. As a result, we saw the need to write a new library designed
primarily for scale and to support essential features such as dynamic
dimensions.

### Concepts

To better understand the concepts and terms used by this library, please see [CONCEPTS.md](CONCEPTS.md)

### Key Requirements

1. Support [dynamic dimensions (tag keys and values)][Tags] at the time of
   measurement.
2. Reduce GC pressure by minimizing the number of objects created by the
   library.
3. Support accurate aggregation in reporters via [monoids][Monoid]
4. Minimize number of dependencies.
5. Decouple instrumentation from reporting in the following ways:
    1. adding a new reporter or modifying an existing reporter does not
       require changing instrumentation code;
    2. each reporter aggregates measurements independently; and
    3. multiple reporters may report at different intervals.
6. (TODO) Support raw event emission for external service consumption.
    - *E.g.*, sending UDP packets to external service similar to [statsd], which
      could do aggregation before sending to an actual time series storage, or
      sending raw events directly to an alerting service or to a time-series
      database.
7. (TODO) Support better cumulative or global-percentile approximation across
   multiple servers or deployments by using structures such as
   [Data Sketches][Sketches] and [T-Digests][TDigest].

#### Non-Functional Requirements

1. The metrics library must allow millions of transactions per second in a
   single JVM process with very little overhead. The largest known application
   currently handles 4M+ RPS with 40+ threads writing to the metrics library in
   a single JVM.
2. Each service transaction may cause dozens (10+) of metric measurements.
3. Each metric may have dozens (10+) of tag dimensions, each with hundreds
   (100+) of tag values and a few (5+) fields. The combined time-series
   cardinality in a JVM can be more than 1,000,000.

#### Where are my Averages?

As mentioned above, we aspire to improve accuracy of measurements at large scale. In the past, we have
used libraries that support *Average* as an aggregation function (or field) to be emitted from each
server. When looking at these metrics across a large deployment, we tend to further aggregate the metrics
leading to incorrect results (sum of averages, average of averages, etc). Most people do this without realizing
the mistake, which is very easy to make.

In order to avoid this problem, we have taken a stance to NOT track averages and instead focus on fields that
can be further aggregated like Sum, Count, Min, Max, etc. Those who wish to obtain average values can
implement weighted-average functions at the reporting layer based on Sum and Count fields.

For example, when tracking a latency, the library would emit:

```java
api.request.latency.sum
api.request.latency.count
```

When querying the data for multiple hosts, sum all of `api.request.latency.sum` and sum all of
`api.request.latency.count`, then compute `sum (api.request.latency.sum) / sum (api.request.latency.count)`.

#### How do we achieve high performance?

We have heavily borrowed from practices commonly employed when building latency critical applications including
techniques often seen in [HFT Libraries][HFT]. Here are some of the ways in which we are able to squeeze out
the most performance from JVM -

1. Avoid Synchronization by using Java Atomic classes and low-level operations from Java's Unsafe API.
   Additionally, the data fields (arrays) are 64-byte aligned to match L1 and L2-cache line size to avoid
   the use of locks explicitly.
2. Use primitives whenever possible to avoid high object creation and GC concerns. While this may seem obvious
   we find engineers using objects excessively when primitives would suffice.
3. We have replaced Java's `HashMaps`, which tend to be object-based, with Linear Probing Tables using
   primitive (`long`) arrays.
4. The core library does not create threads. Instead writes are done using the caller's thread. Reporters manage
   their own threads for reading and publishing. This eliminates the need for a queue between caller and core library.


## Install

In order to use the Ultrabrew Metrics library, you need to add a dependency to your Java project to
the reporters you want to use in your project. All reporters included in this repository are found
in the `bintray.com` maven repository, where the `core` project libraries are found as well.

### Gradle

```gradle
repositories {
  mavenCentral()
}

dependencies {
  compile group: 'io.ultrabrew.metrics', name: 'metrics-{your reporter}', version: '0.9.0'
}
```

### Maven

```pom.xml
<dependencies>
   <dependency>
     <groupId>io.ultrabrew.metrics</groupId>
     <artifactId>metrics-{your reporter}</artifactId>
     <version>0.9.0</version>
   </dependency>
</dependencies>
```

## Usage

There are two distinct and independent phases on using the library: Instrumentation and Reporting.
The goal is to be able to instrument the code once and only modify reporting code with no or very
minimal changes to the instrumentation.

### Instrumentation

#### Definitions

##### Metric Registry

Metric Registry is a collection of metrics, which may be subscribed by a reporter. Each metric is
always associated only with a single metric registry, but reporters may subscribe to multiple metric
registries. Generally you only need one metric registry, although you may choose to use more if you
need to organize your metrics in particular reporting groups or subscribe with different reporters.

Note: All metrics have a unique identifier. You are not allowed to have multiple different types of
metrics for the same identifier. Furthermore, if you attach a reporter to multiple metric
registries, the reporter will aggregate all metrics with the same identifier. In general, it is best
to ensure that identifiers you use for metrics are globally unique.

##### Metric Types

The currently supported metric types are as follows:

- `Counter` increment or decrement a 64-bit integer value
- `Gauge` measures a 64-bit integer value at given time
- `GaugeDouble` measure a double precision floating point value at a given time
- `Timer` measure elapsed time between two events and act as counter for these events

Reporters are responsible for using the best aggregation mechanism, and proper monoid data fields,
based on the metric type and the monitoring or alerting system it is reporting to. This includes
possible mean, local minimum and maximum values, standard deviations, quantiles and others.

#### Instrument Code

An example how to create a metric registry.

```java
  MetricRegistry metricRegistry = new MetricRegistry();
```

##### Counter

An example how to use a Counter to measure a simple count with dynamic dimensions.

```java
public class TestResource {
  private static final String TAG_HOST = "host";
  private static final String TAG_CLIENT = "client";
  private final Counter errorCounter;
  private final String hostName;

  public TestResource(final MetricRegistry metricRegistry,
                      final String hostName) {
    errorCounter = metricRegistry.counter("errors");
    this.hostName = hostName;
  }

  public void handleError(final String clientId) {
    errorCounter.inc(TAG_CLIENT, clientId, TAG_HOST, hostName);

    // .. do something ..
  }
}
```

##### Gauge

An example how to use a Gauge to measure a long value at a given time. GaugeDouble works similarly,
but for double precision floating point values.

```java
public class TestResource {
  private final Gauge cacheSizeGauge;
  private final String[] tagList;
  private final Map<String,String> cache;

  public TestResource(final MetricRegistry metricRegistry, final String hostName) {
    cacheSizeGauge = metricRegistry.gauge("cacheSize");
    cache = new java.util.Map<>();
    tagList = new String[] { "host", hostName };
  }

  public void doSomething() {
    cacheSizeGauge.set(cache.size(), tagList); // this example uses only static tags
  }
}
```

##### Timer

An example how to use a Timer to measure execution time and request count with dynamic and static
dimensions.

```java
public class TestResource {
  private static final String TAG_HOST = "host";
  private static final String TAG_CLIENT = "client";
  private static final String TAG_STATUS = "status";
  private final Timer requestTimer;
  private final String hostName;

  public TestResource(final MetricRegistry metricRegistry,
                      final String hostName) {
    requestTimer = metricRegistry.timer("requests");
    this.hostName = hostName;
  }

  public void handleRequest(final String clientId) {
    final long startTime = requestTimer.start();
    int statusCode;

    // .. handle request ..

    // Note: no need for separate counter for requests per sec, as count is already included
    requestTimer.stop(startTime, TAG_CLIENT, clientId, TAG_HOST, hostName, TAG_STATUS,
      String.valueOf(statusCode));
  }
}
```

### Reporting

A reporter subscribes to a single or multiple metric registries and consumes the measurement events.
It may forward the events to an external aggregator and/or send raw events to an alerting service or
a time series database. The metrics library currently comes with the following reporters:

* `InfluxDBReporter` reports to [InfluxDB] time series database. More information
                      [here](reporter-influxdb).
* `OpenTSDBReporter` reports to [OpenTSDB] time series database. More information
                      [here](reporter-opentsdb).
* `SLF4JReporter` reports to SLF4J Logger with given name to log the aggregated values of the
                   metrics.
  > NOTE: This reporter **IS NOT** intended to be used in production environments, and is only
  > provided for debugging purposes.

#### SLF4JReporter

An example how to attach a SLF4JReporter to the metric registry, and configure it to use `metrics`
SLF4J Logger.

```java
  SLF4JReporter reporter = SLF4JReporter.builder().withName("metrics").build();
  metricRegistry.addReporter(reporter);
```

#### Histograms

In the current implementation, clients must define the distribution buckets and associate them in the reporter with the name of the metric to be histogrammed.

There two types of distribution buckets available:
- `DistributionBucket` represented by a primitive `long` array.
- `DoubleValuedDistributionBucket` represented by a primitive `double` array
  
##### DistributionBucket
Used to represent the distribution of an integer value. For example time spent in nanoseconds or size of a messaging queue.

For a given array of latency distribution in nanoseconds [0, 10_000_000, 100_000_000, 500_000_000, 1000_000_000], the buckets would be like:
* [0, 10_000_000) for 0 <= value < 9_999_999
* [10_000_000, 100_000_000) for 10_000_000 <= value < 99_999_999
* [100_000_000, 500_000_000) for 100_000_000 <= value < 499_999_999
* [500_000_000, 1000_000_000) for 500_000_000 <= value < 999_999_999
* overflow  for values  >= 1000_000_000
* underflow for values  < 0

```Java
  String metricId = "latency";
  DistributionBucket distributionBucket = new DistributionBucket(new long[]{0, 10_000_000, 100_000_000, 500_000_000, 1000_000_000});

  SLF4JReporter reporter =
      SLF4JReporter.builder().withName("metrics")
          .addHistogram(metricId, distributionBucket)    // add histogram for metric with id "latency"
          .build();
    
  String[] tagset = new String[] {"method", "GET", "resource", "metrics", "status", "200"};

  Timer timer = metricRegistry.timer(metricId);    // creates a timer metric with id "latency"

  long start = Timer.start();
  // doSomething();
  timer.stop(start, tagset); // records the latency and the distribution in nanoseconds.
```

##### DoubleValuedDistributionBucket
Used to represent the distribution of a double-precision floating point value. For example ads auction price.

For a given distribution array: [0.0, 0.25, 0.5, 1.0, 5.0, 10.0], the buckets would be like:
* [0.0, 0.25) for 0.0 <= value < 0.25
* [0.25, 0.5) for 0.25 <= value < 0.5
* [0.5, 1.0) for 0.5 <= value < 1.0
* [1.0, 5.0) for 1.0 <= value < 5.0
* [5.0, 10.0) for 5.0 <= value < 10.0
* overflow  for values  >= 10.0
* underflow for values  < 0.0

```Java
  String metricId = "auction_price";
  DoubleValuedDistributionBucket distributionBucket = new DoubleValuedDistributionBucket(new double[]{0.0, 0.25, 0.5, 1.0, 5.0, 10.0});

  SLF4JReporter reporter =
      SLF4JReporter.builder().withName("metrics")
            .addHistogram(metricId, distributionBucket)    // add histogram for metric with id "auction_price"
            .build();

  String[] tagset = new String[] {"experiment", "exp1"};

  GaugeDouble auctionPrice = metricRegistry.gaugeDouble(metricId);    // creates a gauge double metric with id "auction_price"

  auctionPrice.set(getAuctionPrice(), tagset); // records the auction_price and the distribution.
```


## Contribute

Please refer to [the Contributing.md file](Contributing.md) for information about how to get
involved. We welcome issues, questions, and pull requests. Pull Requests are welcome.

## Maintainers

* Mika Mannermaa @mmannerm
* Smruti Ranjan Sahoo @smrutilal2
* Ilpo Ruotsalainen @lonemeow
* Chris Larsen @manolama
* Arun Gupta @arungupta

## License

This project is licensed under the terms of the [Apache 2.0](LICENSE) open source license. Please
refer to [LICENSE](LICENSE) for the full terms.

[DropWizard]: https://metrics.dropwizard.io
[Monoid]: CONCEPTS.md#monoid
[Tags]: CONCEPTS.md#tag-key-and-tag-value
[statsd]: https://github.com/etsy/statsd
[InfluxDB]: https://www.influxdata.com/time-series-platform/influxdb/
[OpenTSDB]: http://opentsdb.net
[Sketches]: https://datasketches.github.io/
[TDigest]: https://github.com/tdunning/t-digest
[HFT]: https://github.com/OpenHFT
[BuildBanner]: https://travis-ci.com/ultrabrew/metrics.svg?branch=master
[Travis]: https://travis-ci.com/ultrabrew/metrics
