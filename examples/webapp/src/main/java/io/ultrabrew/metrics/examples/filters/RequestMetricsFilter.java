package io.ultrabrew.metrics.examples.filters;

import io.ultrabrew.metrics.Timer;
import io.ultrabrew.metrics.examples.MyApp;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;

/**
 * A filter that gathers metrics about servlet requests.
 */
@WebFilter(urlPatterns = {"/*"})
public class RequestMetricsFilter implements Filter {

  // A timer metric to gather statistics about request durations, note that a separate counter is
  // not necessary as the timer also collects count
  private final Timer requestTimer = MyApp.metricRegistry.timer("MyApp.Servlet.requestDuration");

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    // Note when request handling starts
    long start = requestTimer.start();
    // Chain to next filter, will eventually run the actual servlet
    // Note: This is not sufficient to properly handle async requests
    chain.doFilter(request, response);
    // TODO: Add some useful tags
    // Update our timer metrics with the request duration
    requestTimer.stop(start);
  }

  @Override
  public void destroy() {
  }
}
