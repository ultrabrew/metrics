package io.ultrabrew.metrics.examples.handlers;

import io.ultrabrew.metrics.Counter;
import io.ultrabrew.metrics.examples.ExampleServer;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

/**
 * Simple handler that emits some metrics and sends a plain text response to client.
 */
public class HelloWorldHandler implements HttpHandler {

  private final Counter helloCount = ExampleServer.metricRegistry.counter("hello");

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    // Store the name of the handler to the HttpServerExchange so MetricsHandler can use it later
    exchange.putAttachment(MetricsHandler.REQUEST_HANDLER_KEY, getClass().getSimpleName());

    // Emit some metrics specific to this handler
    helloCount.inc();

    // Send response to client
    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
    exchange.getResponseSender().send("Hello World!\n");
  }
}
