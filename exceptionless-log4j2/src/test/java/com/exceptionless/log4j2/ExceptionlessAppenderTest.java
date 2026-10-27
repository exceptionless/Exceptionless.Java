package com.exceptionless.log4j2;

import static com.exceptionless.TestClients.data;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.FakeServer;
import com.exceptionless.LogLevel;
import com.exceptionless.TestClients;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.StringMapMessage;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExceptionlessAppenderTest {
  private final CapturingSubmissionClient server = new CapturingSubmissionClient();
  private LoggerContext context;
  private ExceptionlessClient client;

  @AfterEach
  void tearDown() {
    ThreadContext.clearAll();
    if (context != null) {
      context.stop();
    }
    if (client != null) {
      client.close();
    }
    Exceptionless.setDefault(null);
  }

  /** A logger context whose root logger (level ALL) only appends to {@code appender}. */
  private LoggerContext contextWith(Appender appender) {
    context = new LoggerContext("exceptionless-test-" + System.nanoTime());
    context.start();
    Configuration config = context.getConfiguration();
    appender.start();
    config.addAppender(appender);
    LoggerConfig root = config.getRootLogger();
    for (String name : root.getAppenders().keySet()) {
      root.removeAppender(name);
    }
    root.setLevel(Level.ALL);
    root.addAppender(appender, null, null);
    context.updateLoggers();
    return context;
  }

  private static ExceptionlessAppender.Builder<?> builder(ExceptionlessClient target) {
    return ExceptionlessAppender.newBuilder().setName("Exceptionless").setClient(target);
  }

  private Logger logger(String name, ExceptionlessAppender appender) {
    return contextWith(appender).getLogger(name);
  }

  @ParameterizedTest
  @CsvSource({
    "ALL, TRACE",
    "TRACE, TRACE",
    "DEBUG, DEBUG",
    "INFO, INFO",
    "WARN, WARN",
    "ERROR, ERROR",
    "FATAL, FATAL",
    "OFF, FATAL"
  })
  void mapsLevels(String log4j, LogLevel expected) {
    assertThat(ExceptionlessAppender.toLogLevel(Level.getLevel(log4j))).isEqualTo(expected);
  }

  @Test
  void mapsCustomLevelsByIntensityAndAMissingLevelToInfo() {
    assertThat(ExceptionlessAppender.toLogLevel(Level.forName("NOTICE", 350)))
        .isEqualTo(LogLevel.INFO);
    assertThat(ExceptionlessAppender.toLogLevel(null)).isEqualTo(LogLevel.INFO);
  }

  @Test
  void sendsWarnAndAboveByDefault() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", builder(client).build());

    logger.trace("trace");
    logger.debug("debug");
    logger.info("info");
    logger.warn("Low stock for {}", "widget");
    logger.error("Order failed");
    logger.fatal("Out of memory");

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events)
        .extracting(e -> e.get("message"))
        .containsExactly("Low stock for widget", "Order failed", "Out of memory");
    Map<String, Object> warn = events.get(0);
    assertThat(warn.get("type")).isEqualTo("log");
    assertThat(warn.get("source")).isEqualTo("com.example.Orders");
    assertThat(data(warn))
        .containsEntry("@level", "warn")
        .containsEntry("@submission_method", "log4j2")
        .containsEntry("thread", Thread.currentThread().getName());
    assertThat(data(events.get(1))).containsEntry("@level", "error");
    assertThat(data(events.get(2))).containsEntry("@level", "fatal");
  }

  @Test
  void minimumLevelIsConfigurable() {
    client = server.newClient();
    assertThat(builder(client).setMinimumLevel("bogus").build().getMinimumLevel())
        .isEqualTo(LogLevel.WARN);
    ExceptionlessAppender appender = builder(client).setMinimumLevel("info").build();
    assertThat(appender.getMinimumLevel()).isEqualTo(LogLevel.INFO);
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
    LoggerContext ctx = contextWith(builder(client).build());

    ctx.getLogger("com.example.Orders").debug("orders debug");
    ctx.getLogger("com.example.Orders").info("orders info");
    ctx.getLogger("com.example.Noisy").error("noisy error");
    ctx.getLogger("org.other.Thing").info("other info");
    ctx.getLogger("org.other.Thing").warn("other warn");

    assertThat(server.flush(client))
        .extracting(e -> e.get("message"))
        .containsExactly("orders info", "other warn");
  }

  @Test
  void sendsExceptionsAsErrors() {
    client = server.newClient();
    Logger logger = logger("com.example.Payments", builder(client).build());

    logger.warn("Payment {} failed", 42, new IllegalStateException("card declined"));

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events).hasSize(1);
    Map<String, Object> event = events.get(0);
    assertThat(event.get("type")).isEqualTo("error");
    assertThat(event.get("source")).isEqualTo("com.example.Payments");
    assertThat(event.get("message")).isEqualTo("Payment 42 failed");
    assertThat(data(event))
        .containsEntry("@level", "warn")
        .containsEntry("@submission_method", "log4j2");
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) data(event).get("@error");
    assertThat(error.get("type")).isEqualTo("java.lang.IllegalStateException");
    assertThat(error.get("message")).isEqualTo("card declined");
  }

  @Test
  void addsThreadContextAndMarker() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", builder(client).build());

    ThreadContext.put("requestId", "r-1");
    logger.error(MarkerManager.getMarker("checkout"), "Order failed");

    Map<String, Object> event = server.flush(client).get(0);
    assertThat(data(event)).containsEntry("requestId", "r-1");
    assertThat(event.get("tags")).isEqualTo(List.of("checkout"));
  }

  @Test
  void addsMapMessageFields() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", builder(client).build());

    logger.error(new StringMapMessage().with("orderId", "42").with("status", "declined"));
    StructuredDataMessage audit = new StructuredDataMessage("audit@1", "Refund issued", "Audit");
    audit.put("amount", "9.95");
    logger.warn(audit);

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events).hasSize(2);
    assertThat(data(events.get(0)))
        .containsEntry("orderId", "42")
        .containsEntry("status", "declined");
    assertThat(events.get(1)).containsEntry("message", "Refund issued");
    assertThat(data(events.get(1))).containsEntry("amount", "9.95");
  }

  @Test
  void canLeaveOutThreadContext() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", builder(client).setIncludeMdc(false).build());

    ThreadContext.put("requestId", "r-1");
    logger.error("Order failed");

    assertThat(data(server.flush(client).get(0)))
        .doesNotContainKey("requestId")
        .containsKey("thread");
  }

  @Test
  void ignoresTheClientsOwnLoggersAndThreads() throws Exception {
    client = server.newClient();
    LoggerContext ctx = contextWith(builder(client).build());
    ctx.getLogger("com.exceptionless.DefaultEventQueue").error("internal");
    ctx.getLogger("com.exceptionless").error("internal root");
    Logger app = ctx.getLogger("com.exceptionlessfan.App");
    Thread thread = new Thread(() -> app.error("from client thread"), "exceptionless-1");
    thread.start();
    thread.join();
    app.error("app");

    assertThat(server.flush(client)).extracting(e -> e.get("message")).containsExactly("app");
  }

  @Test
  void doesNotRecurseWhenSubmittingLogs() {
    client = server.newClient();
    Logger logger = logger("com.example.Orders", builder(client).build());
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
    ExceptionlessAppender appender =
        ExceptionlessAppender.newBuilder().setName("Exceptionless").build();
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
    Logger logger = logger("com.example.Orders", builder(client).build());

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
    Logger logger =
        logger("com.example.Orders", builder(client).setIgnoreExceptions(false).build());
    client.close();

    assertThatCode(() -> logger.error("after close", new RuntimeException("boom")))
        .doesNotThrowAnyException();
    assertThat(server.events()).isEmpty();
  }

  @Test
  void reportsTheFirstFailureAndIgnoresItByDefault() {
    client = server.newClient();
    ExceptionlessAppender appender = builder(client).build();
    AtomicInteger reported = new AtomicInteger();
    appender.setHandler(
        new org.apache.logging.log4j.core.ErrorHandler() {
          @Override
          public void error(String msg) {
            reported.incrementAndGet();
          }

          @Override
          public void error(String msg, Throwable t) {
            reported.incrementAndGet();
          }

          @Override
          public void error(String msg, LogEvent event, Throwable t) {
            reported.incrementAndGet();
          }
        });
    contextWith(appender);

    assertThatCode(
            () -> {
              appender.append(failingEvent());
              appender.append(failingEvent());
            })
        .doesNotThrowAnyException();
    assertThat(reported).hasValue(1);
  }

  @Test
  void throwsFailuresWhenNotIgnoringExceptions() {
    client = server.newClient();
    ExceptionlessAppender appender = builder(client).setIgnoreExceptions(false).build();
    contextWith(appender);

    assertThatThrownBy(() -> appender.append(failingEvent()))
        .isInstanceOf(AppenderLoggingException.class)
        .hasRootCauseMessage("cannot format");
    assertThatThrownBy(() -> appender.append(failingEvent()))
        .isInstanceOf(AppenderLoggingException.class);
  }

  /** An event whose message can't be formatted. */
  private static LogEvent failingEvent() {
    Message message =
        new Message() {
          @Override
          public String getFormattedMessage() {
            throw new IllegalStateException("cannot format");
          }

          @Override
          public Object[] getParameters() {
            return null;
          }

          @Override
          public Throwable getThrowable() {
            return null;
          }
        };
    return Log4jLogEvent.newBuilder()
        .setLoggerName("com.example.Orders")
        .setLevel(Level.ERROR)
        .setMessage(message)
        .build();
  }

  @Test
  void stopWaitsAtMostTheGivenTimeoutForQueuedEvents() throws Exception {
    CountDownLatch slowServer = new CountDownLatch(1);
    try (FakeServer fake = new FakeServer()) {
      fake.onEvents(
          request -> {
            try {
              slowServer.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return FakeServer.Response.of(202);
          });
      ExceptionlessAppender appender =
          ExceptionlessAppender.newBuilder()
              .setName("Exceptionless")
              .setApiKey(TestClients.API_KEY)
              .setServerUrl(fake.url())
              .build();
      logger("com.example.Orders", appender).error("Order failed");
      ExceptionlessClient owned = appender.getClient();
      assertThat(owned).isNotNull();

      long start = System.nanoTime();
      appender.stop(200, TimeUnit.MILLISECONDS);
      assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(3_000);
      assertThat(owned.isStarted()).isFalse();
      assertThat(owned.getConfiguration().getShutdownTimeout()).hasMillis(200);
      // Let the server answer, so closing it doesn't wait for the handler.
      slowServer.countDown();
    }
  }

  @Test
  void ownsAndClosesTheClientForItsApiKey() throws Exception {
    try (FakeServer fake = new FakeServer()) {
      ExceptionlessAppender appender =
          ExceptionlessAppender.newBuilder()
              .setName("Exceptionless")
              .setApiKey(TestClients.API_KEY)
              .setServerUrl(fake.url())
              .build();
      Logger logger = logger("com.example.Orders", appender);
      assertThat(appender.getClient()).isNull();

      logger.error("Order failed");
      ExceptionlessClient owned = appender.getClient();
      assertThat(owned).isNotNull();
      assertThat(owned.isStarted()).isTrue();
      assertThat(owned.getConfiguration().getServerUrl()).isEqualTo(fake.url());
      assertThat(owned.getConfiguration().isCaptureUncaughtExceptions()).isFalse();

      appender.stop();
      assertThat(appender.isStopped()).isTrue();
      assertThat(owned.isStarted()).isFalse();
      assertThat(appender.getClient()).isNull();

      List<Map<String, Object>> events =
          fake.take("/api/v2/events", Duration.ofSeconds(5)).events();
      assertThat(events).extracting(e -> e.get("message")).containsExactly("Order failed");
    }
  }

  @Test
  void isDiscoveredByNameFromLog4j2Xml() throws Exception {
    try (FakeServer fake = new FakeServer()) {
      System.setProperty("exceptionless.test.serverUrl", fake.url());
      try {
        context =
            Configurator.initialize(
                "exceptionless-xml-test",
                getClass().getClassLoader(),
                "exceptionless-log4j2-test.xml");
      } finally {
        System.clearProperty("exceptionless.test.serverUrl");
      }

      Appender configured = context.getConfiguration().getAppender("Exceptionless");
      assertThat(configured).isInstanceOf(ExceptionlessAppender.class);
      ExceptionlessAppender appender = (ExceptionlessAppender) configured;
      assertThat(appender.isStarted()).isTrue();
      assertThat(appender.getApiKey()).isEqualTo(TestClients.API_KEY);
      assertThat(appender.getServerUrl()).isEqualTo(fake.url());
      assertThat(appender.getMinimumLevel()).isEqualTo(LogLevel.INFO);
      assertThat(appender.isIncludeMdc()).isFalse();

      Logger logger = context.getLogger("com.example.Xml");
      logger.debug("not sent");
      logger.info("Configured from XML");

      // Stopping the context stops the appender, which closes its client and sends the queue.
      Configurator.shutdown(context);
      context = null;

      List<Map<String, Object>> events =
          fake.take("/api/v2/events", Duration.ofSeconds(5)).events();
      assertThat(events).hasSize(1);
      assertThat(events.get(0).get("message")).isEqualTo("Configured from XML");
      assertThat(data(events.get(0)))
          .containsEntry("@level", "info")
          .containsEntry("@submission_method", "log4j2");
    }
  }
}
