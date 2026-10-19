package com.exceptionless.spring.boot;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.json.JsonParser;
import com.exceptionless.models.UserDescription;
import com.exceptionless.submission.SettingsResponse;
import com.exceptionless.submission.SubmissionClient;
import com.exceptionless.submission.SubmissionResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Captures the events a client posts, for tests. */
final class RecordingSubmissionClient implements SubmissionClient {
  static final String API_KEY = "LhhP1C9gijpSKCslHHCvwdSIz298twx271nTest";

  private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();

  static ExceptionlessConfiguration configuration(RecordingSubmissionClient submissionClient) {
    return new ExceptionlessConfiguration()
        .apiKey(API_KEY)
        .submissionClient(submissionClient)
        .captureUncaughtExceptions(false)
        .registerShutdownHook(false)
        .updateSettingsWhenIdleInterval(Duration.ZERO);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SubmissionResponse postEvents(String eventsJson) {
    events.addAll((List<Map<String, Object>>) JsonParser.parse(eventsJson));
    return new SubmissionResponse(202, null, null);
  }

  @Override
  public SubmissionResponse postUserDescription(String referenceId, UserDescription description) {
    return new SubmissionResponse(202, null, null);
  }

  @Override
  public SettingsResponse getSettings(int version) {
    return new SettingsResponse.NotModified();
  }

  @Override
  public SubmissionResponse sendHeartbeat(String sessionIdOrUserId, boolean closeSession) {
    return new SubmissionResponse(200, null, null);
  }

  List<Map<String, Object>> events() {
    return new ArrayList<>(events);
  }

  /** Flushes the client until at least {@code count} events were posted, or fails. */
  List<Map<String, Object>> awaitEvents(ExceptionlessClient client, int count)
      throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (events.size() < count) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("Expected " + count + " events, got " + events);
      }
      client.flush(Duration.ofSeconds(2));
      if (events.size() < count) {
        Thread.sleep(25);
      }
    }
    return events();
  }

  /** Flushes the client and returns everything posted so far. */
  List<Map<String, Object>> flush(ExceptionlessClient client) {
    client.flush(Duration.ofSeconds(5));
    return events();
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> data(Map<String, Object> event) {
    return (Map<String, Object>) event.get("data");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> request(Map<String, Object> event) {
    return (Map<String, Object>) data(event).get("@request");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> error(Map<String, Object> event) {
    return (Map<String, Object>) data(event).get("@error");
  }
}
