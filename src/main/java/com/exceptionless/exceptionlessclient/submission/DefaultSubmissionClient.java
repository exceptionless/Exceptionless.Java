package com.exceptionless.exceptionlessclient.submission;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.exceptions.SubmissionException;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.UserDescription;
import com.exceptionless.exceptionlessclient.models.submission.SubmissionResponse;
import com.exceptionless.exceptionlessclient.settings.SettingsManager;
import com.exceptionless.exceptionlessclient.utils.Utils;
import com.exceptionless.exceptionlessclient.utils.VisibleForTesting;
import lombok.Builder;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultSubmissionClient implements SubmissionClientIF {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultSubmissionClient.class);
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

      if (response.code() / 100 == 2) {
        updateSettingsFromHeaders(response.headers());
      }

      return SubmissionResponse.builder()
          .statusCode(response.code())
          .message(Utils.addCodeToResponseBodyStr(response))
          .build();
    } catch (Exception e) {
      throw new SubmissionException(e);
    }
  }

  private void updateSettingsFromHeaders(Headers headers) {
    Optional<String> maybeSettingsVersion =
        Optional.ofNullable(headers.get(CONFIGURATION_VERSION_HEADER));
    if (maybeSettingsVersion.isPresent()) {
      settingsManager.checkVersion(Long.parseLong(maybeSettingsVersion.get()));
    } else {
      LOG.error("No config version header was returned");
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

      if (response.code() / 100 != 2) {
        LOG.error(
            String.format(
                "Error in submitting heartbeat to the server for sessionOrUserId: %s",
                sessionIdOrUserId));
      }
    } catch (Exception e) {
      throw new SubmissionException(e);
    }
  }
}
