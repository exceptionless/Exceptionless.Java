package com.exceptionless.exceptionlessclient.submission;

import com.exceptionless.exceptionlessclient.configuration.ValueProvider;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.UserDescription;
import com.exceptionless.exceptionlessclient.settings.SettingsManager;
import com.exceptionless.exceptionlessclient.utils.Utils;
import com.exceptionless.exceptionlessclient.utils.VisibleForTesting;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DefaultSubmissionClient implements SubmissionClientIF {
  private static final String CONFIGURATION_VERSION_HEADER = "x-exceptionless-configversion";
  private static final String RATE_LIMITING_HEADER = "x-ratelimit-remaining";

  private final SettingsManager settingsManager;
  private final OkHttpClient defaultHttpClient;
  private final ValueProvider<Integer> submissionClientTimeoutInMillis;
  private final ValueProvider<String> serverUrl;
  private final ValueProvider<String> apiKey;
  private final ValueProvider<String> heartbeatServerUrl;

  @Builder
  public DefaultSubmissionClient(
      SettingsManager settingsManager,
      ValueProvider<Integer> submissionClientTimeoutInMillis,
      ValueProvider<String> serverUrl,
      ValueProvider<String> apiKey,
      ValueProvider<String> heartbeatServerUrl) {
    this(
        new OkHttpClient().newBuilder().build(),
        settingsManager,
        submissionClientTimeoutInMillis,
        serverUrl,
        apiKey,
        heartbeatServerUrl);
  }

  @VisibleForTesting
  DefaultSubmissionClient(
      OkHttpClient defaultHttpClient,
      SettingsManager settingsManager,
      ValueProvider<Integer> submissionClientTimeoutInMillis,
      ValueProvider<String> serverUrl,
      ValueProvider<String> apiKey,
      ValueProvider<String> heartbeatServerUrl) {
    this.settingsManager = settingsManager;
    this.defaultHttpClient = defaultHttpClient;
    this.submissionClientTimeoutInMillis = submissionClientTimeoutInMillis;
    this.serverUrl = serverUrl;
    this.apiKey = apiKey;
    this.heartbeatServerUrl = heartbeatServerUrl;
  }

  @Override
  public SubmissionResponse postEvents(List<Event> events) {
    return postSubmission(
        String.format("%s/api/v2/events?access_token=%s", serverUrl.get(), apiKey.get()),
        events.stream().map(SubmissionMapper::toRequest).collect(Collectors.toList()));
  }

  @Override
  public SubmissionResponse postUserDescription(String referenceId, UserDescription description) {
    return postSubmission(
        String.format(
            "%s/api/v2/events/by-ref/%s/user-description?access_token=%s",
            serverUrl.get(), referenceId, apiKey.get()),
        SubmissionMapper.toRequest(description));
  }

  private SubmissionResponse postSubmission(String url, Object data) {
    try {
      String requestJSON = Utils.JSON_MAPPER.writeValueAsString(data);
      Request request =
          new Request.Builder()
              .url(url)
              .post(RequestBody.create(requestJSON, MediaType.parse("application/json")))
              .build();

      Response response =
          defaultHttpClient
              .newBuilder()
              .connectTimeout(Duration.ofMillis(submissionClientTimeoutInMillis.get()))
              .build()
              .newCall(request)
              .execute();

      if (response.isSuccessful()) {
        updateSettingsFromHeaders(response);
      }

      ResponseBody body = response.body();
      return SubmissionResponse.builder()
          .code(response.code())
          .body(body == null ? "" : body.string())
          .rateLimitingHeaderFound(isRateLimitingHeaderFound(response))
          .build();
    } catch (Exception e) {
      return SubmissionResponse.builder().exception(e).build();
    }
  }

  private void updateSettingsFromHeaders(Response response) {
    String settingsVersion = response.headers().get(CONFIGURATION_VERSION_HEADER);
    if (settingsVersion != null) {
      settingsManager.checkVersion(Long.parseLong(settingsVersion));
    } else {
      log.error("No config version header was returned");
    }
  }

  private boolean isRateLimitingHeaderFound(Response response) {
    return response.headers().get(RATE_LIMITING_HEADER) != null;
  }

  @Override
  public void sendHeartBeat(String sessionIdOrUserId, boolean closeSession) {
    try {
      Request request =
          new Request.Builder()
              .url(
                  String.format(
                      "%s/api/v2/events/session/heartbeat?id=%s&close=%s&access_token=%s",
                      heartbeatServerUrl.get(),
                      URLEncoder.encode(sessionIdOrUserId, StandardCharsets.UTF_8),
                      closeSession,
                      apiKey.get()))
              .get()
              .build();

      Response response =
          defaultHttpClient
              .newBuilder()
              .connectTimeout(Duration.ofMillis(submissionClientTimeoutInMillis.get()))
              .build()
              .newCall(request)
              .execute();

      if (!response.isSuccessful()) {
        log.error(
            String.format(
                "Error in submitting heartbeat to the server for sessionOrUserId: %s",
                sessionIdOrUserId));
      }
    } catch (Exception e) {
      log.error("Error while submitting heartbeat", e);
    }
  }
}
