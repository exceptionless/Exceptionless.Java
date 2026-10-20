package com.exceptionless.e2e.support;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.models.UserDescription;
import com.exceptionless.submission.DefaultSubmissionClient;
import com.exceptionless.submission.SettingsResponse;
import com.exceptionless.submission.SubmissionClient;
import com.exceptionless.submission.SubmissionResponse;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Wraps the client's real {@link DefaultSubmissionClient} (so requests really go over HTTP to the
 * server) and records each request and response for assertions and evidence.
 */
public final class RecordingSubmissionClient implements SubmissionClient {
  /** One request: kind is events, user-description, settings or heartbeat. */
  public record Call(
      String kind, String detail, String body, int status, Integer configVersion, String message) {}

  private final SubmissionClient delegate;
  private final List<Call> calls = new CopyOnWriteArrayList<>();

  public RecordingSubmissionClient(ExceptionlessConfiguration config) {
    this.delegate = new DefaultSubmissionClient(config);
  }

  public List<Call> calls() {
    return List.copyOf(calls);
  }

  public List<Call> calls(String kind) {
    return calls.stream().filter(c -> c.kind().equals(kind)).toList();
  }

  @Override
  public SubmissionResponse postEvents(String eventsJson) {
    SubmissionResponse response = delegate.postEvents(eventsJson);
    calls.add(
        new Call(
            "events",
            null,
            eventsJson,
            response.statusCode(),
            response.configVersion(),
            response.message()));
    return response;
  }

  @Override
  public SubmissionResponse postUserDescription(String referenceId, UserDescription description) {
    SubmissionResponse response = delegate.postUserDescription(referenceId, description);
    calls.add(
        new Call(
            "user-description",
            referenceId,
            com.exceptionless.json.JsonWriter.toJson(description),
            response.statusCode(),
            response.configVersion(),
            response.message()));
    return response;
  }

  @Override
  public SettingsResponse getSettings(int version) {
    SettingsResponse response = delegate.getSettings(version);
    String detail = "v=" + version;
    if (response instanceof SettingsResponse.Updated updated) {
      calls.add(
          new Call(
              "settings",
              detail,
              String.valueOf(updated.settings()),
              200,
              updated.version(),
              null));
    } else if (response instanceof SettingsResponse.Failed failed) {
      calls.add(new Call("settings", detail, null, -1, null, failed.message()));
    } else {
      calls.add(new Call("settings", detail, null, 304, null, null));
    }
    return response;
  }

  @Override
  public SubmissionResponse sendHeartbeat(String sessionIdOrUserId, boolean closeSession) {
    SubmissionResponse response = delegate.sendHeartbeat(sessionIdOrUserId, closeSession);
    calls.add(
        new Call(
            "heartbeat",
            sessionIdOrUserId + (closeSession ? " close" : ""),
            null,
            response.statusCode(),
            response.configVersion(),
            response.message()));
    return response;
  }
}
