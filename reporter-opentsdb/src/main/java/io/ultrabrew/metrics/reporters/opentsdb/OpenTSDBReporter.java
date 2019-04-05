// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters.opentsdb;

import io.ultrabrew.metrics.Metric;
import io.ultrabrew.metrics.data.Aggregator;
import io.ultrabrew.metrics.data.Cursor;
import io.ultrabrew.metrics.data.Type;
import io.ultrabrew.metrics.reporters.TimeWindowReporter;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reporter that sends data to an OpenTSDB host (or rotation) via HTTP POSTs. It will send batches
 * of data to the configured host and endpoint.
 *
 * <p>Usage:
 * <pre>
 * OpenTSDBReporter reporter = OpenTSDBReporter.builder()
 *   .withBaseUri(URI.create("http://localhost:4242"))
 *   .build();
 * MetricRegistry metricRegistry = new MetricRegistry();
 * metricRegistry.addReporter(reporter);
 * </pre>
 */
public class OpenTSDBReporter extends TimeWindowReporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenTSDBReporter.class);
  private static final String DEFAULT_API_ENDPOINT = "api/v1/put";
  private static final int DEFAULT_BATCH_SIZE = 64;
  private final OpenTSDBHttpClient client;
  private long lastReportedTimestamp = 0;

  private OpenTSDBReporter(final String name, final OpenTSDBHttpClient client,
      final int windowSeconds,
      final Map<Class<? extends Metric>, Function<Metric, ? extends Aggregator>> defaultAggregators,
      final Map<String, Function<Metric, ? extends Aggregator>> metricAggregators) {

    super(name, windowSeconds, defaultAggregators, metricAggregators);
    this.client = client;
    this.start();
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  protected void doReport(final Map<String, Aggregator> aggregators) {
    long newestTimestamp = 0;
    try {
      for (final Map.Entry<String, Aggregator> entry : aggregators.entrySet()) {
        final Aggregator aggregator = entry.getValue();
        final Cursor cursor = aggregator.cursor();
        final String metricName = entry.getKey();
        while (cursor.next()) {
          if (cursor.lastUpdated() > lastReportedTimestamp) {
            final String[] fields = cursor.getFields();
            final Type[] types = cursor.getTypes();
            for (int i = 0; i < fields.length; i++) {
              client.write(metricName, cursor.getTags(), cursor.lastUpdated(),
                  types[i].readAndReset(cursor, i));
            }
            newestTimestamp = Math.max(newestTimestamp, cursor.lastUpdated());
          }
        }
      }
      client.flush();
    } catch (IOException t) {
      LOGGER.error("Failed to send data", t);
    }
    if (newestTimestamp > 0) {
      lastReportedTimestamp = newestTimestamp;
    }
  }

  public static class Builder extends TimeWindowReporterBuilder<Builder, OpenTSDBReporter> {

    private URI baseUri;
    private String apiEndpoint = DEFAULT_API_ENDPOINT;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private boolean timestampsInMilliseconds = true;
    private int windowSeconds = 1;

    /**
     * Set the base URI of the OpenTSDB installation. The path component of the URI must end with a
     * slash.
     */
    public Builder withBaseUri(URI baseUri) {
      if (!baseUri.getPath().endsWith("/")) {
        throw new IllegalArgumentException("Base URI path must end with '/'");
      }
      this.baseUri = baseUri;
      return this;
    }

    /**
     * Set the API endpoint path to use. Should not be used unless the OpenTSDB installation is
     * behind a load balancer or reverse proxy that does path mapping. This must point to an
     * endpoint compatible with v1 of the OpenTSDB HTTP put API.
     */
    public Builder withApiEndpoint(String apiEndpoint) {
      this.apiEndpoint = apiEndpoint;
      return this;
    }

    /**
     * Sets the number of metrics (measurements) to send in each batch. Defaults to 64.
     *
     * @param batchSize number of measurements to send in each batch
     */
    public Builder withBatchSize(final int batchSize) {
      if (batchSize < 1) {
        throw new IllegalArgumentException("Batch size must be greater than or equal to 1");
      }
      this.batchSize = batchSize;
      return this;
    }

    /**
     * Set the reporting time window size.
     *
     * @param windowSeconds reporting time window in seconds
     */
    public Builder withWindowSize(int windowSeconds) {
      this.windowSeconds = windowSeconds;
      return this;
    }

    /**
     * Create an {@link OpenTSDBReporter} instance.
     */
    @Override
    public OpenTSDBReporter build() {
      if (baseUri == null) {
        throw new IllegalArgumentException("Invalid baseUri");
      }
      URI dbUri = baseUri.resolve(apiEndpoint);
      OpenTSDBHttpClient client = new OpenTSDBHttpClient(dbUri, batchSize,
          timestampsInMilliseconds);
      return new OpenTSDBReporter(dbUri.toString(), client, windowSeconds, defaultAggregators,
          metricAggregators);
    }
  }
}
