package com.exceptionless.e2e.support;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A server project with a client API key, plus helpers to create clients and read events back. */
public final class TestProject {
  private final ExceptionlessServer server;
  private final String projectId;
  private final String apiKey;

  TestProject(ExceptionlessServer server, String projectId, String apiKey) {
    this.server = server;
    this.projectId = projectId;
    this.apiKey = apiKey;
  }

  public String id() {
    return projectId;
  }

  public String apiKey() {
    return apiKey;
  }

  public ServerApi api() {
    return server.api();
  }

  public ExceptionlessServer server() {
    return server;
  }

  /** The configuration every test client starts from. */
  public ExceptionlessConfiguration configuration(String apiKey) {
    return new ExceptionlessConfiguration()
        .apiKey(apiKey)
        .serverUrl(server.baseUrl())
        .captureUncaughtExceptions(false)
        .registerShutdownHook(false)
        .shutdownTimeout(Duration.ofSeconds(10));
  }

  /** A started client for this project that records its HTTP calls. */
  public TestClient newClient(Consumer<ExceptionlessConfiguration> configure) {
    return newClient(apiKey, configure);
  }

  public TestClient newClient(String apiKey, Consumer<ExceptionlessConfiguration> configure) {
    ExceptionlessConfiguration config = configuration(apiKey);
    configure.accept(config);
    RecordingSubmissionClient recorder = new RecordingSubmissionClient(config);
    config.submissionClient(recorder);
    return new TestClient(new ExceptionlessClient(config).startup(), recorder);
  }

  /** Waits until {@code count} events match {@code filter} and returns them (newest first). */
  public List<Object> awaitEvents(String filter, int count) {
    return Await.value(
        count + " event(s) matching " + filter,
        Await.EVENTS,
        () -> api().getEvents(projectId, filter, Math.max(count, 10)),
        events -> events.size() >= count);
  }

  /** Waits for the single event matching {@code filter}. */
  public Map<String, Object> awaitEvent(String filter) {
    return Json.object(awaitEvents(filter, 1).get(0));
  }

  /** A started client and the recorder of its requests. */
  public record TestClient(ExceptionlessClient client, RecordingSubmissionClient recorder)
      implements AutoCloseable {
    /** Sends everything queued and asserts the queue drained. */
    public void flush() {
      if (!client.flush(Duration.ofSeconds(60))) {
        throw new AssertionError("Queue did not drain; requests so far: " + recorder.calls());
      }
    }

    /** Every event the client posted (successfully or not), parsed. */
    public List<Map<String, Object>> sentEvents() {
      return recorder.calls("events").stream()
          .flatMap(call -> Json.array(Json.parse(call.body())).stream())
          .map(Json::object)
          .toList();
    }

    /** The event the client posted with the given tag. */
    public Map<String, Object> sentEventWithTag(String tag) {
      return sentEvents().stream()
          .filter(e -> e.get("tags") instanceof List<?> tags && tags.contains(tag))
          .findFirst()
          .orElseThrow(
              () ->
                  new AssertionError(
                      "The client sent no event tagged " + tag + ": " + recorder.calls()));
    }

    /** Writes the raw request bodies the client sent to target/e2e/{name}.json for inspection. */
    public void savePayloads(String name) {
      Path dir = Path.of(System.getProperty("e2e.outputDirectory", "target/e2e"));
      try {
        Files.createDirectories(dir);
        StringBuilder sb = new StringBuilder();
        for (RecordingSubmissionClient.Call call : recorder.calls()) {
          sb.append("// ")
              .append(call.kind())
              .append(call.detail() == null ? "" : " " + call.detail())
              .append(" -> HTTP ")
              .append(call.status())
              .append(call.configVersion() == null ? "" : " config-version " + call.configVersion())
              .append('\n');
          if (call.body() != null) {
            sb.append(call.body()).append('\n');
          }
        }
        Files.writeString(dir.resolve(name + ".json"), sb.toString());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public void close() {
      client.close();
    }
  }
}
