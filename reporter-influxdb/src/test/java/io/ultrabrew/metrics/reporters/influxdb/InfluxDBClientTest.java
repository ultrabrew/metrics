// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters.influxdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.http.util.EntityUtils;

public class InfluxDBClientTest {

  @Mocked
  CloseableHttpClient httpClient;

  @Mocked
  StatusLine statusLine;

  @Mocked
  CloseableHttpResponse closeableHttpResponse;

  private InfluxDBClient client;

  @BeforeEach
  public void before() throws Exception {
    client = new InfluxDBClient(URI.create("http://localhost:8086/write?db=test"), 64 * 1024);
  }

  @Test
  public void testWriteSuccessful() throws Exception {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 204;
      closeableHttpResponse.getEntity();
      result = new BasicHttpEntity();
    }};
    String[] tags = {"host", "server01", "region", "us-west"};
    String[] fields = {"temp", "80", "fanSpeed", "743"};
    client.write("cpu_load_short", tags, fields, 1534055562000000003L);
    client.write("cpu_load_short", tags, fields, 1534055562000000004L);
    client.flush();
    client.write("cpu_load_short", tags, fields, 1534055562000000007L);
    client.write("cpu_load_short", tags, fields, 1534055562000000008L);
    client.flush();

    new Verifications() {{
      EntityUtils.consumeQuietly((HttpEntity) any);
      times = 2;
    }};
  }

  @Test
  public void testWriteFails() throws Exception {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 500;
      closeableHttpResponse.getEntity();
      result = new BasicHttpEntity();
    }};
    String[] tags = {"host", "server01", "region", "us-west"};
    String[] fields = {"temp", "80", "fanSpeed", "743"};
    client.write("cpu_load_short", tags, fields, 1534055562000000003L);
    client.write("cpu_load_short", tags, fields, 1534055562000000004L);
    assertThrows(IOException.class, client::flush);
    client.write("cpu_load_short", tags, fields, 1534055562000000007L);
    client.write("cpu_load_short", tags, fields, 1534055562000000008L);
    assertThrows(IOException.class, client::flush);

    new Verifications() {{
      EntityUtils.consumeQuietly((HttpEntity) any);
      times = 2;
    }};
  }

  @Test
  public void testWriteFailsIllegalHttpResponse() throws Exception {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = null;
      closeableHttpResponse.getEntity();
      result = new BasicHttpEntity();
    }};
    String[] tags = {"host", "server01", "region", "us-west"};
    String[] fields = {"temp", "80", "fanSpeed", "743"};
    client.write("cpu_load_short", tags, fields, 1534055562000000003L);
    client.write("cpu_load_short", tags, fields, 1534055562000000004L);
    assertThrows(RuntimeException.class, client::flush);
    client.write("cpu_load_short", tags, fields, 1534055562000000007L);
    client.write("cpu_load_short", tags, fields, 1534055562000000008L);
    assertThrows(RuntimeException.class, client::flush);

    new Verifications() {{
      EntityUtils.consumeQuietly((HttpEntity) any);
      times = 2;
    }};
  }

  @Test
  public void testWriteSplitting() throws IOException {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 200;
    }};

    InfluxDBClient c = new InfluxDBClient(URI.create("http://localhost:8086/write?db=test"), 10);
    // 4+1+1+1+1 = 8 bytes
    c.write("test", new String[]{}, new String[]{"a", "1"}, 0);
    // 4+1+1+1+1 = 8 bytes
    // Won't fit in buffer (8+8 > 10), should automatically flush previous
    c.write("test", new String[]{}, new String[]{"a", "2"}, 0);
    c.flush();

    new Verifications() {{
      httpClient.execute((HttpUriRequest) any);
      times = 2;
    }};
  }

  @Test
  public void testTooLargeMeasurement() throws IOException {
    InfluxDBClient c = new InfluxDBClient(URI.create("http://localhost:8086/write?db=test"), 10);
    assertThrows(IllegalArgumentException.class,
        () -> c.write("thisisaverylongmeasurementname", new String[]{},
            new String[]{"verylongfieldname", "1234567890"}, 0));
  }

  @Test
  public void testTooLargeMeasurementAfterOne() throws IOException {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 200;
    }};

    InfluxDBClient c = new InfluxDBClient(URI.create("http://localhost:8086/write?db=test"), 10);
    c.write("test", new String[]{}, new String[]{"a", "1"}, 0);
    assertThrows(IllegalArgumentException.class,
        () -> c.write("thisisaverylongmeasurementname", new String[]{},
            new String[]{"verylongfieldname", "1234567890"}, 0));

    new Verifications() {{
      httpClient.execute((HttpUriRequest) any);
    }};
  }

  @Test
  public void testPayload() throws IOException {
    List<HttpPost> requests = new ArrayList<>();
    new Expectations() {{
      httpClient.execute(withCapture(requests));
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 200;
    }};

    InfluxDBClient c = new InfluxDBClient(URI.create("http://localhost:8086/write?db=test"), 100);
    c.write("test", new String[]{"foo", "bar"}, new String[]{"val", "1"}, 123);
    c.flush();

    assertEquals("test,foo=bar val=1 123\n", EntityUtils.toString(requests.get(0).getEntity()));
  }

  @Test
  public void testPayloadAfterException() throws Exception {
    List<HttpPost> requests = new ArrayList<>();
    new Expectations() {{
      httpClient.execute(withCapture(requests));
      result = new IOException();
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 200;
    }};

    client.write("test_it", new String[]{"foo", "bar"}, new String[]{"val", "1"}, 123);
    assertThrows(IOException.class, client::flush);
    client.write("test_longer", new String[]{"foo", "bar"}, new String[]{"val", "1"}, 123);
    client.flush();

    assertEquals("test_longer,foo=bar val=1 123\n", EntityUtils.toString(requests.get(1).getEntity()));
  }
}
