package com.example.console;

import com.exceptionless.Exceptionless;
import com.exceptionless.LogLevel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Shows the main features of the Exceptionless Java client.
 *
 * <p>Run it with your API key (and server URL when self hosting):
 *
 * <pre>
 * EXCEPTIONLESS_API_KEY=your-key ./mvnw -pl samples/console -am -q exec:java
 * EXCEPTIONLESS_API_KEY=your-key EXCEPTIONLESS_SERVER_URL=http://localhost:5200 ./mvnw ...
 * </pre>
 */
public final class ConsoleApp {
  private static final Logger LOG = Logger.getLogger(ConsoleApp.class.getName());

  private ConsoleApp() {}

  public static void main(String[] args) throws Exception {
    // Route java.util.logging WARNING+ records to Exceptionless (see logging.properties).
    try (var config = ConsoleApp.class.getResourceAsStream("/logging.properties")) {
      LogManager.getLogManager().readConfiguration(config);
    }

    // Reads EXCEPTIONLESS_API_KEY / EXCEPTIONLESS_SERVER_URL (or exceptionless.properties).
    // Uncaught exceptions are reported and queued events are sent when the JVM exits.
    Exceptionless.startup(
        config ->
            config
                .version("1.0.0")
                .addDefaultTags("sample", "console")
                .addDefaultData("environment", "development")
                .setUserIdentity("sample-user", "Sample User")
                .addDataExclusions("*password*")
                .useSessions());

    if (!Exceptionless.getConfiguration().isEnabled()) {
      System.err.println("Set EXCEPTIONLESS_API_KEY to send events to Exceptionless.");
      return;
    }

    // Logs. The source is usually the class or logger name.
    Exceptionless.submitLog(ConsoleApp.class.getName(), "Console sample started", LogLevel.INFO);

    // Feature usage.
    Exceptionless.createFeatureUsage("Report Export")
        .setProperty("format", "csv")
        .setValue(42)
        .submit();

    // A handled exception with extra context.
    try {
      placeOrder(Map.of("id", "order-17", "total", 99.95, "password", "never sent"));
    } catch (IllegalStateException e) {
      Exceptionless.createException(e)
          .addTags("orders")
          .setProperty("order", Map.of("id", "order-17", "items", List.of("book", "pen")))
          .markAsCritical()
          .submit();
      String referenceId = Exceptionless.getLastReferenceId();
      System.out.println("Reported the error; tell support it was " + referenceId);
      Exceptionless.getDefault()
          .updateUserEmailAndDescription(referenceId, "user@example.com", "I clicked Order twice");
    }

    // JUL records reach Exceptionless through ExceptionlessHandler (see logging.properties).
    LOG.log(Level.WARNING, "Inventory is low for {0}", "pen");

    // 404s.
    Exceptionless.submitNotFound("/products/discontinued");

    // Send everything now (also happens automatically on exit).
    boolean sent = Exceptionless.flush(Duration.ofSeconds(10));
    System.out.println(sent ? "All events sent." : "Some events are still queued.");

    // Uncaught exceptions are reported automatically before the JVM exits.
    if (args.length > 0 && args[0].equals("crash")) {
      throw new IllegalStateException("The console sample crashed on purpose");
    }
  }

  private static void placeOrder(Map<String, Object> order) {
    try {
      chargeCard(order);
    } catch (RuntimeException e) {
      throw new IllegalStateException("Unable to place order " + order.get("id"), e);
    }
  }

  private static void chargeCard(Map<String, Object> order) {
    throw new UnsupportedOperationException("Card declined");
  }
}
