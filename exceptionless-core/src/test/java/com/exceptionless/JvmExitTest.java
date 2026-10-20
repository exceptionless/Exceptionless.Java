package com.exceptionless;

import static com.exceptionless.TestClients.path;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Proves the two lifecycle guarantees in a real JVM: the client never keeps the JVM alive, and
 * queued events are sent by the shutdown hook even when the app never closes the client.
 */
class JvmExitTest {
  private static Process launch(String... args) throws Exception {
    String java = ProcessHandle.current().info().command().orElse("java");
    String classpath =
        location(ExceptionlessClient.class) + File.pathSeparator + location(ExitProbe.class);
    List<String> command =
        new ArrayList<>(List.of(java, "-cp", classpath, ExitProbe.class.getName()));
    command.addAll(List.of(args));
    return new ProcessBuilder(command).redirectErrorStream(true).start();
  }

  private static String location(Class<?> type) throws Exception {
    return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  }

  @Test
  void jvmExitsAndShutdownHookSendsQueuedEvents() throws Exception {
    try (FakeServer server = new FakeServer()) {
      Process process = launch(server.url());
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("JVM exited").isTrue();
      String output = new String(process.getInputStream().readAllBytes());
      assertThat(process.exitValue()).as(output).isZero();
      List<Map<String, Object>> events =
          server.take("/api/v2/events", Duration.ofSeconds(5)).events();
      assertThat(events).extracting(e -> e.get("message")).containsExactly("before exit");
    }
  }

  @Test
  void uncaughtExceptionInMainIsReported() throws Exception {
    try (FakeServer server = new FakeServer()) {
      Process process = launch(server.url(), "crash");
      assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
      String output = new String(process.getInputStream().readAllBytes());
      assertThat(process.exitValue()).isEqualTo(1);
      assertThat(output)
          .contains("Exception in thread \"main\" java.lang.IllegalStateException: main crashed");
      List<Map<String, Object>> events =
          server.take("/api/v2/events", Duration.ofSeconds(5)).events();
      assertThat(events).extracting(e -> e.get("type")).containsExactly("log", "error");
      Map<String, Object> error = events.get(1);
      assertThat(path(error, "data.@error.message")).isEqualTo("main crashed");
      assertThat(path(error, "data.@submission_method")).isEqualTo("uncaughtExceptionHandler");
    }
  }
}
