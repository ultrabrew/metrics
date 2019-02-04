package io.ultrabrew.metrics.examples.listeners;

import io.ultrabrew.metrics.examples.MyApp;
import io.ultrabrew.metrics.reporters.SLF4JReporter;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * A context lifecycle listener, used to initialize and shutdown metrics reporting.
 */
@WebListener
public class MetricsInitializer implements ServletContextListener {

  private static final String REPORTER_ATTRIBUTE = "io.ultrabrew.metrics.examples.reporter";

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    // Create a reporter and add it to the registry.
    // For demo purposes we use SLF4JReporter which should not be used for production systems
    SLF4JReporter reporter = new SLF4JReporter("example", 10);
    MyApp.metricRegistry.addReporter(reporter);
    sce.getServletContext().setAttribute(REPORTER_ATTRIBUTE, reporter);
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    // Clean up the reporter here to avoid leaking resources
    SLF4JReporter reporter = (SLF4JReporter) sce.getServletContext()
        .getAttribute(REPORTER_ATTRIBUTE);
    reporter.close();
  }
}
