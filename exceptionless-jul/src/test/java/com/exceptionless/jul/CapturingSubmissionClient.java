package com.exceptionless.jul;

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

/** Captures the events a client posts, parsed from the JSON it would send. */
final class CapturingSubmissionClient implements SubmissionClient {
  static final String API_KEY = "LhhP1C9gijpSKCslHHCvwdSIz298twx271nTest";

  private final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();

  /** A started client that sends to this fake. */
  ExceptionlessClient newClient() {
    return new ExceptionlessClient(newConfiguration()).startup();
  }

  ExceptionlessConfiguration newConfiguration() {
    return new ExceptionlessConfiguration()
        .apiKey(API_KEY)
        .submissionClient(this)
        .captureUncaughtExceptions(false)
        .registerShutdownHook(false)
        .includeModules(false);
  }

  /** Sends the client's queued events and returns everything posted so far. */
  List<Map<String, Object>> flush(ExceptionlessClient client) {
    if (!client.flush(Duration.ofSeconds(10))) {
      throw new AssertionError("Queued events were not sent");
    }
    return events();
  }

  List<Map<String, Object>> events() {
    return new ArrayList<>(events);
  }

  @Override
  @SuppressWarnings("unchecked")
  public SubmissionResponse postEvents(String eventsJson, ExceptionlessConfiguration config) {
    events.addAll((List<Map<String, Object>>) JsonParser.parse(eventsJson));
    return new SubmissionResponse(202, null, null);
  }

  @Override
  public SubmissionResponse postUserDescription(
      String referenceId, UserDescription description, ExceptionlessConfiguration config) {
    return new SubmissionResponse(202, null, null);
  }

  @Override
  public SettingsResponse getSettings(int version, ExceptionlessConfiguration config) {
    return SettingsResponse.notModifiedResponse();
  }

  @Override
  public SubmissionResponse sendHeartbeat(
      String sessionIdOrUserId, boolean closeSession, ExceptionlessConfiguration config) {
    return new SubmissionResponse(200, null, null);
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> data(Map<String, Object> event) {
    Object data = event.get("data");
    return data == null ? Map.of() : (Map<String, Object>) data;
  }
}
