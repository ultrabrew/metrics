// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters.influxdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.MetricRegistry;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

public class InfluxDBReporterTest {

  private static final URI TEST_URI = URI.create("http://localhost:8086");

  @Test
  public void testMissingBaseUri() {
    assertThrows(IllegalArgumentException.class,
        () -> InfluxDBReporter.builder().withDatabase("test").build());
  }

  @Test
  public void testMissingDatabase() {
    assertThrows(IllegalArgumentException.class,
        () -> InfluxDBReporter.builder().withBaseUri(TEST_URI).build());
  }

  @Test
  public void testSetWindow() {
    InfluxDBReporter r = InfluxDBReporter.builder()
        .withBaseUri(TEST_URI)
        .withDatabase("test")
        .withWindowSize(12765)
        .build();

    long actualWindowSizeMillis = Deencapsulation.getField(r, "windowStepSizeMillis");
    assertEquals(12765 * 1000, actualWindowSizeMillis);
  }

  @Test
  public void testSetBufferSize() {
    InfluxDBReporter r = InfluxDBReporter.builder()
        .withBaseUri(TEST_URI)
        .withDatabase("test")
        .withBufferSize(12765)
        .build();

    InfluxDBClient c = Deencapsulation.getField(r, "dbClient");
    ByteBuffer buffer = Deencapsulation.getField(c, "byteBuffer");
    assertEquals(12765, buffer.capacity());
  }
  
  @Test
  public void testSeEndpoint(@Mocked CloseableHttpClient httpClient,
      @Mocked CloseableHttpResponse closeableHttpResponse, @Mocked StatusLine statusLine)
      throws InterruptedException, IOException {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 200;
    }};
    
    MetricRegistry registry = new MetricRegistry();
    InfluxDBReporter r = InfluxDBReporter.builder()
        .withBaseUri(TEST_URI)
        .withDatabase("test") // ignored
        .withEndpoint("/my/change?db=foo")
        .withBufferSize(12765)
        .build();

    registry.addReporter(r);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      HttpPost request;
      httpClient.execute(request = withCapture());
      times = 1;
      assertEquals(TEST_URI + "/my/change?db=foo", request.getURI().toString());
    }};
  }

  @Test
  public void testReporting(@Mocked CloseableHttpClient httpClient,
      @Mocked CloseableHttpResponse closeableHttpResponse, @Mocked StatusLine statusLine)
      throws InterruptedException, IOException {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 200;
    }};

    MetricRegistry registry = new MetricRegistry();
    InfluxDBReporter reporter = InfluxDBReporter.builder()
        .withBaseUri(URI.create("http://localhost:8086"))
        .withDatabase("test")
        .build();
    registry.addReporter(reporter);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      httpClient.execute((HttpUriRequest) any);
      times = 1;
    }};
  }

  @Test
  public void testUploadFailedServerError(@Mocked CloseableHttpClient httpClient,
      @Mocked CloseableHttpResponse closeableHttpResponse, @Mocked StatusLine statusLine,
      @Capturing Logger logger) throws IOException, InterruptedException {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 500;
    }};

    MetricRegistry registry = new MetricRegistry();
    InfluxDBReporter reporter = InfluxDBReporter.builder()
        .withBaseUri(URI.create("http://localhost:8086"))
        .withDatabase("test")
        .build();
    registry.addReporter(reporter);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      logger.error(anyString, withInstanceOf(IOException.class));
    }};
  }

  @Test
  public void testUploadFailedException(@Mocked CloseableHttpClient httpClient,
      @Capturing Logger logger) throws IOException, InterruptedException {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = new IOException();
    }};

    MetricRegistry registry = new MetricRegistry();
    InfluxDBReporter reporter = InfluxDBReporter.builder()
        .withBaseUri(URI.create("http://localhost:8086"))
        .withDatabase("test")
        .build();
    registry.addReporter(reporter);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      logger.error(anyString, withInstanceOf(IOException.class));
    }};
  }
}
