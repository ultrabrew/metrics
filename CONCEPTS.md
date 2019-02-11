# Concepts

## Table of Contents
- [Metric](#metric)
- [Tag Key and Tag Value](#tag-key-and-tag-value)
- [Timestamp](#timestamp)
- [Timeseries](#timeseries)
- [Gauge](#gauge)
- [Counter](#counter)
- [Timer](#timer)
- [Metric Registry](#metric-registry)
- [Reporter](#reporter)
- [Time Window](#time-window)
- [Monoid](#monoid)

### Metric
A quantifiable characteristic of an application that is being measured. Metrics have numeric values that are either 64-bit signed integers or 64-bit double-precision floating-point values. Metrics have names that are strings and should meaningfully describe the quantity being measured. Some examples -
* `webserver.requests` the number of requests per second being processed by a web server
* `webserver.request.latency` time taken (latency, cumulative or percentiles) to process requests by a web server

### Tag Key and Tag Value
A tag (aka dimension) is a pair that consists of a key and a value. Tags describe the context for which a metric is being recorded. For example, `host=webserver1.domain.com`. Here, `host` is a tag key and `webserver1.domain.com` is the tag value. Other examples could include `url`, `resource-path`, `response-code`, `application-id`, `region`, or `availability-zone`.

In addition to being more descriptive, tags allow identification of metrics at a fine-grained level. These could be used for flexible analysis via monitoring and reporting systems whereby one could get data summaries along any combination of tags along which the metrics were recorded. For example, total number of requests handled by all web servers in a region.

### Timestamp
For the purposes of this library, a timestamp represents the end of a time interval for which a metric has been recorded and reported. If a metric is reported every 10 seconds, the timestamp should represent the end of a given 10-second interval, e.g., 10, 20, or 30 seconds past the minute. If metrics are reported immediately upon receipt by the library, the timestamp should represent the moment when the update was recorded.

### Timeseries
A time series is a sequence of timestamp and numeric-value pairs where consecutive timestamps are monotonically increasing. For our purposes, the identity of a time series is the unique combination of metric name and associated tags, and the series itself consists of the timestamps for which values were reported and values of the metric at those timestamps.

The values in a timeseries can be sparse i.e., there could be discontinuity in the sequence of timestamp-value pairs. An example of this could be number of errors. An application might not generate errors during most time-intervals but might do so for some. As a result, values would only show up for intervals when errors were recorded.

`metric name: webserver.errors`
| timestamp | value |
|      ---: |  ---: | 
|     12:00 |     5 |
|     12:01 |     3 |
|     12:05 |     4 |
|     12:12 |    15 |
|     12:40 |     2 |

### Gauge
A Gauge is a type of metric whose value can increase or decrease over various measurements. Gauges are represented using 64-bit longs. There is an implementation for Java Doubles as well. Gauges are aggregated over time and reported as
* `Sum` (e.g., total latency of request processing over a minute)
* `Count` (e.g., number of requests processed over a minute)
* `Min` (e.g., lowest latency across all requests over a minute)
* `Max` (e.g., highest latency across all requests over a minute)

### Counter
Counter is a type of metric whose value can increase or decrease by whole numbers. It is stored in a 64-bit long. Counter values are aggregated over time and reported as
* `Sum` (e.g., total count of requests processed over a minute)

### Timer
Timer is a type of metric useful for recording information about application latency. The underlying implementation of a timer is same as a Gauge with the same aggregations being reported. We plan to add higher-order aggregations for timers such as Histograms, Sketches, and T-digests for more accurate calculation of percentiles across servers or application instances. Timers have a helper function for recording the initial timestamp when the code to be measured starts executing.

### Metric Registry
Metric Registry holds the configurations for metrics including metric name and type, as well as Metrics Reporters that will track and report the metrics.

### Reporter
Reporters (aka Metric Reporters) track metrics and handle the work of reporting them somewhere. Usually, reporters aggregate metrics over a predefined time-interval (e.g., 1 second, 10 seconds, 1 minute) and then forward them to monitoring systems for storage, analysis and alerting. Reporters are extensions points for this library. We anticipate the collection of reporters will grow over time as more teams use this library.

### Time Window
Reporters that aggregate metrics over time intervals should hold metric state for two intervals: one for data presently arriving, and one for data that arrived in the past. Past data can then be reported without locking state for the present data. We call this set of two intervals a time window. Users can extend our time-window reporter to facilitate tracking of state across the intervals.

```
application started at 12:00.00
wallclock time: 12:00.01
+--------------------------+
|   12:00    |    12:01    |
| (current)  |             |
|  writes go |   not in    |
|  here      |   use       |
+--------------------------+

wallclock time: 12:01.01
+--------------------------+
|   12:00    |    12:01    |
| (previous) |  (current)  |
|  reporters |   writes go |
|  read here |   here      |
+--------------------------+

wallclock time: 12:02.01
+--------------------------+
|   12:01    |    12:02    |
| (previous) |  (current)  |
|  reporters |   writes go |
|  read here |   here      |
+--------------------------+

```

### Monoid
To achieve accurate aggregates over large deployments, value aggregations should be associative. For example, average latency and latency percentiles should not be calculated at each host because such values cannot be further combined from multiple hosts in any meaningful or mathematically-correct manner. Using monoids such as sum and count help (for mean latency) since they can indeed be added across hosts to obtain total latency across all hosts as well as total number of requests, which can
then be used to compute weighted mean latency (`total latency`, i.e., `sum  / total number of requests`). Similarly, percentiles can be more accurately estimated by relying on richer data structures such as histograms, data sketches or n-grams. Hence, this library only supports monoids.

Read more about [Monoids][Monoid].


[Monoid]: https://en.wikipedia.org/wiki/Monoid
