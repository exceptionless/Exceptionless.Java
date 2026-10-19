package com.exceptionless;

import static com.exceptionless.TestClients.path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.exceptionless.TestClients.CapturingSubmissionClient;
import com.exceptionless.models.Event;
import com.exceptionless.plugins.EventPluginContext;
import com.exceptionless.submission.SettingsResponse;
import com.exceptionless.submission.SubmissionResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExceptionlessClientTest {
  private final CapturingSubmissionClient submission = new CapturingSubmissionClient();
  private final ExceptionlessConfiguration config =
      TestClients.config().submissionClient(submission);
  private final ExceptionlessClient client = new ExceptionlessClient(config);

  @AfterEach
  void close() {
    client.close();
  }

  private List<Map<String, Object>> sent() {
    assertThat(client.flush(Duration.ofSeconds(10))).isTrue();
    return submission.events();
  }

  @Test
  void startsOnFirstUse() {
    assertThat(client.isStarted()).isFalse();
    client.submitLog("hello");
    assertThat(client.isStarted()).isTrue();
    assertThat(sent())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.get("type")).isEqualTo("log");
              assertThat(e.get("message")).isEqualTo("hello");
              assertThat(e.get("date")).isNotNull();
            });
  }

  @Test
  void startupIsIdempotent() {
    assertThat(client.startup()).isSameAs(client);
    assertThat(client.startup()).isSameAs(client);
  }

  @Test
  void backgroundThreadIsADaemon() throws Exception {
    client.submitLog("x");
    client.flush(Duration.ofSeconds(5));
    List<Thread> threads =
        Thread.getAllStackTraces().keySet().stream()
            .filter(t -> t.getName().startsWith("exceptionless-"))
            .toList();
    assertThat(threads).isNotEmpty().allSatisfy(t -> assertThat(t.isDaemon()).isTrue());
  }

  @Test
  void builderSetsAllEventFields() {
    client
        .createFeatureUsage("Export")
        .setMessage("Exported report")
        .addTags("reports", "csv")
        .markAsCritical()
        .setGeo(43.5, -96.75)
        .setValue(12.5)
        .setReferenceId("report-12345")
        .setEventReference("parent", "parent-12345")
        .setUserIdentity("user@example.com", "Ann")
        .setUserDescription("ann@example.com", "It broke")
        .setManualStackingKey("exports", "Export failures")
        .setProperty("order", Map.of("id", 7, "items", List.of("a", "b")))
        .setProperty("password", "never-sent")
        .setVersion("1.0.0")
        .setLevel(LogLevel.INFO)
        .submit();
    config.addDataExclusions("password");
    Map<String, Object> event = sent().get(0);
    assertThat(event.get("type")).isEqualTo("usage");
    assertThat(event.get("source")).isEqualTo("Export");
    assertThat(event.get("tags")).isEqualTo(List.of("reports", "csv", "Critical"));
    assertThat(event.get("geo")).isEqualTo("43.5,-96.75");
    assertThat(event.get("value")).hasToString("12.5");
    assertThat(event.get("reference_id")).isEqualTo("report-12345");
    assertThat(path(event, "data.@ref:parent")).isEqualTo("parent-12345");
    assertThat(path(event, "data.@user"))
        .isEqualTo(Map.of("identity", "user@example.com", "name", "Ann"));
    assertThat(path(event, "data.@user_description"))
        .isEqualTo(Map.of("email_address", "ann@example.com", "description", "It broke"));
    assertThat(path(event, "data.@stack"))
        .isEqualTo(
            Map.of(
                "title",
                "Export failures",
                "signature_data",
                Map.of("ManualStackingKey", "exports")));
    assertThat(path(event, "data.order")).isEqualTo(Map.of("id", 7L, "items", List.of("a", "b")));
    assertThat(path(event, "data.@version")).isEqualTo("1.0.0");
    assertThat(path(event, "data.@level")).isEqualTo("info");
    assertThat(path(event, "data.@environment.o_s_name")).isNotNull();
  }

  @Test
  void dataExclusionsApplyToProperties() {
    config.addDataExclusions("*password*");
    client
        .createLog("x")
        .setProperty("userPassword", "a")
        .setProperty("user", Map.of("password", "b", "name", "c"))
        .submit();
    Map<String, Object> event = sent().get(0);
    assertThat(path(event, "data.userPassword")).isNull();
    assertThat(path(event, "data.user")).isEqualTo(Map.of("name", "c"));
  }

  @Test
  void builderValidatesInput() {
    EventBuilder builder = client.createLog("x");
    assertThatThrownBy(() -> builder.setReferenceId("bad id"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> builder.setGeo(91, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> builder.setGeo(0, 181)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> builder.setEventReference(" ", "valid-id-1"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void exceptionsBecomeErrorEventsWithReferenceIds() {
    client.submitException(new IllegalStateException("broken", new RuntimeException("cause")));
    Map<String, Object> event = sent().get(0);
    assertThat(event.get("type")).isEqualTo("error");
    assertThat(path(event, "data.@error.type")).isEqualTo("java.lang.IllegalStateException");
    assertThat(path(event, "data.@error.inner.message")).isEqualTo("cause");
    assertThat((String) event.get("reference_id")).hasSize(16);
    assertThat(client.getLastReferenceId()).isEqualTo(event.get("reference_id"));
  }

  @Test
  void javaLangErrorsCanBeReported() {
    client.submitUnhandledException(new OutOfMemoryError("Java heap space"), "test");
    Map<String, Object> event = sent().get(0);
    assertThat(path(event, "data.@error.type")).isEqualTo("java.lang.OutOfMemoryError");
    assertThat(path(event, "data.@submission_method")).isEqualTo("test");
  }

  @Test
  void logShortcuts() {
    client.submitLog("com.example.Source", "message", LogLevel.ERROR);
    client.submitNotFound("/missing");
    client.submitFeatureUsage("Search");
    List<Map<String, Object>> events = sent();
    assertThat(events).extracting(e -> e.get("type")).containsExactly("log", "404", "usage");
    assertThat(events.get(0).get("source")).isEqualTo("com.example.Source");
    assertThat(path(events.get(0), "data.@level")).isEqualTo("error");
    assertThat(events.get(1).get("source")).isEqualTo("/missing");
  }

  @Test
  void submittingHandlersCanModifyOrCancel() {
    client.addSubmittingEventHandler(e -> e.getEvent().addTags("handled"));
    client.addSubmittingEventHandler(
        e -> {
          if ("drop".equals(e.getEvent().getMessage())) {
            e.cancel();
          }
        });
    client.addSubmittingEventHandler(
        e -> {
          throw new IllegalStateException("handler errors are isolated");
        });
    AtomicInteger submitted = new AtomicInteger();
    client.addSubmittedEventHandler(e -> submitted.incrementAndGet());
    EventPluginContext dropped = client.createLog("drop").submit();
    client.submitLog("keep");
    assertThat(dropped.isCancelled()).isTrue();
    List<Map<String, Object>> events = sent();
    assertThat(events)
        .singleElement()
        .satisfies(e -> assertThat(e.get("tags")).isEqualTo(List.of("handled")));
    assertThat(submitted).hasValue(1);
  }

  @Test
  void pluginExceptionsDoNotLoseTheEvent() {
    config.addPlugin(
        "broken",
        15,
        ctx -> {
          throw new IllegalStateException("plugin bug");
        });
    client.submitLog("still sent");
    assertThat(sent()).hasSize(1);
  }

  @Test
  void reentrantSubmissionIsIgnored() {
    config.addPlugin("reentrant", 1, ctx -> ctx.getClient().submitLog("from plugin"));
    client.submitLog("outer");
    assertThat(sent()).extracting(e -> e.get("message")).containsExactly("outer");
  }

  @Test
  void disabledClientIsANoOp() {
    ExceptionlessClient disabled =
        new ExceptionlessClient(
            new ExceptionlessConfiguration()
                .captureUncaughtExceptions(false)
                .registerShutdownHook(false)
                .submissionClient(submission));
    try (disabled) {
      EventPluginContext context = disabled.createLog("x").submit();
      assertThat(context.isCancelled()).isTrue();
      assertThat(disabled.flush(Duration.ofSeconds(1))).isTrue();
      assertThat(submission.posts).isEmpty();
    }
  }

  @Test
  void closeSendsQueuedEventsAndStopsAcceptingEvents() {
    config.queueProcessInterval(Duration.ofHours(1));
    client.submitLog("queued");
    client.close();
    assertThat(submission.events()).hasSize(1);
    assertThat(client.isStarted()).isFalse();
    assertThat(client.createLog("after close").submit().isCancelled()).isTrue();
    client.close(); // idempotent
  }

  @Test
  void closeIsBoundedByShutdownTimeout() {
    CountDownLatch release = new CountDownLatch(1);
    submission.eventsResponse =
        json -> {
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return new SubmissionResponse(202, null, null);
        };
    config.shutdownTimeout(Duration.ofMillis(300)).queueProcessInterval(Duration.ofHours(1));
    client.submitLog("slow");
    long start = System.nanoTime();
    client.close();
    release.countDown();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
  }

  @Test
  void neverStartedClientClosesQuietly() {
    new ExceptionlessClient(TestClients.config()).close();
  }

  @Test
  void queueIsProcessedOnTheInterval() throws Exception {
    config.queueProcessInterval(Duration.ofMillis(100));
    client.submitLog("timer");
    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (submission.posts.isEmpty() && System.nanoTime() < deadline) {
      Thread.sleep(20);
    }
    assertThat(submission.posts).hasSize(1);
  }

  @Test
  void configVersionHeaderTriggersSettingsRefresh() {
    submission.eventsResponse = json -> new SubmissionResponse(202, null, 5);
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("@@log:*", "error"), 5);
    client.submitLog("x");
    client.flush(Duration.ofSeconds(5));
    assertThat(submission.settingsRequests).contains(0);
    assertThat(config.getSettings().getString("@@log:*")).isEqualTo("error");
    client.submitLog("com.x", "info message is now filtered", LogLevel.INFO);
    client.submitLog("com.x", "error message", LogLevel.ERROR);
    client.flush(Duration.ofSeconds(5));
    assertThat(submission.events())
        .extracting(e -> e.get("message"))
        .containsExactly("x", "error message");
  }

  @Test
  void updateSettingsOnDemand() throws Exception {
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("feature", "on"), 1);
    client.updateSettings().get(5, TimeUnit.SECONDS);
    assertThat(config.getSettings().getString("feature")).isEqualTo("on");
  }

  @Test
  void userDescriptions() throws Exception {
    SubmissionResponse response =
        client
            .updateUserEmailAndDescription("ref-123456", "a@b.c", "what happened")
            .get(5, TimeUnit.SECONDS);
    assertThat(response.isSuccess()).isTrue();
    assertThat(submission.userDescriptions)
        .containsExactly(
            "ref-123456={\"email_address\":\"a@b.c\",\"description\":\"what happened\"}");
    assertThatThrownBy(() -> client.updateUserEmailAndDescription("bad ref", null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sessionHeartbeatsAndEnd() throws Exception {
    assertThat(client.submitSessionHeartbeat(null).get(5, TimeUnit.SECONDS).statusCode())
        .isEqualTo(-1);
    config.userIdentity("user-1");
    client.submitSessionHeartbeat(null).get(5, TimeUnit.SECONDS);
    client.submitSessionEnd("explicit-id").get(5, TimeUnit.SECONDS);
    assertThat(submission.heartbeats).containsExactly("user-1", "explicit-id:close");
  }

  @Test
  void sessionsStartOnStartupAndEndOnClose() {
    config.userIdentity("user-1", "Ann").useSessions(false, Duration.ofMinutes(1), false);
    client.startup();
    client.close();
    assertThat(submission.events())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.get("type")).isEqualTo("session");
              assertThat(path(e, "data.@user.identity")).isEqualTo("user-1");
            });
    assertThat(submission.heartbeats).containsExactly("user-1:close");
  }

  @Test
  void sessionIdManagementWithoutUser() {
    config.useSessions(false, Duration.ofMinutes(1), true);
    client.startup();
    client.submitLog("in session");
    client.close();
    List<Map<String, Object>> events = submission.events();
    assertThat(events).hasSize(2);
    String sessionId = (String) events.get(0).get("reference_id");
    assertThat(events.get(0).get("type")).isEqualTo("session");
    assertThat(path(events.get(1), "data.@ref:session")).isEqualTo(sessionId);
    assertThat(submission.heartbeats).containsExactly(sessionId + ":close");
  }

  @Test
  void heartbeatsAreSentOnTheInterval() throws Exception {
    ExceptionlessConfiguration hbConfig =
        TestClients.config().submissionClient(submission).userIdentity("hb-user");
    hbConfig.useSessions(true, Duration.ofSeconds(30), false);
    // Use the plugin directly with a short interval through reflection-free access: replace with a
    // fast heartbeat.
    hbConfig.removePlugin("HeartbeatPlugin");
    hbConfig.addPlugin("FastHeartbeat", 100, ctx -> {});
    try (ExceptionlessClient hbClient = new ExceptionlessClient(hbConfig)) {
      hbClient.scheduleAtFixedRate(
          () -> hbClient.submitSessionHeartbeat(null),
          Duration.ofMillis(10),
          Duration.ofMillis(50));
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      while (submission.heartbeats.size() < 2 && System.nanoTime() < deadline) {
        Thread.sleep(20);
      }
    }
    assertThat(submission.heartbeats).contains("hb-user");
  }

  @Test
  void sessionEndStopsHeartbeats() throws Exception {
    config.userIdentity("user-2").useSessions(true, Duration.ofMinutes(1), false);
    client.submitLog("starts the heartbeat");
    com.exceptionless.plugins.HeartbeatPlugin heartbeat =
        (com.exceptionless.plugins.HeartbeatPlugin)
            config.getPlugins().stream()
                .filter(p -> p instanceof com.exceptionless.plugins.HeartbeatPlugin)
                .findFirst()
                .orElseThrow();
    assertThat(heartbeat.isRunning()).isTrue();
    client.submitSessionEnd(null).get(5, TimeUnit.SECONDS);
    assertThat(heartbeat.isRunning()).isFalse();
  }

  @Test
  void uncaughtExceptionHandlerReportsAndChains() throws Exception {
    Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
    AtomicInteger previousCalls = new AtomicInteger();
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> previousCalls.incrementAndGet());
    try {
      ExceptionlessConfiguration handlerConfig =
          TestClients.config().submissionClient(submission).captureUncaughtExceptions(true);
      try (ExceptionlessClient handlerClient = new ExceptionlessClient(handlerConfig).startup()) {
        Thread thread =
            new Thread(
                () -> {
                  throw new IllegalStateException("thread crashed");
                },
                "worker-1");
        thread.start();
        thread.join();
        handlerClient.flush(Duration.ofSeconds(5));
        assertThat(previousCalls).hasValue(1);
        assertThat(submission.events())
            .singleElement()
            .satisfies(
                e -> {
                  assertThat(path(e, "data.@error.message")).isEqualTo("thread crashed");
                  assertThat(path(e, "data.@submission_method"))
                      .isEqualTo("uncaughtExceptionHandler");
                  assertThat(path(e, "data.thread")).isEqualTo("worker-1");
                });
      }
      // Closing restores the previous handler.
      assertThat(Thread.getDefaultUncaughtExceptionHandler()).isNotNull();
      Thread.getDefaultUncaughtExceptionHandler()
          .uncaughtException(Thread.currentThread(), new RuntimeException());
      assertThat(previousCalls).hasValue(2);
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(original);
    }
  }

  @Test
  void fileStoragePersistsEventsAcrossRestarts(@TempDir Path dir) throws Exception {
    CapturingSubmissionClient failing = new CapturingSubmissionClient();
    failing.eventsResponse = json -> new SubmissionResponse(503, null, null);
    ExceptionlessConfiguration first =
        TestClients.config()
            .submissionClient(failing)
            .useFileStorage(dir)
            .shutdownTimeout(Duration.ofSeconds(2));
    try (ExceptionlessClient c = new ExceptionlessClient(first)) {
      c.submitLog("survives restart");
      c.flush(Duration.ofSeconds(5));
    }
    String installId;
    ExceptionlessConfiguration second =
        TestClients.config().submissionClient(submission).useFileStorage(dir);
    try (ExceptionlessClient c = new ExceptionlessClient(second)) {
      installId = c.getInstallId();
      assertThat(c.flush(Duration.ofSeconds(5))).isTrue();
    }
    assertThat(submission.events())
        .extracting(e -> e.get("message"))
        .containsExactly("survives restart");
    try (java.util.stream.Stream<Path> files = Files.walk(dir)) {
      assertThat(files.map(p -> p.getFileName().toString())).contains("install-id", "queue");
    }
    try (ExceptionlessClient c =
        new ExceptionlessClient(
            TestClients.config().submissionClient(submission).useFileStorage(dir))) {
      assertThat(c.getInstallId()).isEqualTo(installId);
    }
  }

  @Test
  void savedSettingsApplyBeforeTheFirstRequest(@TempDir Path dir) {
    submission.settingsResponse = new SettingsResponse.Updated(Map.of("@@log:*", "off"), 3);
    try (ExceptionlessClient c =
        new ExceptionlessClient(
            TestClients.config().submissionClient(submission).useFileStorage(dir))) {
      c.updateSettings().join();
    }
    ExceptionlessConfiguration restarted =
        TestClients.config().submissionClient(new CapturingSubmissionClient()).useFileStorage(dir);
    try (ExceptionlessClient c = new ExceptionlessClient(restarted).startup()) {
      assertThat(restarted.getSettings().getString("@@log:*")).isEqualTo("off");
      assertThat(c.createLog("src", "dropped", LogLevel.FATAL).submit().isCancelled()).isTrue();
    }
  }

  @Test
  void manualEventSubmission() {
    Event event = new Event();
    event.setType("custom");
    event.setMessage("hand built");
    EventPluginContext context = client.submitEvent(event, null);
    assertThat(context.isCancelled()).isFalse();
    assertThat(sent())
        .singleElement()
        .satisfies(e -> assertThat(e.get("type")).isEqualTo("custom"));
  }

  @Test
  void concurrentSubmissionsAreAllSent() throws Exception {
    config.submissionBatchSize(25);
    int threads = 8;
    int perThread = 100;
    Thread[] workers = new Thread[threads];
    for (int t = 0; t < threads; t++) {
      int id = t;
      workers[t] =
          new Thread(
              () -> {
                for (int i = 0; i < perThread; i++) {
                  client.submitLog("thread-" + id, "message " + i);
                }
              });
      workers[t].start();
    }
    for (Thread worker : workers) {
      worker.join();
    }
    assertThat(sent()).hasSize(threads * perThread);
  }
}
