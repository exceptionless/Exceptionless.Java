package com.exceptionless.logging;

import static com.exceptionless.TestClients.data;
import static com.exceptionless.TestClients.error;
import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import com.exceptionless.TestClients;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LogEventSupportTest {
  private final CapturingSubmissionClient server = new CapturingSubmissionClient();
  private final List<String> reported = new CopyOnWriteArrayList<>();
  private final LogEventSupport support =
      new LogEventSupport("test", (message, error) -> reported.add(message));
  private ExceptionlessClient client;

  @AfterEach
  void tearDown() {
    support.close();
    if (client != null) {
      client.close();
    }
    Exceptionless.setDefault(null);
  }

  private Throwable log(String loggerName, LogLevel level, String message) {
    return support.submit(
        loggerName,
        "main",
        level,
        target -> support.createEvent(target, loggerName, message, level, null, "main"));
  }

  private List<Object> messages() {
    return server.flush(client).stream().map(e -> e.get("message")).toList();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "com.exceptionless, main, true",
        "com.exceptionless.queue.DefaultEventQueue, main, true",
        "com.exceptionlessfan.App, main, false",
        "com.example.App, exceptionless-1, true",
        "com.example.App, exceptionless, false",
        "null, null, false",
        "null, exceptionless-2, true"
      })
  void recognizesTheClientsOwnLoggersAndThreads(
      String loggerName, String threadName, boolean internal) {
    assertThat(LogEventSupport.isInternal(loggerName, threadName)).isEqualTo(internal);
  }

  @Test
  void sendsNothingUntilStartedOrAfterClose() {
    client = server.newClient();
    support.setClient(client);

    log("com.example.App", LogLevel.ERROR, "before start");
    support.start();
    log("com.example.App", LogLevel.ERROR, "started");
    support.close();
    log("com.example.App", LogLevel.ERROR, "after close");

    assertThat(messages()).containsExactly("started");
    assertThat(client.isStarted()).as("an explicit client is never closed").isTrue();
  }

  @Test
  void appliesMinimumLevelAndProjectSettings() {
    client = server.newClient();
    client.getConfiguration().applyServerSettings(Map.of("@@log:com.example.Chatty", "debug"));
    support.setClient(client);
    support.start();
    assertThat(support.getMinimumLevel()).isEqualTo(LogLevel.WARN);

    log("com.example.App", LogLevel.INFO, "info");
    log("com.example.App", LogLevel.WARN, "warn");
    log("com.example.Chatty", LogLevel.DEBUG, "chatty debug");
    assertThat(support.setMinimumLevel("information")).isTrue();
    assertThat(support.setMinimumLevel("bogus")).isFalse();
    log("com.example.App", LogLevel.INFO, "info after lowering");

    assertThat(support.getMinimumLevel()).isEqualTo(LogLevel.INFO);
    assertThat(messages()).containsExactly("warn", "chatty debug", "info after lowering");
  }

  @Test
  void createsLogAndErrorEvents() {
    client = server.newClient();
    support.setClient(client);
    support.start();

    support.submit(
        "com.example.Orders",
        "worker-1",
        LogLevel.WARN,
        target ->
            support.createEvent(
                target, "com.example.Orders", "Low stock", LogLevel.WARN, null, "worker-1"));
    support.submit(
        "com.example.Orders",
        "worker-1",
        LogLevel.ERROR,
        target ->
            support.createEvent(
                target,
                "com.example.Orders",
                null,
                LogLevel.ERROR,
                new IllegalStateException("declined"),
                "worker-1"));

    List<Map<String, Object>> events = server.flush(client);
    assertThat(events).hasSize(2);
    assertThat(events.get(0))
        .containsEntry("type", "log")
        .containsEntry("source", "com.example.Orders")
        .containsEntry("message", "Low stock");
    assertThat(data(events.get(0)))
        .containsEntry("@level", "warn")
        .containsEntry("@submission_method", "test")
        .containsEntry("thread", "worker-1");
    assertThat(events.get(1))
        .containsEntry("type", "error")
        .containsEntry("source", "com.example.Orders");
    assertThat(error(events.get(1))).containsEntry("message", "declined");
    assertThat(data(events.get(1)))
        .containsEntry("@level", "error")
        .containsEntry("@submission_method", "test");
  }

  @Test
  void dropsEventsLoggedWhileSubmitting() {
    client = server.newClient();
    support.setClient(client);
    support.start();
    AtomicInteger nested = new AtomicInteger();

    support.submit(
        "com.example.App",
        "main",
        LogLevel.ERROR,
        target -> {
          assertThat(log("com.example.App", LogLevel.ERROR, "nested")).isNull();
          nested.incrementAndGet();
          return support.createEvent(
              target, "com.example.App", "outer", LogLevel.ERROR, null, null);
        });

    assertThat(nested).hasValue(1);
    assertThat(messages()).containsExactly("outer");
  }

  @Test
  void usesTheDefaultClientOnlyOnceStarted() {
    support.start();
    log("com.example.App", LogLevel.ERROR, "no default client");
    assertThat(Exceptionless.hasDefault()).isFalse();

    client = new ExceptionlessClient(server.newConfiguration());
    Exceptionless.setDefault(client);
    log("com.example.App", LogLevel.ERROR, "not started");
    assertThat(client.isStarted()).isFalse();
    client.startup();
    log("com.example.App", LogLevel.ERROR, "started");

    assertThat(messages()).containsExactly("started");
    assertThat(support.getClient()).isNull();
  }

  @Test
  void anExplicitClientTakesPrecedenceOverTheApiKey() {
    client = server.newClient();
    support.setApiKey(TestClients.API_KEY);
    support.setClient(client);
    support.start();

    assertThat(support.resolveClient()).isSameAs(client);
    assertThat(support.getClient()).isSameAs(client);
  }

  @Test
  void ownsTheClientForItsApiKey() {
    support.setApiKey("  " + TestClients.API_KEY + "  ");
    support.setServerUrl("http://127.0.0.1:9/");
    assertThat(support.getApiKey()).isEqualTo(TestClients.API_KEY);
    assertThat(support.resolveClient()).as("not created before start").isNull();
    support.start();

    ExceptionlessClient owned = support.resolveClient();
    assertThat(owned).isNotNull().isSameAs(support.getClient());
    assertThat(owned.isStarted()).isTrue();
    ExceptionlessConfiguration config = owned.getConfiguration();
    assertThat(config.getApiKey()).isEqualTo(TestClients.API_KEY);
    assertThat(config.getServerUrl()).isEqualTo("http://127.0.0.1:9");
    assertThat(config.isCaptureUncaughtExceptions()).isFalse();

    support.setApiKey("AnotherApiKey1234567890");
    assertThat(owned.isStarted()).as("changing the key closes the old client").isFalse();
    ExceptionlessClient replaced = support.resolveClient();
    assertThat(replaced).isNotNull().isNotSameAs(owned);
    assertThat(replaced.getConfiguration().getApiKey()).isEqualTo("AnotherApiKey1234567890");

    support.close(Duration.ofMillis(100));
    assertThat(replaced.isStarted()).isFalse();
    assertThat(support.getClient()).isNull();
    assertThat(reported).isEmpty();
  }

  @Test
  void reportsAFailureToCreateTheClientOnceUntilRestarted() {
    support.setApiKey(TestClients.API_KEY);
    support.setServerUrl("ftp://not-http");
    support.start();

    assertThat(support.resolveClient()).isNull();
    assertThat(support.resolveClient()).isNull();
    assertThat(reported).containsExactly("Unable to create the Exceptionless client");

    support.start();
    assertThat(support.resolveClient()).isNull();
    assertThat(reported).hasSize(2);

    support.setServerUrl("http://127.0.0.1:9");
    assertThat(support.resolveClient()).isNotNull();
  }

  @Test
  void reportsTheFirstSubmitFailureAndReturnsEveryOne() {
    client = server.newClient();
    support.setClient(client);
    support.start();
    IllegalStateException boom = new IllegalStateException("boom");

    LogEventSupport.EventFactory failing =
        target -> {
          throw boom;
        };
    assertThat(support.submit("com.example.App", "main", LogLevel.ERROR, failing)).isSameAs(boom);
    assertThat(support.submit("com.example.App", "main", LogLevel.ERROR, failing)).isSameAs(boom);
    assertThat(reported).containsExactly("Unable to send a log event to Exceptionless");

    support.start();
    support.submit("com.example.App", "main", LogLevel.ERROR, failing);
    assertThat(reported).hasSize(2);
  }

  @Test
  void sendsNothingWhileTheClientIsDisabled() {
    client =
        new ExceptionlessClient(
                new ExceptionlessConfiguration()
                    .submissionClient(server)
                    .captureUncaughtExceptions(false)
                    .registerShutdownHook(false))
            .startup();
    support.setClient(client);
    support.start();
    AtomicInteger created = new AtomicInteger();

    support.submit(
        "com.example.App",
        "main",
        LogLevel.FATAL,
        target -> {
          created.incrementAndGet();
          return target.createLog("never");
        });

    assertThat(created).hasValue(0);
    assertThat(server.events()).isEmpty();
  }
}
