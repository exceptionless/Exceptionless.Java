package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.submission.SettingsResponse;
import com.exceptionless.exceptionlessclient.utils.Utils;
import com.exceptionless.exceptionlessclient.utils.VisibleForTesting;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Builder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class DefaultSettingsClient implements SettingsClientIF {
  private final Configuration configuration;
  private final HttpClient httpClient;

  @Builder
  public DefaultSettingsClient(Configuration configuration) {
    this.configuration = configuration;
    this.httpClient = HttpClient.newHttpClient();
  }

  @VisibleForTesting
  DefaultSettingsClient(Configuration configuration, HttpClient httpClient) {
    this.configuration = configuration;
    this.httpClient = httpClient;
  }

  @Override
  public SettingsResponse getSettings(long version) {
    try {
      URI uri =
          new URI(
              String.format(
                  "%s/api/v2/projects/config?v=%s&access_token=%s",
                  configuration.getServerUrl(), version, configuration.getApiKey()));

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(uri)
              .GET()
              .header("User-Agent", Configuration.USER_AGENT)
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
    } catch (Exception e) {
      return SettingsResponse.builder().success(false).exception(e).message(e.getMessage()).build();
    }
  }
}
