// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters.opentsdb;

import io.ultrabrew.metrics.util.Strings;
import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client that writes metrics in batches to an OpenTSDB HTTP endpoint. Note a connection to the
 * host is opened and kept open so the client lacks built-in load balancing at this time.
 */
class OpenTSDBHttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(OpenTSDBHttpClient.class);

  private static final char[] METRIC = "{\"metric\":".toCharArray();
  private static final char[] TIMESTAMP = "\"timestamp\":".toCharArray();
  private static final char[] VALUE = "\"value\":".toCharArray();
  private static final char[] TAGS = "\"tags\":{".toCharArray();

  protected final ByteArrayOutputStream buffer;
  private final CloseableHttpClient httpClient;
  private final URI dbUri;
  private final int batchSize;
  private final boolean timestampsInMilliseconds;
  protected final PrintWriter writer;
  protected int currentBatchSize = 0;

  public OpenTSDBHttpClient(final URI dbUri, int batchSize, boolean timestampsInMilliseconds) {
    this.dbUri = dbUri;
    this.batchSize = batchSize;
    this.timestampsInMilliseconds = timestampsInMilliseconds;
    this.httpClient = getHttpClient();
    buffer = new ByteArrayOutputStream(batchSize * 256);
    final OutputStreamWriter utf8_writer = new OutputStreamWriter(buffer,
        StandardCharsets.UTF_8.newEncoder());
    writer = new PrintWriter(utf8_writer);
    writer.write('[');
  }

  // TODO - configs for these values.
  private CloseableHttpClient getHttpClient() {
    RequestConfig httpRequestConfig = RequestConfig.custom()
        .setConnectTimeout(3000)
        .setSocketTimeout(3000)
        .setConnectionRequestTimeout(3000)
        .build();

    return HttpClients.custom()
        .disableCookieManagement()
        .setMaxConnPerRoute(2 * 3)
        .setMaxConnTotal(2 * 3)
        .setDefaultRequestConfig(httpRequestConfig)
        .setRetryHandler(new DefaultHttpRequestRetryHandler())
        .build();
  }

  void write(final String metricName,
      final String[] tags,
      final long timestamp,
      final String value) throws IOException {
    // validation
    if (Strings.isNullOrEmpty(metricName)) {
      LOG.warn("Null or empty metric name.");
      return;
    }
    if (Strings.isNullOrEmpty(value)) {
      LOG.warn("Null or empty value.");
      return;
    }
    if (tags == null) {
      LOG.warn("At least one tag pair must be present.");
      return;
    }
    if (tags.length % 2 != 0) {
      LOG.warn("Uneven tag count: {} for metric {}", Arrays.toString(tags), metricName);
      return;
    }
    for (int i = 0; i < tags.length; i++) {
      if (i % 2 == 0 && Strings.isNullOrEmpty(tags[i])) {
        LOG.warn("Null tag key in: {} for metric {}", Arrays.toString(tags), metricName);
        return;
      }
    }
    
    if (currentBatchSize++ > 0) {
      writer.write(',');
    }

    writer.write(METRIC);
    writeEscapedString(metricName);
    writer.write(',');
    writer.write(TIMESTAMP);
    // TODO we could optimize this out to write directly but... yuk
    writer.write(Long.toString((timestampsInMilliseconds ? timestamp : timestamp / 1_000)));
    writer.write(',');
    writer.write(TAGS);
    boolean toggle = true;
    for (int i = 0; i < tags.length; i++) {
      if (i % 2 != 0 && tags[i] == null) {
        writeEscapedString("NULL");
      } else {
        writeEscapedString(tags[i]);
      }
      if (toggle) {
        writer.write(':');
      } else if (i + 1 < tags.length) {
        writer.write(',');
      }
      toggle = !toggle;
    }
    writer.write('}'); // end tags
    writer.write(',');
    writer.write(VALUE);
    writer.write(value);
    writer.write('}'); // end obj

    // see if we need to flush it.
    if (currentBatchSize >= batchSize) {
      flush();
    }
  }

  void flush() throws IOException {
    if (currentBatchSize < 1) {
      return;
    }

    // flush
    writer.write(']');
    writer.flush();
    HttpPost httpPost = new HttpPost(dbUri);
    httpPost.setEntity(new ByteArrayEntity(buffer.toByteArray(), ContentType.APPLICATION_JSON));
    final StatusLine status;
    CloseableHttpResponse response = null;
    try {
      response = httpClient.execute(httpPost);
      status = response.getStatusLine();
      // CLOVER:OFF
      // Just tracing.
      if (LOG.isTraceEnabled()) {
        LOG.trace("Response from OpenTSDB [{}] ",
            EntityUtils.toString(response.getEntity()));
      }
      // CLOVER:ON
    } finally {
      if (response != null) {
        EntityUtils.consumeQuietly(response.getEntity());
      }
      // reset our buffer and batch size
      currentBatchSize = 0;
      buffer.reset();
      writer.write('[');
    }
    if (status.getStatusCode() / 100 != 2) {
      throw new IllegalStateException(String.format(
          "Failed to write metrics to OpenTSDB '%d' - '%s'",
          status.getStatusCode(),
          status.getReasonPhrase()));
    }
  }

  /**
   * Escapes quotes and back slashes to make sure we satisfy the JSON spec and surrounds the string
   * in quotes.
   *
   * @param string The non-null and non-empty string to escape.
   */
  void writeEscapedString(final String string) {
    writer.write('"');
    for (int i = 0; i < string.length(); i++) {
      int cp = string.codePointAt(i);
      if (Character.isISOControl(cp)) {
        throw new IllegalArgumentException("Invalid control character in metric or tag: " + cp);
      }
      if (cp == '"' || cp == '\\') {
        writer.write('\\');
      }
      writer.write(cp);
    }
    writer.write('"');
  }
}