# InfluxReporter

This reporter will push batches of metrics to an InfluxDB V1x HTTP API server as JSON object. Note that a database name must be included.

Configuration parameters include:

* **baseUri** - *(required)* A hostname including protocol, host and optional port. E.g. `http://localhost:4242`. Note that the host must start with a protocol of either `http://` or `https://`.
* **endpoint** - *(Default: `/write?db=`)* A string with the endpoint to post results to. Note that the endpoint must start with a forward slash and can be `/?db=`. If this parameter is set then the `database` parameter will be ignored and must be supplied as a query parameter in the endpoint string.
* **database** - *(required)* A string denoting the InfluxDB database to send measurements to.
* **bufferSize** - *(Default: `64 * 1024`)* The maximum size of the buffer before it's flushed.
* **windowSize** - (Default: `1`) How often to report to the API in seconds.

To instantiate and run the reporter execute:

```Java
InfluxDBReporter.Builder reporter_builder = 
    InfluxDBReporter.builder()
          .withBaseUri(URI.create("http://localhost:4242")))
          .withDatabase("Ultrabrew")
          .withWindowSize(60);

InfluxDBReporter reporter = reporter_builder.build();
MetricRegistry metricRegistry = new MetricRegistry();
metric_registry.addReporter(reporter);

// on program shutdown
reporter.close();
```