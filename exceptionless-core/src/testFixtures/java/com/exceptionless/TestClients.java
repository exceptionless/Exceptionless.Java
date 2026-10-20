package com.exceptionless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.exceptionless.json.JsonParser;
import com.exceptionless.json.JsonWriter;
import com.exceptionless.models.UserDescription;
import com.exceptionless.submission.SettingsResponse;
import com.exceptionless.submission.SubmissionClient;
import com.exceptionless.submission.SubmissionResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Test helpers for building clients that never touch the network. Shared with the integration
 * modules through the {@code exceptionless-core} test jar.
 */
public final class TestClients {
  public static final String API_KEY = "LhhP1C9gijpSKCslHHCvwdSIz298twx271nTest";

  /** How long {@link CapturingSubmissionClient#awaitEvents(ExceptionlessClient, int)} waits. */
  public static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(10);

  private TestClients() {}

  /** A configuration with a valid API key and no global side effects. */
  public static ExceptionlessConfiguration config() {
    return new ExceptionlessConfiguration()
        .apiKey(API_KEY)
        .serverUrl("http://localhost:1")
        .captureUncaughtExceptions(false)
        .registerShutdownHook(false)
        .includeModules(false);
  }

  /** Records every request and answers with configurable responses. */
  public static final class CapturingSubmissionClient implements SubmissionClient {
    public final List<String> posts = new CopyOnWriteArrayList<>();
    public final List<String> heartbeats = new CopyOnWriteArrayList<>();
    public final List<String> userDescriptions = new CopyOnWriteArrayList<>();
    public final List<Integer> settingsRequests = new CopyOnWriteArrayList<>();
    public volatile Function<String, SubmissionResponse> eventsResponse =
        json -> new SubmissionResponse(202, null, null);
    public volatile SettingsResponse settingsResponse = new SettingsResponse.NotModified();

    private final Object postLock = new Object();

    /**
     * A {@link TestClients#config() test configuration} that sends to this client and doesn't fetch
     * settings when idle.
     */
    public ExceptionlessConfiguration newConfiguration() {
      return config().submissionClient(this).updateSettingsWhenIdleInterval(Duration.ZERO);
    }

    /** A started client that sends to this one. */
    public ExceptionlessClient newClient() {
      return new ExceptionlessClient(newConfiguration()).startup();
    }

    @Override
    public SubmissionResponse postEvents(String eventsJson) {
      posts.add(eventsJson);
      synchronized (postLock) {
        postLock.notifyAll();
      }
      return eventsResponse.apply(eventsJson);
    }

    @Override
    public SubmissionResponse postUserDescription(String referenceId, UserDescription description) {
      userDescriptions.add(referenceId + "=" + JsonWriter.toJson(description));
      return new SubmissionResponse(202, null, null);
    }

    @Override
    public SettingsResponse getSettings(int version) {
      settingsRequests.add(version);
      return settingsResponse;
    }

    @Override
    public SubmissionResponse sendHeartbeat(String sessionIdOrUserId, boolean closeSession) {
      heartbeats.add(sessionIdOrUserId + (closeSession ? ":close" : ""));
      return new SubmissionResponse(200, null, null);
    }

    /** All events posted so far, parsed. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> events() {
      List<Map<String, Object>> events = new ArrayList<>();
      for (String post : posts) {
        events.addAll((List<Map<String, Object>>) JsonParser.parse(post));
      }
      return events;
    }

    /** Sends the client's queued events and returns everything posted so far. */
    public List<Map<String, Object>> flush(ExceptionlessClient client) {
      if (!client.flush(AWAIT_TIMEOUT)) {
        throw new AssertionError("Queued events were not sent; posted so far: " + events());
      }
      return events();
    }

    /**
     * Waits until at least {@code count} events have been posted by whatever sends them (for
     * example a client being closed), without polling.
     */
    public List<Map<String, Object>> awaitEvents(int count, Duration timeout)
        throws InterruptedException {
      long deadline = System.nanoTime() + timeout.toNanos();
      synchronized (postLock) {
        while (events().size() < count) {
          long remaining = deadline - System.nanoTime();
          if (remaining <= 0) {
            throw new AssertionError(
                "Expected " + count + " events within " + timeout + "; got " + events());
          }
          TimeUnit.NANOSECONDS.timedWait(postLock, remaining);
        }
      }
      return events();
    }

    /**
     * Sends {@code client}'s queue until at least {@code count} events have been posted, for events
     * that are submitted asynchronously (for example after an HTTP response was sent).
     */
    public List<Map<String, Object>> awaitEvents(ExceptionlessClient client, int count) {
      await()
          .atMost(AWAIT_TIMEOUT)
          .pollInterval(Duration.ofMillis(25))
          .untilAsserted(
              () -> {
                client.flush(Duration.ofSeconds(2));
                assertThat(events()).hasSizeGreaterThanOrEqualTo(count);
              });
      return events();
    }
  }

  /** The event's {@code data}, or an empty map. */
  public static Map<String, Object> data(Map<String, Object> event) {
    return map(event.get("data"));
  }

  /** The event's {@code @error}, or an empty map. */
  public static Map<String, Object> error(Map<String, Object> event) {
    return map(data(event).get("@error"));
  }

  /** The event's {@code @request}, or an empty map. */
  public static Map<String, Object> request(Map<String, Object> event) {
    return map(data(event).get("@request"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  /** Reads a nested value with a dotted path, e.g. {@code data.@error.type}. */
  @SuppressWarnings("unchecked")
  public static Object path(Object root, String path) {
    Object current = root;
    for (String part : path.split("\\.(?![^\\[]*\\])")) {
      if (current instanceof Map<?, ?> map) {
        current = map.get(part);
      } else if (current instanceof List<?> list) {
        current = list.get(Integer.parseInt(part));
      } else {
        return null;
      }
    }
    return current;
  }
}
