package com.exceptionless.exceptionlessclient.submission;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.exceptions.SubmissionClientException;
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
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class DefaultSubmissionClient implements SubmissionClientIF {
  private static final String CONFIGURATION_VERSION_HEADER = "x-exceptionless-configversion";

  private final Configuration configuration;
  private final SettingsManager settingsManager;
  private final OkHttpClient httpClient;

  @Builder
  public DefaultSubmissionClient(Configuration configuration, SettingsManager settingsManager) {
    this.configuration = configuration;
    this.settingsManager = settingsManager;
    this.httpClient =
        new OkHttpClient()
            .newBuilder()
            .connectTimeout(Duration.ofMillis(configuration.getSubmissionClientTimeoutInMillis()))
            .build();
  }

  @VisibleForTesting
  DefaultSubmissionClient(
      Configuration configuration, SettingsManager settingsManager, OkHttpClient httpClient) {
    this.configuration = configuration;
    this.settingsManager = settingsManager;
    this.httpClient = httpClient;
  }

  @Override
  public SubmissionResponse postEvents(List<Event> events) {
    return postSubmission(
        String.format(
            "%s/api/v2/events?access_token=%s",
            configuration.getServerUrl(), configuration.getApiKey()),
        events.stream().map(SubmissionMapper::toRequest).collect(Collectors.toList()));
  }

  @Override
  public SubmissionResponse postUserDescription(String referenceId, UserDescription description) {
    return postSubmission(
        String.format(
            "%s/api/v2/events/by-ref/%s/user-description?access_token=%s",
            configuration.getServerUrl(), referenceId, configuration.getApiKey()),
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

      Response response = httpClient.newCall(request).execute();

      if (response.isSuccessful()) {
        updateSettingsFromHeaders(response.headers());
      }

      ResponseBody body = response.body();
      return SubmissionResponse.builder()
          .code(response.code())
          .body(body == null ? "" : body.string())
          .build();
    } catch (Exception e) {
      throw new SubmissionClientException(e);
    }
  }

  private void updateSettingsFromHeaders(Headers headers) {
    Optional<String> maybeSettingsVersion =
        Optional.ofNullable(headers.get(CONFIGURATION_VERSION_HEADER));
    if (maybeSettingsVersion.isPresent()) {
      settingsManager.checkVersion(Long.parseLong(maybeSettingsVersion.get()));
    } else {
      log.error("No config version header was returned");
    }
  }

  @Override
  public void sendHeartBeat(String sessionIdOrUserId, boolean closeSession) {
    try {
      Request request =
          new Request.Builder()
              .url(
                  String.format(
                      "%s/api/v2/events/session/heartbeat?id=%s&close=%s&access_token=%s",
                      configuration.getHeartbeatServerUrl(),
                      URLEncoder.encode(sessionIdOrUserId, StandardCharsets.UTF_8),
                      closeSession,
                      configuration.getApiKey()))
              .get()
              .build();

      Response response = httpClient.newCall(request).execute();

      if (response.isSuccessful()) {
        log.error(
            String.format(
                "Error in submitting heartbeat to the server for sessionOrUserId: %s",
                sessionIdOrUserId));
      }
    } catch (Exception e) {
      throw new SubmissionClientException(e);
    }
  }
}
