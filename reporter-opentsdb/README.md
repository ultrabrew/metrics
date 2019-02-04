# OpenTSDBReporter

This reporter will push batches of metrics to an OpenTSDB HTTP API server as JSON object.

Configuration parameters include:

* **host** - *(required)* A hostname including protocol, host and optional port. E.g. `http://localhost:4242`. Note that the host must start with a protocol of either `http://` or `https://`.
* **endpoint** - *(Default: `/api/put`)* A string with the endpoint to post results to. Note that the endpoint must start with a forward slash and can be `/`.
* **batchSize** - *(Default: `64`)* The maximum number of measurements to flush in each batch.
* **timestampsInMilliseconds** - (Default: `false`) Whether or not to post timestamps in seconds `false` or milliseconds `true`.

To instantiate and run the reporter execute:

```Java
OpenTSDBConfig config = OpenTSDBConfig.newBuilder()
  .setHost("http://localhost:4242")
  .build();

// 60 second reporting window
OpenTSDBReporter reporter = new OpenTSDBReporter(config, 60);
MetricRegistry metricRegistry = new MetricRegistry();
metricRegistry.addReporter(reporter);
```