package com.exceptionless.logback;

import static com.exceptionless.logback.CapturingSubmissionClient.data;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.MarkerFactory;

class ExceptionlessAppenderTest {
  private final CapturingSubmissionClient server = new CapturingSubmissionClient();
  private LoggerContext context;
  private ExceptionlessClient client;

  @BeforeEach
  void setUp() {
    context = new LoggerContext();
    context.setMDCAdapter(MDC.getMDCAdapter());
    context.start();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
    context.stop();
    if (client != null) {
      client.close();
    }
    Exceptionless.setDefault(null);
  }

  private ExceptionlessAppender appender(ExceptionlessClient target) {
    ExceptionlessAppender appender = new ExceptionlessAppender();
    appender.setContext(context);
    appender.setName("EXCEPTIONLESS");
    appender.setClient(target);
    appender.start();
    return appender;
  }

  private Logger logger(String name, ExceptionlessAppender appender) {
    Logger logger = context.getLogger(name);
    logger.setLevel(Level.TRACE);
    logger.setAdditive(false);
    logger.addAppender(appender);
    return logger;
  }

  @Test
  void mapsLevels() {
    assertThat(ExceptionlessAppender.toLogLevel(Level.TRACE)).isEqualTo(LogLevel.TRACE);
    assertThat(ExceptionlessAppender.toLogLevel(Level.DEBUG)).isEqualTo(LogLevel.DEBUG);
    assertThat(ExceptionlessAppender.toLogLevel(Level.INFO)).isEqualTo(LogLevel.INFO);
    assertThat(ExceptionlessAppender.toLogLevel(Level.WARN)).isEqualTo(LogLevel.WARN);
    assertThat(ExceptionlessAppender.toLogLevel(Level.ERROR)).isEqualTo(LogLevel.ERROR);
    assertThat(ExceptionlessAppender.toLogLevel(null)).isEqualTo(LogLevel.INFO);
  }

  @Test
  void sendsWarnAndAboveByDefault() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", appender(client));

