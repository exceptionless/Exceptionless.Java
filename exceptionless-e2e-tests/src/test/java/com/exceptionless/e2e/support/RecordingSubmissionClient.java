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
  public SubmissionResponse postEvents(String eventsJson, ExceptionlessConfiguration config) {
    SubmissionResponse response = delegate.postEvents(eventsJson, config);
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
  public SubmissionResponse postUserDescription(
      String referenceId, UserDescription description, ExceptionlessConfiguration config) {
    SubmissionResponse response = delegate.postUserDescription(referenceId, description, config);
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
  public SettingsResponse getSettings(int version, ExceptionlessConfiguration config) {
    SettingsResponse response = delegate.getSettings(version, config);
    int status = response.success() ? 200 : response.notModified() ? 304 : -1;
    calls.add(
        new Call(
            "settings",
            "v=" + version,
            String.valueOf(response.settings()),
            status,
            response.version(),
            response.message()));
    return response;
  }

  @Override
  public SubmissionResponse sendHeartbeat(
      String sessionIdOrUserId, boolean closeSession, ExceptionlessConfiguration config) {
    SubmissionResponse response = delegate.sendHeartbeat(sessionIdOrUserId, closeSession, config);
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
