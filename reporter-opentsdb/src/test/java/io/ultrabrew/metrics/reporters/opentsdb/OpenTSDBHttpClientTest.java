// Copyright 2018, Oath Inc.
// Licensed under the terms of the Apache License 2.0 license. See LICENSE file in Ultrabrew Metrics
// for terms.

package io.ultrabrew.metrics.reporters.opentsdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import mockit.Capturing;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.apache.http.util.EntityUtils;

public class OpenTSDBHttpClientTest {

  private static final URI DUMMY_DB_URI = URI.create("http://localhost:4242/api/put");

  @Mocked
  private CloseableHttpClient httpClient;

  @Mocked
  private StatusLine statusLine;

  @Mocked
  private CloseableHttpResponse closeableHttpResponse;

  @Test
  public void testWriteSuccessfulManualFlush() throws Exception {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 204;
    }};
    OpenTSDBHttpClient client = new OpenTSDBHttpClient(DUMMY_DB_URI, 64, true);
    String[] tags = {"host", "server01", "region", "us-west"};
    client.write("cpu_load_short.temp", tags, 1534055562000000003L, "80");
    client.write("cpu_load_short.fanSpeed", tags, 1534055562000000004L, "74.3");
    assertEquals(2, client.currentBatchSize);
    client.flush();
    assertEquals(0, client.currentBatchSize);
  }

  @Test
  public void testWriteSuccessfulAutoFlush() throws Exception {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 204;
    }};
    OpenTSDBHttpClient client = new OpenTSDBHttpClient(DUMMY_DB_URI, 1, true);
    String[] tags = {"host", "server01", "region", "us-west"};
    client.write("cpu_load_short.temp", tags, 1534055562000000003L, "80");
    assertEquals(0, client.currentBatchSize);
    client.write("cpu_load_short.fanSpeed", tags, 1534055562000000004L, "74.3");
    assertEquals(0, client.currentBatchSize);
  }

  @Test
  public void testWriteFailsManualFlush() throws Exception {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 500;
    }};
    OpenTSDBHttpClient client = new OpenTSDBHttpClient(DUMMY_DB_URI, 64, true);
    String[] tags = {"host", "server01", "region", "us-west"};
    client.write("cpu_load_short.temp", tags, 1534055562000000003L, "80");
    client.write("cpu_load_short.fanSpeed", tags, 1534055562000000004L, "74.3");
    assertEquals(2, client.currentBatchSize);
    assertThrows(IllegalStateException.class, client::flush);
    assertEquals(0, client.currentBatchSize);
  }

  @Test
  public void testNullStringsAndTagsValidation() throws Exception {
    new Expectations() {{
      httpClient.execute((HttpUriRequest) any);
      result = closeableHttpResponse;
      closeableHttpResponse.getStatusLine();
      result = statusLine;
      statusLine.getStatusCode();
      result = 200;
    }};
    OpenTSDBHttpClient client = new OpenTSDBHttpClient(DUMMY_DB_URI, 64, true);
    client.write(null, new String[] { "host", "web01" }, 1534055562000000003L, "80");
    client.write("cpu_load_short.temp", new String[] { null, "web01" }, 1534055562000000003L, "80");
    client.write("cpu_load_short.temp", new String[] { "host", null }, 1534055562000000003L, "80");
    client.write("cpu_load_short.temp", new String[] { "host", "web01" }, 1534055562000000003L, null);
    client.write("cpu_load_short.temp", null, 1534055562000000003L, "80");
    client.write("cpu_load_short.temp", new String[] { "host" }, 1534055562000000003L, "80");
    client.flush();
    new Verifications() {{
      HttpPost request;
      httpClient.execute(request = withCapture());
      times = 1;
      assertEquals("[{\"metric\":\"cpu_load_short.temp\",\"timestamp\":1534055562000000003,\"tags\":{\"host\":\"NULL\"},\"value\":80}]", 
                     EntityUtils.toString(request.getEntity()));
    }};
  }
  
  @Test
  public void testEscapeString() throws Exception {
    OpenTSDBHttpClient c = new OpenTSDBHttpClient(DUMMY_DB_URI, 64, false);

    c.write("Sîne klâwen durh die wolken \"sint\" geslagen",
        new String[]{"host", "web\\01", "colo", "phx"}, 1000000, "42.5");
    c.write("m1", new String[]{"host", "web\\01", "colo", "lga"}, 2000000, "24.6");
    c.writer.flush();
    assertEquals("[{\"metric\":\"Sîne klâwen durh die wolken \\\"sint\\\" geslagen\","
            + "\"timestamp\":1000,\"tags\":{\"host\":\"web\\\\01\",\"colo\":\"phx\"},"
            + "\"value\":42.5},{\"metric\":\"m1\",\"timestamp\":2000,\"tags\":"
            + "{\"host\":\"web\\\\01\",\"colo\":\"lga\"},\"value\":24.6}",
        new String(c.buffer.toByteArray(), StandardCharsets.UTF_8));
  }

  @Test
  public void testEscapeStringMillis() throws Exception {
    OpenTSDBHttpClient c = new OpenTSDBHttpClient(DUMMY_DB_URI, 64, true);

    c.write("Sîne klâwen durh die wolken \"sint\" geslagen",
        new String[]{"host", "web\\01", "colo", "phx"}, 1000000, "42.5");
    c.write("m1", new String[]{"host", "web\\01", "colo", "lga"}, 2000000, "24.6");
    c.writer.flush();
    assertEquals("[{\"metric\":\"Sîne klâwen durh die wolken \\\"sint\\\" geslagen\","
            + "\"timestamp\":1000000,\"tags\":{\"host\":\"web\\\\01\",\"colo\":\"phx\"},"
            + "\"value\":42.5},{\"metric\":\"m1\",\"timestamp\":2000000,\"tags\":"
            + "{\"host\":\"web\\\\01\",\"colo\":\"lga\"},\"value\":24.6}",
        new String(c.buffer.toByteArray(), StandardCharsets.UTF_8));
  }

  @Test
  public void testEscapeStringControlChars() {
    OpenTSDBHttpClient c = new OpenTSDBHttpClient(DUMMY_DB_URI, 64, true);

    assertThrows(IllegalArgumentException.class, () -> c
        .write("test\nmetric", new String[]{"host", "web\\01", "colo", "phx"}, 1000000, "42.5"));
  }
}
