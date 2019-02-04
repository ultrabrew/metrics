package io.ultrabrew.metrics.examples.servlets;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A trivial servlet that simulates some slow processing by sleeping a random time between 1-3
 * seconds.
 */
@WebServlet(urlPatterns = "/slow")
@SuppressFBWarnings(value = "SECPR", justification = "Not production code")
public class SlowServlet extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 3000));
    } catch (InterruptedException e) {
      // Shouldn't happen, don't care if it does
    }
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain");
    resp.getWriter().println("Slow process done.");
  }
}
