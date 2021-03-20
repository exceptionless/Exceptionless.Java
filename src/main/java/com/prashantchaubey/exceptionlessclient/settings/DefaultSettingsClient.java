package com.prashantchaubey.exceptionlessclient.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationSettings;
import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import com.prashantchaubey.exceptionlessclient.models.submission.SettingsResponse;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.prashantchaubey.exceptionlessclient.configuration.ConfigurationSettings.USER_AGENT;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class DefaultSettingsClient implements SettingsClientIF {
  private ConfigurationSettings settings;
  @Builder.Default private long timeoutInMillis = 100;

  // lombok ignored fields
  private ObjectMapper $objectMapper = new ObjectMapper();
  private HttpClient $httpClient = HttpClient.newHttpClient();

  @Override
  public SettingsResponse getSettings(long version) {
    try {
      URI uri =
          new URI(
              String.format(
                  "%s/api/v2/projects/config?v=%s&access_token=%s",
                  version, settings.getServerUrl(), settings.getApiKey()));

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .GET()
              .header("X-Exceptionless-Client", USER_AGENT)
              .timeout(Duration.ofMillis(timeoutInMillis))
              .build();

      HttpResponse<String> response =
          $httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      ServerSettings serverSettings =
          $objectMapper.readValue(response.body(), new TypeReference<ServerSettings>() {});

      return SettingsResponse.builder().success(true).settings(serverSettings).build();
    } catch (URISyntaxException | InterruptedException | IOException e) {
      return SettingsResponse.builder().success(false).exception(e).build();
    }
  }
}
