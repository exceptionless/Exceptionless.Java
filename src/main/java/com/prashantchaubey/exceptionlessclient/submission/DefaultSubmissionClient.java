package com.prashantchaubey.exceptionlessclient.submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationSettings;
import com.prashantchaubey.exceptionlessclient.exceptions.ClientException;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.UserDescription;
import com.prashantchaubey.exceptionlessclient.models.submission.SubmissionResponse;
import com.prashantchaubey.exceptionlessclient.settings.SettingsManager;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

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

import static com.prashantchaubey.exceptionlessclient.configuration.ConfigurationSettings.USER_AGENT;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class DefaultSubmissionClient implements SubmissionClientIF {
  private static final String CONFIGURATION_VERSION_HEADER = "x-exceptionless-configversion";

  private ConfigurationSettings settings;
  private LogIF log;
  private SettingsManager settingsManager;
  @Builder.Default private long timeoutInMillis = 100;

  // Lombok ignored fields
  private ObjectMapper $objectMapper = new ObjectMapper();
  private HttpClient $httpClient = HttpClient.newHttpClient();

  @Override
  public SubmissionResponse postEvents(List<Event> events, boolean isAppExiting) {
    try {
      URI uri =
          new URI(
              String.format(
                  "%s/api/v2/events?access_token=%s",
                  settings.getServerUrl(), settings.getApiKey()));
      String requestJSON = $objectMapper.writeValueAsString(events);

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .POST(HttpRequest.BodyPublishers.ofString(requestJSON))
              .header("Content-Type", "application/json")
              .header("X-Exceptionless-Client", USER_AGENT)
              .timeout(Duration.ofMillis(timeoutInMillis))
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
  public SubmissionResponse postUserDescription(String referenceId, UserDescription description) {
    return null;
  }

  @Override
  public void sendHeartBeat(String sessionIdOrUserId, boolean closeSession) {}
}
