package io.ultrabrew.metrics.examples.handlers;

import io.ultrabrew.metrics.Timer;
import io.ultrabrew.metrics.examples.ExampleServer;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;

/**
 * An example {@link HttpHandler} that collects request metrics.
 */
public class MetricsHandler implements HttpHandler {

  // The key under which we expect the final handler to have attached its name
  public static final AttachmentKey<String> REQUEST_HANDLER_KEY = AttachmentKey
      .create(String.class);

  // Tags used in emitted metrics
  private static final String REQUEST_METHOD = "method";
  private static final String REQUEST_HANDLER = "handler";
  private static final String STATUS_CODE = "status";

  // String used if handler name is missing
  private static final String NO_HANDLER = "DEFAULT";

  private final Timer requestTimer = ExampleServer.metricRegistry.timer("http.request");

  private final HttpHandler next;

  public MetricsHandler(HttpHandler next) {
    this.next = next;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {
    // Start counting request handling time
    long start = requestTimer.start();

    // Attach a listener to request completion event
    exchange.addExchangeCompleteListener((ex, nxt) -> {

      // Get the name of the final handler or our default string if it does not exist
      String handler = ex.getAttachment(REQUEST_HANDLER_KEY);
      if (handler == null) {
        handler = NO_HANDLER;
      }

      // Emit the duration of request processing, implicitly also produces request count
      requestTimer.stop(start,
          REQUEST_METHOD, ex.getRequestMethod().toString(),
          REQUEST_HANDLER, handler,
          STATUS_CODE, Integer.toString(ex.getStatusCode()));

      // Chain to next listener
      nxt.proceed();
    });

    // Let the next handler process the request
    next.handleRequest(exchange);
  }
}
