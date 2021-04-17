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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Collectors;

public class DefaultSubmissionClient implements SubmissionClientIF {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultSubmissionClient.class);
  private static final String CONFIGURATION_VERSION_HEADER = "x-exceptionless-configversion";

  private final Configuration configuration;
  private final SettingsManager settingsManager;
  private final HttpClient httpClient;

  @Builder
  public DefaultSubmissionClient(Configuration configuration, SettingsManager settingsManager) {
    this.configuration = configuration;
    this.settingsManager = settingsManager;
    this.httpClient = HttpClient.newHttpClient();
  }

  @VisibleForTesting
  DefaultSubmissionClient(
      Configuration configuration, SettingsManager settingsManager, HttpClient httpClient) {
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
      URI uri = new URI(url);
      String requestJSON = Utils.JSON_MAPPER.writeValueAsString(data);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .POST(HttpRequest.BodyPublishers.ofString(requestJSON))
              .header("Content-Type", "application/json")
              .header("User-Agent", Configuration.USER_AGENT)
              .timeout(Duration.ofMillis(configuration.getSubmissionClientTimeoutInMillis()))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        updateSettingsFromHeaders(response.headers());
      }

      return SubmissionResponse.builder()
          .statusCode(response.statusCode())
          .message(response.body())
          .build();
    } catch (Exception e) {
      throw new SubmissionException(e);
    }
  }

  private void updateSettingsFromHeaders(HttpHeaders headers) {
    OptionalLong maybeSettingsVersion = headers.firstValueAsLong(CONFIGURATION_VERSION_HEADER);
    if (maybeSettingsVersion.isPresent()) {
      settingsManager.checkVersion(maybeSettingsVersion.getAsLong());
    } else {
      LOG.error("No config version header was returned");
    }
  }

  @Override
  public void sendHeartBeat(String sessionIdOrUserId, boolean closeSession) {
    try {
      URI uri =
          new URI(
              String.format(
                  "%s/api/v2/events/session/heartbeat?id=%s&close=%s&access_token=%s",
                  configuration.getHeartbeatServerUrl(),
                  URLEncoder.encode(sessionIdOrUserId, StandardCharsets.UTF_8),
                  closeSession,
                  configuration.getApiKey()));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .GET()
              .header("X-Exceptionless-Client", Configuration.USER_AGENT)
              .timeout(Duration.ofMillis(configuration.getSubmissionClientTimeoutInMillis()))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
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
