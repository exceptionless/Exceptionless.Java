package com.exceptionless.jul;

import static com.exceptionless.TestClients.data;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.FakeServer;
import com.exceptionless.LogLevel;
import com.exceptionless.TestClients;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExceptionlessHandlerTest {
  private final CapturingSubmissionClient server = new CapturingSubmissionClient();
  private final List<Logger> loggers = new ArrayList<>();
  private final List<Handler> handlers = new ArrayList<>();
  private ExceptionlessClient client;

  @AfterEach
  void tearDown() throws IOException {
    for (Logger logger : loggers) {
      for (Handler handler : logger.getHandlers()) {
        logger.removeHandler(handler);
      }
    }
    handlers.forEach(Handler::close);
    if (client != null) {
      client.close();
    }
    Exceptionless.setDefault(null);
  }

  private ExceptionlessHandler handler(ExceptionlessClient target) {
    ExceptionlessHandler handler = new ExceptionlessHandler(target);
    handlers.add(handler);
    return handler;
  }

  private Logger logger(String name, Handler handler) {
    Logger logger = Logger.getLogger(name);
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
    logger.addHandler(handler);
    loggers.add(logger);
    return logger;
  }

  /** Applies a logging.properties for the duration of {@code body}, then restores the defaults. */
  private static void withLogManagerProperties(String properties, ThrowingRunnable body)
      throws Exception {
    LogManager manager = LogManager.getLogManager();
    try (InputStream in =
        new ByteArrayInputStream(properties.getBytes(StandardCharsets.ISO_8859_1))) {
      manager.readConfiguration(in);
    }
    try {
      body.run();
    } finally {
      manager.readConfiguration();
    }
  }

  interface ThrowingRunnable {
    void run() throws Exception;
  }

  @ParameterizedTest
  @CsvSource({
    "ALL, TRACE",
    "FINEST, TRACE",
    "FINER, TRACE",
    "FINE, DEBUG",
    "CONFIG, DEBUG",
    "INFO, INFO",
    "WARNING, WARN",
    "SEVERE, ERROR",
    "OFF, ERROR"
  })
  void mapsLevels(String jul, LogLevel expected) {
    assertThat(ExceptionlessHandler.toLogLevel(Level.parse(jul))).isEqualTo(expected);
  }

  @Test
  void mapsAMissingLevelToInfo() {
    assertThat(ExceptionlessHandler.toLogLevel(null)).isEqualTo(LogLevel.INFO);
  }

  @Test
  void sendsWarningAndAboveByDefault() {
    client = server.newClient();
    Logger logger = logger("com.example.jul.Orders", handler(client));

    logger.finest("finest");
    logger.fine("fine");
    logger.config("config");
    logger.info("info");
    logger.log(Level.WARNING, "Low stock for {0}", "widget");
    logger.severe("Order failed");

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events)
        .extracting(e -> e.get("message"))
        .containsExactly("Low stock for widget", "Order failed");
    Map<String, Object> warn = events.get(0);
    assertThat(warn.get("type")).isEqualTo("log");
    assertThat(warn.get("source")).isEqualTo("com.example.jul.Orders");
    assertThat(data(warn))
        .containsEntry("@level", "warn")
        .containsEntry("@submission_method", "jul")
        .containsEntry("thread", Thread.currentThread().getName());
    assertThat(data(events.get(1))).containsEntry("@level", "error");
  }

  @Test
  void minimumLevelIsConfigurable() {
    client = server.newClient();
    ExceptionlessHandler handler = handler(client);
    handler.setMinimumLevel("information");
    assertThat(handler.getMinimumLevel()).isEqualTo(LogLevel.INFO);
    handler.setMinimumLevel("bogus");
    assertThat(handler.getMinimumLevel()).isEqualTo(LogLevel.INFO);
    Logger logger = logger("com.example.jul.Minimum", handler);

    logger.fine("fine");
    logger.info("info");

    assertThat(server.flush(client)).extracting(e -> e.get("message")).containsExactly("info");
  }

  @Test
  void projectLogSettingsOverrideTheMinimumLevel() {
    client = server.newClient();
    client
        .getConfiguration()
        .applyServerSettings(
            Map.of("@@log:com.example.*", "info", "@@log:com.example.jul.Noisy", "off"));
    ExceptionlessHandler handler = handler(client);
    Logger orders = logger("com.example.jul.Settings", handler);
    Logger noisy = logger("com.example.jul.Noisy", handler);
    Logger other = logger("org.other.jul.Thing", handler);

    orders.fine("orders fine");
    orders.info("orders info");
    noisy.severe("noisy severe");
    other.info("other info");
    other.warning("other warning");

    assertThat(server.flush(client))
        .extracting(e -> e.get("message"))
        .containsExactly("orders info", "other warning");
  }

  @Test
  void honorsHandlerLevelAndFilter() {
    client = server.newClient();
    ExceptionlessHandler handler = handler(client);
    handler.setLevel(Level.SEVERE);
    handler.setFilter(record -> !record.getMessage().contains("filtered"));
    Logger logger = logger("com.example.jul.Filtered", handler);

    logger.warning("below handler level");
    logger.severe("filtered out");
    logger.severe("sent");

    assertThat(server.flush(client)).extracting(e -> e.get("message")).containsExactly("sent");
  }

  @Test
  void sendsExceptionsAsErrors() {
    client = server.newClient();
    Logger logger = logger("com.example.jul.Payments", handler(client));

    logger.log(Level.WARNING, "Payment {0} failed", new Object[] {42});
    logger.log(Level.SEVERE, "Payment failed", new IllegalStateException("card declined"));

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events).hasSize(2);
    Map<String, Object> event = events.get(1);
    assertThat(event.get("type")).isEqualTo("error");
    assertThat(event.get("source")).isEqualTo("com.example.jul.Payments");
    assertThat(event.get("message")).isEqualTo("Payment failed");
    assertThat(data(event))
        .containsEntry("@level", "error")
        .containsEntry("@submission_method", "jul");
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) data(event).get("@error");
    assertThat(error.get("type")).isEqualTo("java.lang.IllegalStateException");
    assertThat(error.get("message")).isEqualTo("card declined");
  }

  @Test
  void handlesAnonymousLoggersAndNullMessages() {
    client = server.newClient();
    Logger anonymous = Logger.getAnonymousLogger();
    anonymous.setUseParentHandlers(false);
    anonymous.addHandler(handler(client));
    loggers.add(anonymous);

    anonymous.warning("anonymous");
    anonymous.log(Level.SEVERE, null, new IllegalStateException("no message"));

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events).hasSize(2);
    assertThat(events.get(0)).doesNotContainKey("source").containsEntry("message", "anonymous");
    assertThat(events.get(1).get("type")).isEqualTo("error");
  }

  @Test
  void usesTheConfiguredFormatterForMessages() {
    client = server.newClient();
    ExceptionlessHandler handler = handler(client);
    handler.setFormatter(
        new Formatter() {
          @Override
          public String format(LogRecord record) {
            return "unused";
          }

          @Override
          public String formatMessage(LogRecord record) {
            return "formatted: " + record.getMessage();
          }
        });
    logger("com.example.jul.Formatted", handler).warning("hello");

    assertThat(server.flush(client))
        .extracting(e -> e.get("message"))
        .containsExactly("formatted: hello");
  }

  @Test
  void ignoresTheClientsOwnLoggersAndThreads() throws Exception {
    client = server.newClient();
    ExceptionlessHandler handler = handler(client);
    logger("com.exceptionless.DefaultEventQueue", handler).severe("internal");
    logger("com.exceptionless", handler).severe("internal root");
    Logger app = logger("com.exceptionlessfan.App", handler);
    Thread thread = new Thread(() -> app.severe("from client thread"), "exceptionless-1");
    thread.start();
    thread.join();
    app.severe("app");

    assertThat(server.flush(client)).extracting(e -> e.get("message")).containsExactly("app");
  }

  @Test
  void doesNotRecurseWhenSubmittingLogs() {
    client = server.newClient();
    Logger logger = logger("com.example.jul.Recursion", handler(client));
    AtomicInteger handlerCalls = new AtomicInteger();
    client.addSubmittingEventHandler(
        e -> {
          handlerCalls.incrementAndGet();
          logger.severe("logged while submitting");
        });

    logger.severe("Order failed");

    assertThat(server.flush(client))
        .extracting(e -> e.get("message"))
        .containsExactly("Order failed");
    assertThat(handlerCalls).hasValue(1);
  }

  @Test
  void usesTheDefaultClientOnceStarted() {
    ExceptionlessHandler handler = new ExceptionlessHandler();
    handlers.add(handler);
    Logger logger = logger("com.example.jul.Default", handler);

    logger.severe("before any default client");
    assertThat(Exceptionless.hasDefault()).isFalse();

    client = new ExceptionlessClient(server.newConfiguration());
    Exceptionless.setDefault(client);
    logger.severe("before startup");
    assertThat(client.isStarted()).isFalse();

    client.startup();
    logger.severe("after startup");

    assertThat(server.flush(client))
        .extracting(e -> e.get("message"))
        .containsExactly("after startup");
    assertThat(handler.getClient()).isNull();
  }

  @Test
  void doesNothingWhenTheClientIsDisabled() {
    client =
        new ExceptionlessClient(
                new ExceptionlessConfiguration()
                    .submissionClient(server)
                    .captureUncaughtExceptions(false)
                    .registerShutdownHook(false))
            .startup();
    Logger logger = logger("com.example.jul.Disabled", handler(client));

    assertThatCode(
            () -> {
              logger.severe("one");
              logger.log(Level.SEVERE, "two", new RuntimeException("boom"));
            })
        .doesNotThrowAnyException();
    client.flush(Duration.ofSeconds(5));
    assertThat(server.events()).isEmpty();
  }

  @Test
  void neverThrowsForAClosedClient() {
    client = server.newClient();
    Logger logger = logger("com.example.jul.Closed", handler(client));
    client.close();

    assertThatCode(() -> logger.log(Level.SEVERE, "after close", new RuntimeException("boom")))
        .doesNotThrowAnyException();
    assertThat(server.events()).isEmpty();
  }

  @Test
  void reportsOnlyTheFirstFailure() {
    client = server.newClient();
    ExceptionlessHandler handler = handler(client);
    List<String> errors = new CopyOnWriteArrayList<>();
    handler.setErrorManager(
        new ErrorManager() {
          @Override
          public void error(String msg, Exception ex, int code) {
            errors.add(msg);
          }
        });
    LogRecord failing =
        new LogRecord(Level.SEVERE, "boom") {
          @Override
          public Throwable getThrown() {
            throw new IllegalStateException("cannot read the exception");
          }
        };
    failing.setLoggerName("com.example.jul.Failing");

    assertThatCode(
            () -> {
              handler.publish(failing);
              handler.publish(failing);
            })
        .doesNotThrowAnyException();
    assertThat(errors).containsExactly("Unable to send a log event to Exceptionless");
  }

  @Test
  void closeLeavesAnExplicitClientOpen() {
    client = server.newClient();
    ExceptionlessHandler handler = handler(client);
    Logger logger = logger("com.example.jul.Explicit", handler);

    handler.close();
    logger.severe("after handler close");

    assertThat(client.isStarted()).isTrue();
    assertThat(server.flush(client)).isEmpty();
  }

  @Test
  void ownsAndClosesTheClientForItsApiKey() throws Exception {
    try (FakeServer fake = new FakeServer()) {
      ExceptionlessHandler handler = new ExceptionlessHandler();
      handlers.add(handler);
      handler.setApiKey(TestClients.API_KEY);
      handler.setServerUrl(fake.url());
      assertThat(handler.getClient()).isNull();
      Logger logger = logger("com.example.jul.Owned", handler);

      logger.severe("Order failed");
      ExceptionlessClient owned = handler.getClient();
      assertThat(owned).isNotNull();
      assertThat(owned.isStarted()).isTrue();
      assertThat(owned.getConfiguration().getServerUrl()).isEqualTo(fake.url());
      assertThat(owned.getConfiguration().isCaptureUncaughtExceptions()).isFalse();

      handler.close();
      assertThat(owned.isStarted()).isFalse();
      assertThat(handler.getClient()).isNull();
      logger.severe("after close");

      List<Map<String, Object>> events =
          fake.take("/api/v2/events", Duration.ofSeconds(5)).events();
      assertThat(events).extracting(e -> e.get("message")).containsExactly("Order failed");
      assertThat(handler.getClient()).isNull();
    }
  }

  @Test
  void changingTheApiKeyReplacesTheOwnedClient() {
    ExceptionlessHandler handler = new ExceptionlessHandler();
    handlers.add(handler);
    handler.setApiKey(TestClients.API_KEY);
    handler.setServerUrl("http://127.0.0.1:9");
    Logger logger = logger("com.example.jul.Rekey", handler);
    logger.info("creates the client");
    ExceptionlessClient first = handler.getClient();
    assertThat(first).isNotNull();

    handler.setApiKey("AnotherApiKey1234567890");

    assertThat(first.isStarted()).isFalse();
    logger.info("creates another client");
    assertThat(handler.getClient()).isNotNull().isNotSameAs(first);
    assertThat(handler.getClient().getConfiguration().getApiKey())
        .isEqualTo("AnotherApiKey1234567890");
  }

  /** Rejects records whose message contains "skip"; created by name from logging.properties. */
  public static final class SkipFilter implements Filter {
    @Override
    public boolean isLoggable(LogRecord record) {
      return !record.getMessage().contains("skip");
    }
  }

  @Test
  void readsLogManagerProperties() throws Exception {
    String prefix = ExceptionlessHandler.class.getName();
    withLogManagerProperties(
        prefix
            + ".apiKey = "
            + TestClients.API_KEY
            + "\n"
            + prefix
            + ".serverUrl = http://127.0.0.1:9/\n"
            + prefix
            + ".minimumLevel = info\n"
            + prefix
            + ".level = FINE\n"
            + prefix
            + ".filter = "
            + SkipFilter.class.getName()
            + "\n"
            + prefix
            + ".formatter = java.util.logging.XMLFormatter\n",
        () -> {
          ExceptionlessHandler handler = new ExceptionlessHandler();
          handlers.add(handler);
          assertThat(handler.getApiKey()).isEqualTo(TestClients.API_KEY);
          assertThat(handler.getServerUrl()).isEqualTo("http://127.0.0.1:9/");
          assertThat(handler.getMinimumLevel()).isEqualTo(LogLevel.INFO);
          assertThat(handler.getLevel()).isEqualTo(Level.FINE);
          assertThat(handler.getFilter()).isInstanceOf(SkipFilter.class);
          assertThat(handler.getFormatter()).isInstanceOf(java.util.logging.XMLFormatter.class);
        });
  }

  @Test
  void defaultsWithoutLogManagerProperties() {
    ExceptionlessHandler handler = new ExceptionlessHandler();
    handlers.add(handler);
    assertThat(handler.getApiKey()).isNull();
    assertThat(handler.getServerUrl()).isNull();
    assertThat(handler.getMinimumLevel()).isEqualTo(LogLevel.WARN);
    assertThat(handler.getLevel()).isEqualTo(Level.ALL);
    assertThat(handler.getFilter()).isNull();
  }

  @Test
  void isConfiguredFromLoggingProperties() throws Exception {
    try (FakeServer fake = new FakeServer();
        InputStream template =
            getClass().getResourceAsStream("/exceptionless-logging-test.properties")) {
      String properties =
          new String(template.readAllBytes(), StandardCharsets.ISO_8859_1)
              .replace("@SERVER_URL@", fake.url());
      withLogManagerProperties(
          properties,
          () -> {
            Logger logger = Logger.getLogger("com.example.jul.Configured");
            assertThat(logger.getHandlers())
                .hasSize(1)
                .allMatch(ExceptionlessHandler.class::isInstance);
            ExceptionlessHandler handler = (ExceptionlessHandler) logger.getHandlers()[0];
            assertThat(handler.getMinimumLevel()).isEqualTo(LogLevel.INFO);

            logger.fine("not sent");
            logger.info("Configured from logging.properties");
            logger.info("skip me");

            // Resetting the LogManager closes the handler, which closes its client and sends the
            // queue.
            LogManager.getLogManager().reset();
            assertThat(handler.getClient()).isNull();

            List<Map<String, Object>> events =
                fake.take("/api/v2/events", Duration.ofSeconds(5)).events();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).get("message"))
                .isEqualTo("Configured from logging.properties");
            assertThat(data(events.get(0)))
                .containsEntry("@level", "info")
                .containsEntry("@submission_method", "jul");
          });
    }
  }
}
