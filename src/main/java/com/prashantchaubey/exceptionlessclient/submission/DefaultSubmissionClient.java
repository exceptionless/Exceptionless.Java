package com.prashantchaubey.exceptionlessclient.submission;

import com.prashantchaubey.exceptionlessclient.configuration.Configuration;
import com.prashantchaubey.exceptionlessclient.exceptions.ClientException;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.UserDescription;
import com.prashantchaubey.exceptionlessclient.models.submission.SubmissionResponse;
import com.prashantchaubey.exceptionlessclient.settings.SettingsManager;
import com.prashantchaubey.exceptionlessclient.utils.JsonUtils;
import lombok.Builder;
import lombok.Getter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

import static com.prashantchaubey.exceptionlessclient.configuration.Configuration.USER_AGENT;

@Builder
@Getter
public class DefaultSubmissionClient implements SubmissionClientIF {
  private static final String CONFIGURATION_VERSION_HEADER = "x-exceptionless-configversion";

  private Configuration configuration;
  private LogIF log;
  private SettingsManager settingsManager;

  // Lombok ignored fields
  private HttpClient $httpClient = HttpClient.newHttpClient();

  @Override
  public SubmissionResponse postEvents(List<Event> events, boolean isAppExiting) {
    return postSubmission(
        String.format(
            "%s/api/v2/events?access_token=%s",
            configuration.getServerUrl(), configuration.getApiKey()),
        events);
  }

  @Override
  public SubmissionResponse postUserDescription(String referenceId, UserDescription description) {
    return postSubmission(
        String.format(
            "%s/api/v2/events/by-ref/%s/user-description?access_token=%s",
            configuration.getServerUrl(), referenceId, configuration.getApiKey()),
        description);
  }

  private SubmissionResponse postSubmission(String url, Object data) {
    try {
      URI uri = new URI(url);
      String requestJSON = JsonUtils.JSON_MAPPER.writeValueAsString(data);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .POST(HttpRequest.BodyPublishers.ofString(requestJSON))
              .header("Content-Type", "application/json")
              .header("X-Exceptionless-Client", USER_AGENT)
              .timeout(Duration.ofMillis(configuration.getSubmissionClientTimeoutInMillis()))
              .build();

      HttpResponse<String> response =
          $httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      updateSettingsFromHeaders(response.headers());
      return SubmissionResponse.builder()
          .statusCode(response.statusCode())
          .message(response.body())
          .build();
    } catch (URISyntaxException | InterruptedException | IOException e) {
      throw new ClientException(e);
    }
  }

  private void updateSettingsFromHeaders(HttpHeaders headers) {
    OptionalLong maybeSettingsVersion = headers.firstValueAsLong(CONFIGURATION_VERSION_HEADER);
    if (maybeSettingsVersion.isPresent()) {
      settingsManager.checkVersion(maybeSettingsVersion.getAsLong());
    } else {
      log.error("No config version header was returned");
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
                  sessionIdOrUserId,
                  closeSession,
                  configuration.getApiKey()));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .GET()
              .header("X-Exceptionless-Client", USER_AGENT)
              .timeout(Duration.ofMillis(configuration.getSettingsClientTimeoutInMillis()))
              .build();

      HttpResponse<String> response =
          $httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error(
            String.format(
                "Error in submitting heartbeat to the server for sessionOrUserId: %s",
                sessionIdOrUserId));
      }
    } catch (URISyntaxException | InterruptedException | IOException e) {
      throw new ClientException(e);
    }
  }
}
