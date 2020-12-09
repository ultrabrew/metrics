// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters.influxdb;

import io.ultrabrew.metrics.util.Strings;
import java.util.Arrays;
import java.io.IOException;
import java.net.URI;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides methods to access InfluxDB.
 */
public class InfluxDBClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(InfluxDBClient.class);
  
  private static final String UTF_8 = StandardCharsets.UTF_8.name();
  private static final byte WHITESPACE = ' ';
  private static final byte COMMA = ',';
  private static final byte EQUALS = '=';
  private static final byte NEWLINE = '\n';

  private final ByteBuffer byteBuffer;
  private final URI dbUri;
  private final CloseableHttpClient httpClient;

  InfluxDBClient(final URI dbUri, final int bufferSize) {
    this.httpClient = getHttpClient();
    this.byteBuffer = ByteBuffer.allocate(bufferSize);
    this.dbUri = dbUri;
  }

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
        .build();
  }

  private void doWrite(final String measurement, final String[] tags, final String[] fields,
      final long timestamp)
      throws IOException {
    if (Strings.isNullOrEmpty(measurement)) {
      LOGGER.warn("Null or empty measurement.");
      return;
    }
    int rollback = byteBuffer.position();
    byteBuffer.put(measurement.getBytes(UTF_8));
    for (int i = 0; i < tags.length; i += 2) {
      if (Strings.isNullOrEmpty(tags[i])) {
        LOGGER.warn("Null or empty tag key in tags array: {} for measurement {}", 
          Arrays.toString(tags), measurement);
        byteBuffer.position(rollback);
        return;
      }
      if (Strings.isNullOrEmpty(tags[i + 1])) {
        LOGGER.warn("Null or empty tag value in tags array: {} for measurement {}", 
          Arrays.toString(tags), measurement);
        byteBuffer.position(rollback);
        return;
      }
      byteBuffer.put(COMMA)
          .put(tags[i].getBytes(UTF_8))
          .put(EQUALS)
          .put(tags[i + 1].getBytes(UTF_8));
    }
    byteBuffer.put(WHITESPACE);

    boolean f = true;
    for (int i = 0; i < fields.length; i += 2) {
      if (!f) {
        byteBuffer.put(COMMA);
      }
      if (Strings.isNullOrEmpty(fields[i])) {
        LOGGER.warn("Null or empty field name in array: {} for measurement {}", 
          Arrays.toString(fields), measurement);
        byteBuffer.position(rollback);
        return;
      }
      if (Strings.isNullOrEmpty(fields[i + 1])) {
        LOGGER.warn("Null or empty field value in array: {} for measurement {}", 
          Arrays.toString(fields), measurement);
        byteBuffer.position(rollback);
        return;
      }
      byteBuffer.put(fields[i].getBytes(UTF_8))
          .put(EQUALS)
          .put(fields[i + 1].getBytes(UTF_8));
      f = false;
    }
    if (timestamp > 0) {
      byteBuffer.put(WHITESPACE)
          .put(Long.toString(timestamp).getBytes(UTF_8));
    }
    byteBuffer.put(NEWLINE);
  }

  public void write(final String measurement, final String[] tags, final String[] fields,
      final long timestamp)
      throws IOException {
    // CLOVER:OFF
    // The loop exit condition is supposed to be unreachable
    for (int retry = 0; retry < 2; retry++) {
      // CLOVER:ON
      byteBuffer.mark();
      try {
        doWrite(measurement, tags, fields, timestamp);
        return;
      } catch (BufferOverflowException e) {
        byteBuffer.reset();
        if (byteBuffer.position() == 0) {
          throw new IllegalArgumentException("Internal buffer too small to fit one measurement");
        } else {
          flush();
        }
      }
    }
    // CLOVER:OFF
    // This should be truly unreachable
    throw new RuntimeException("Internal error");
    // CLOVER:ON
  }

  public void flush() throws IOException {
    if (byteBuffer.position() == 0) {
      return;
    }
    byteBuffer.flip();
    HttpPost httpPost = new HttpPost(this.dbUri);
    httpPost.setEntity(
        new ByteArrayEntity(byteBuffer.array(), 0, byteBuffer.limit(), ContentType.DEFAULT_TEXT));
    try {
      CloseableHttpResponse response = httpClient.execute(httpPost);
      EntityUtils.consumeQuietly(response.getEntity());
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode / 100 != 2) {
        throw new IOException(
            "InfluxDB write failed: " + statusCode + " " + response.getStatusLine()
                .getReasonPhrase());
      }
    } finally {
      // Always clear the buffer. But this will lead to data loss in case of non 2xx response (i.e write operation failed)
      // received from the InfluxDB server. Ideally non 2xx server response should be rare but revisit this part
      // if data loss occurs frequently.
      byteBuffer.clear();
    }
  }
  
}
