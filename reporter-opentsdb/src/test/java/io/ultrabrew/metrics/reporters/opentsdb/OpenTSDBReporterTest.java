// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters.opentsdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.MetricRegistry;
import java.io.IOException;
import java.net.URI;
import mockit.Capturing;
import mockit.Deencapsulation;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

public class OpenTSDBReporterTest {

  private static final URI TEST_URI = URI.create("http://localhost:4242/");

  private OpenTSDBReporter makeReporter() {
    return OpenTSDBReporter.builder()
        .withBaseUri(TEST_URI)
        .build();
  }

  @Test
  public void testReporting(@Mocked CloseableHttpClient httpClient,
      @Mocked CloseableHttpResponse closeableHttpResponse,
      @Mocked StatusLine statusLine)
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
    OpenTSDBReporter reporter = makeReporter();
    registry.addReporter(reporter);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      HttpUriRequest request;
      httpClient.execute(request = withCapture());
      assertEquals("/api/v1/put", request.getURI().getPath());
      times = 1;
    }};
  }

  @Test
  public void testReportingBaseUriWithPath(@Mocked CloseableHttpClient httpClient,
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
    OpenTSDBReporter reporter = OpenTSDBReporter.builder()
        .withBaseUri(URI.create("http://localhost:4242/some/path/"))
        .build();
    registry.addReporter(reporter);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      HttpUriRequest request;
      httpClient.execute(request = withCapture());
      assertEquals("/some/path/api/v1/put", request.getURI().getPath());
      times = 1;
    }};
  }

  @Test
  public void testSetApiEndpoint(@Mocked CloseableHttpClient httpClient,
      @Mocked CloseableHttpResponse closeableHttpResponse,
      @Mocked StatusLine statusLine)
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
    OpenTSDBReporter reporter = OpenTSDBReporter.builder()
        .withBaseUri(URI.create("http://localhost:4242/"))
        .withApiEndpoint("very/special/put")
        .build();
    registry.addReporter(reporter);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      HttpUriRequest request;
      httpClient.execute(request = withCapture());
      assertEquals("/very/special/put", request.getURI().getPath());
      times = 1;
    }};
  }

  @Test
  public void testUploadFailedServerError(@Mocked CloseableHttpClient httpClient,
      @Mocked CloseableHttpResponse closeableHttpResponse,
      @Mocked StatusLine statusLine,
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
    OpenTSDBReporter reporter = makeReporter();
    registry.addReporter(reporter);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      logger.error(anyString, withInstanceOf(IllegalStateException.class));
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
    OpenTSDBReporter reporter = makeReporter();
    registry.addReporter(reporter);

    Counter counter = registry.counter("counter");
    counter.inc("tag", "value");

    Thread.sleep(3000);

    new Verifications() {{
      logger.error(anyString, withInstanceOf(Throwable.class));
    }};
  }

  @Test
  public void testSetWindow() {
    OpenTSDBReporter r = OpenTSDBReporter.builder()
        .withBaseUri(TEST_URI)
        .withWindowSize(12765)
        .build();

    long actualWindowSizeMillis = Deencapsulation.getField(r, "windowStepSizeMillis");
    assertEquals(12765 * 1000, actualWindowSizeMillis);
  }

  @Test
  public void testSetBatchSize() {
    OpenTSDBReporter r = OpenTSDBReporter.builder()
        .withBaseUri(TEST_URI)
        .withBatchSize(123)
        .build();

    OpenTSDBHttpClient client = Deencapsulation.getField(r, "client");
    int actualBatchSize = Deencapsulation.getField(client, "batchSize");
    assertEquals(123, actualBatchSize);
  }

  @Test
  public void testMissingBaseUri() {
    assertThrows(IllegalArgumentException.class, () -> OpenTSDBReporter.builder().build());
  }

  @Test
  public void testInvalidBaseUri() {
    assertThrows(IllegalArgumentException.class,
        () -> OpenTSDBReporter.builder().withBaseUri(URI.create("http://localhost:4242")));
  }

  @Test
  public void testInvalidBatchSize() {
    assertThrows(IllegalArgumentException.class, () -> OpenTSDBReporter.builder().withBatchSize(0));
  }
}
