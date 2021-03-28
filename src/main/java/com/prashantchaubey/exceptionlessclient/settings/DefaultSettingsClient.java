package com.prashantchaubey.exceptionlessclient.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.prashantchaubey.exceptionlessclient.configuration.Configuration;
import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import com.prashantchaubey.exceptionlessclient.models.submission.SettingsResponse;
import com.prashantchaubey.exceptionlessclient.utils.Utils;
import lombok.Builder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.prashantchaubey.exceptionlessclient.configuration.Configuration.USER_AGENT;

public class DefaultSettingsClient implements SettingsClientIF {
  private Configuration configuration;
  private HttpClient httpClient;

  @Builder
  public DefaultSettingsClient(Configuration configuration) {
    this.configuration = configuration;
    this.httpClient = HttpClient.newHttpClient();
  }

  @Override
  public SettingsResponse getSettings(long version) {
    try {
      URI uri =
          new URI(
              String.format(
                  "%s/api/v2/projects/config?v=%s&access_token=%s",
                  version, configuration.getServerUrl(), configuration.getApiKey()));

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .GET()
              .header("X-Exceptionless-Client", USER_AGENT)
              .timeout(Duration.ofMillis(configuration.getSettingsClientTimeoutInMillis()))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        return SettingsResponse.builder().success(false).message(response.body()).build();
      }

      ServerSettings serverSettings =
          Utils.JSON_MAPPER.readValue(response.body(), new TypeReference<ServerSettings>() {});

      return SettingsResponse.builder().success(true).settings(serverSettings).build();
    } catch (URISyntaxException | InterruptedException | IOException e) {
      return SettingsResponse.builder().success(false).exception(e).message(e.getMessage()).build();
    }
  }
}
