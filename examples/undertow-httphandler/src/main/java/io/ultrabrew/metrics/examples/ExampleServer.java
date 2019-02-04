package io.ultrabrew.metrics.examples;

import io.ultrabrew.metrics.MetricRegistry;
import io.ultrabrew.metrics.Reporter;
import io.ultrabrew.metrics.examples.handlers.HelloWorldHandler;
import io.ultrabrew.metrics.examples.handlers.MetricsHandler;
import io.ultrabrew.metrics.reporters.SLF4JReporter;
import io.undertow.Undertow;
import io.undertow.predicate.Predicates;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.PredicateHandler;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.error.SimpleErrorPageHandler;

public class ExampleServer {

  public static final MetricRegistry metricRegistry = new MetricRegistry();

  public static void main(String... args) {
    // Create a reporter and add it to the registry.
    // For demo purposes we use SLF4JReporter which should not be used for production systems
    Reporter reporter = new SLF4JReporter("example", 10);
    metricRegistry.addReporter(reporter);

    // Create the handler chain
    // MetricsHandler -> PathHandler -> HelloWorldHandler / 404
    HttpHandler helloHandler = new HelloWorldHandler();
    PathHandler pathHandler = new PathHandler();
    pathHandler.addExactPath("/hello", helloHandler);
    HttpHandler metricsHandler = new MetricsHandler(pathHandler);

    // Build the server instance
    Undertow server = Undertow.builder()
        .addHttpListener(8080, "localhost")
        .setHandler(metricsHandler)
        .build();

    // And start it
    server.start();
  }
}
