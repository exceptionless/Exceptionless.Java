package com.exceptionless;

import com.exceptionless.json.JsonParser;
import com.exceptionless.models.UserDescription;
import com.exceptionless.submission.SettingsResponse;
import com.exceptionless.submission.SubmissionClient;
import com.exceptionless.submission.SubmissionResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Test helpers for building clients that never touch the network. */
public final class TestClients {
  public static final String API_KEY = "LhhP1C9gijpSKCslHHCvwdSIz298twx271nTest";

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
    public volatile SettingsResponse settingsResponse = SettingsResponse.notModifiedResponse();

    @Override
    public SubmissionResponse postEvents(String eventsJson, ExceptionlessConfiguration config) {
      posts.add(eventsJson);
      return eventsResponse.apply(eventsJson);
    }

    @Override
    public SubmissionResponse postUserDescription(
        String referenceId, UserDescription description, ExceptionlessConfiguration config) {
      userDescriptions.add(
          referenceId + "=" + com.exceptionless.json.JsonWriter.toJson(description));
      return new SubmissionResponse(202, null, null);
    }

    @Override
    public SettingsResponse getSettings(int version, ExceptionlessConfiguration config) {
      settingsRequests.add(version);
      return settingsResponse;
    }

    @Override
    public SubmissionResponse sendHeartbeat(
        String sessionIdOrUserId, boolean closeSession, ExceptionlessConfiguration config) {
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
