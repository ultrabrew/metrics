# OpenTSDBReporter

This reporter will push batches of metrics to an OpenTSDB HTTP API server as JSON object.

Configuration parameters include:

* **host** - *(required)* A hostname including protocol, host and optional port. E.g. `http://localhost:4242`. Note that the host must start with a protocol of either `http://` or `https://`.
* **endpoint** - *(Default: `/api/put`)* A string with the endpoint to post results to. Note that the endpoint must start with a forward slash and can be `/`.
* **batchSize** - *(Default: `64`)* The maximum number of measurements to flush in each batch.
* **timestampsInMilliseconds** - (Default: `false`) Whether or not to post timestamps in seconds `false` or milliseconds `true`.
* **windowSize** - (Default: `1`) How often to report to the API in seconds.

To instantiate and run the reporter execute:

```Java
OpenTSDBReporter.Builder reporter_builder =
          OpenTSDBReporter.builder()
              .withBaseUri(URI.create("http://localhost:4242")))
              .withBatchSize(64)
              .withWindowSize(60);
if (!Strings.isNullOrEmpty(tsdb.getConfig().getString(TSD_ENDPOINT))) {
  reporter_builder.withApiEndpoint("/proxy/opentsdb/api/put);
}
OpenTSDBReporter opentsdb_reporter = reporter_builder.build();
MetricRegistry metricRegistry = new MetricRegistry();
metric_registry.addReporter(opentsdb_reporter);

// on program shutdown
opentsdb_reporter.close();
```