    logger.trace("trace");
    logger.debug("debug");
    logger.info("info");
    logger.warn("Low stock for {}", "widget");
    logger.error("Order failed");

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events)
        .extracting(e -> e.get("message"))
        .containsExactly("Low stock for widget", "Order failed");
    Map<String, Object> warn = events.get(0);
    assertThat(warn.get("type")).isEqualTo("log");
    assertThat(warn.get("source")).isEqualTo("com.example.Orders");
    assertThat(data(warn))
        .containsEntry("@level", "warn")
        .containsEntry("@submission_method", "logback")
        .containsEntry("thread", Thread.currentThread().getName());
    assertThat(data(events.get(1))).containsEntry("@level", "error");
  }

  @Test
  void minimumLevelIsConfigurable() {
    client = server.newClient();
    ExceptionlessAppender appender = appender(client);
    appender.setMinimumLevel("info");
    assertThat(appender.getMinimumLevel()).isEqualTo("info");
    appender.setMinimumLevel("bogus");
    assertThat(appender.getMinimumLevel()).isEqualTo("info");
    Logger logger = logger("com.example.Orders", appender);

    logger.debug("debug");
    logger.info("info");

    assertThat(server.flush(client)).extracting(e -> e.get("message")).containsExactly("info");
  }

  @Test
  void projectLogSettingsOverrideTheMinimumLevel() {
    client = server.newClient();
    client
        .getConfiguration()
        .applyServerSettings(
            Map.of("@@log:com.example.*", "info", "@@log:com.example.Noisy", "off"));
    ExceptionlessAppender appender = appender(client);
    Logger orders = logger("com.example.Orders", appender);
    Logger noisy = logger("com.example.Noisy", appender);
    Logger other = logger("org.other.Thing", appender);

    orders.debug("orders debug");
    orders.info("orders info");
    noisy.error("noisy error");
    other.info("other info");
    other.warn("other warn");

    assertThat(server.flush(client))
        .extracting(e -> e.get("message"))
        .containsExactly("orders info", "other warn");
  }

  @Test
  void sendsExceptionsAsErrors() {
    client = server.newClient();
    Logger logger = logger("com.example.Payments", appender(client));

    logger.warn("Payment {} failed", 42, new IllegalStateException("card declined"));

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events).hasSize(1);
    Map<String, Object> event = events.get(0);
    assertThat(event.get("type")).isEqualTo("error");
    assertThat(event.get("source")).isEqualTo("com.example.Payments");
    assertThat(event.get("message")).isEqualTo("Payment 42 failed");
    assertThat(data(event))
        .containsEntry("@level", "warn")
        .containsEntry("@submission_method", "logback");
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) data(event).get("@error");
    assertThat(error.get("type")).isEqualTo("java.lang.IllegalStateException");
    assertThat(error.get("message")).isEqualTo("card declined");
  }

  @Test
  void addsMdcKeyValuePairsAndMarkers() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", appender(client));

    MDC.put("requestId", "r-1");
    logger
        .atError()
        .addMarker(MarkerFactory.getMarker("checkout"))
        .addMarker(MarkerFactory.getMarker("billing"))
        .addKeyValue("orderId", 42)
        .log("Order failed");

    Map<String, Object> event = server.flush(client).get(0);
    assertThat(data(event)).containsEntry("requestId", "r-1").containsEntry("orderId", 42L);
    @SuppressWarnings("unchecked")
    List<String> tags = (List<String>) event.get("tags");
    assertThat(tags).containsExactlyInAnyOrder("checkout", "billing");
  }

  @Test
  void canLeaveOutMdc() {
    client = server.newClient();
    ExceptionlessAppender appender = appender(client);
    appender.setIncludeMdc(false);
    Logger logger = logger("com.example.Orders", appender);

    MDC.put("requestId", "r-1");
    logger.atError().addKeyValue("orderId", 42).log("Order failed");

    Map<String, Object> event = server.flush(client).get(0);
    assertThat(data(event)).doesNotContainKey("requestId").containsEntry("orderId", 42L);
  }

  @Test
  void appliesDataExclusionsToMdc() {
    client =
        new ExceptionlessClient(server.newConfiguration().addDataExclusions("*password*"))
            .startup();
    Logger logger = logger("com.example.Login", appender(client));

    MDC.put("userPassword", "secret");
    MDC.put("user", "eric");
    logger.error("Login failed");

    assertThat(data(server.flush(client).get(0)))
        .doesNotContainKey("userPassword")
        .containsEntry("user", "eric");
  }

  @Test
  void ignoresTheClientsOwnLoggersAndThreads() throws Exception {
    client = server.newClient();
    ExceptionlessAppender appender = appender(client);
    logger("com.exceptionless.DefaultEventQueue", appender).error("internal");
    logger("com.exceptionless", appender).error("internal root");
    Logger app = logger("com.exceptionlessfan.App", appender);
    Thread thread = new Thread(() -> app.error("from client thread"), "exceptionless-1");
    thread.start();
    thread.join();
    app.error("app");

    assertThat(server.flush(client)).extracting(e -> e.get("message")).containsExactly("app");
  }

  @Test
  void doesNotRecurseWhenSubmittingLogs() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", appender(client));
    AtomicInteger handlerCalls = new AtomicInteger();
    client.addSubmittingEventHandler(
        e -> {
          handlerCalls.incrementAndGet();
          logger.error("logged while submitting");
        });

    logger.error("Order failed");

    assertThat(server.flush(client))
        .extracting(e -> e.get("message"))
        .containsExactly("Order failed");
    assertThat(handlerCalls).hasValue(1);
  }

  @Test
  void usesTheDefaultClientOnceStarted() {
    ExceptionlessAppender appender = new ExceptionlessAppender();
    appender.setContext(context);
    appender.start();
    Logger logger = logger("com.example.Orders", appender);

    logger.error("before any default client");
    assertThat(Exceptionless.hasDefault()).isFalse();

    client = new ExceptionlessClient(server.newConfiguration());
    Exceptionless.setDefault(client);
    logger.error("before startup");
    assertThat(client.isStarted()).isFalse();

    client.startup();
    logger.error("after startup");

    assertThat(server.flush(client))
        .extracting(e -> e.get("message"))
        .containsExactly("after startup");
    assertThat(appender.getClient()).isNull();
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
    Logger logger = logger("com.example.Orders", appender(client));

    assertThatCode(
            () -> {
              logger.error("one");
              logger.error("two", new RuntimeException("boom"));
            })
        .doesNotThrowAnyException();
    client.flush(Duration.ofSeconds(5));
    assertThat(server.events()).isEmpty();
  }

  @Test
  void neverThrowsForAClosedClient() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", appender(client));
    client.close();

    assertThatCode(() -> logger.error("after close", new RuntimeException("boom")))
        .doesNotThrowAnyException();
    assertThat(server.events()).isEmpty();
  }

  @Test
  void ownsAndClosesTheClientForItsApiKey() throws Exception {
    try (FakeServer fake = new FakeServer()) {
      ExceptionlessAppender appender = new ExceptionlessAppender();
      appender.setContext(context);
      appender.setApiKey(CapturingSubmissionClient.API_KEY);
      appender.setServerUrl(fake.url());
      appender.start();
      assertThat(appender.getClient()).isNull();
      Logger logger = logger("com.example.Orders", appender);

      logger.error("Order failed");
      ExceptionlessClient owned = appender.getClient();
      assertThat(owned).isNotNull();
      assertThat(owned.isStarted()).isTrue();
      assertThat(owned.getConfiguration().getServerUrl()).isEqualTo(fake.url());
      assertThat(owned.getConfiguration().isCaptureUncaughtExceptions()).isFalse();

      appender.stop();
      assertThat(owned.isStarted()).isFalse();
      assertThat(appender.getClient()).isNull();

      List<Map<String, Object>> events =
          fake.take("/api/v2/events", Duration.ofSeconds(5)).events();
      assertThat(events).extracting(e -> e.get("message")).containsExactly("Order failed");
    }
  }

  @Test
  void isConfiguredFromLogbackXml() throws Exception {
    try (FakeServer fake = new FakeServer()) {
      context.putProperty("exceptionlessServerUrl", fake.url());
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(context);
      configurator.doConfigure(getClass().getResource("/exceptionless-logback-test.xml"));

      ExceptionlessAppender appender =
          (ExceptionlessAppender)
              context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("EXCEPTIONLESS");
      assertThat(appender).isNotNull();
      assertThat(appender.isStarted()).isTrue();
      assertThat(appender.getApiKey()).isEqualTo(CapturingSubmissionClient.API_KEY);
      assertThat(appender.getServerUrl()).isEqualTo(fake.url());
      assertThat(appender.getMinimumLevel()).isEqualTo("info");
      assertThat(appender.isIncludeMdc()).isFalse();

      org.slf4j.Logger logger = context.getLogger("com.example.Xml");
      logger.debug("not sent");
      logger.info("Configured from XML");

      // Stopping the context stops the appender, which closes its client and sends the queue.
      context.stop();

      List<Map<String, Object>> events =
          fake.take("/api/v2/events", Duration.ofSeconds(5)).events();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).get("message")).isEqualTo("Configured from XML");
      assertThat(data(events.get(0)))
          .containsEntry("@level", "info")
          .containsEntry("@submission_method", "logback");
    }
  }
}
