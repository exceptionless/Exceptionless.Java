package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.exceptions.SettingsClientException;
import com.exceptionless.exceptionlessclient.utils.Utils;
import com.exceptionless.exceptionlessclient.utils.VisibleForTesting;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Builder;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.time.Duration;

public class DefaultSettingsClient implements SettingsClientIF {
  private final Configuration configuration;
  private final OkHttpClient httpClient;

  @Builder
  public DefaultSettingsClient(Configuration configuration) {
    this.configuration = configuration;
    this.httpClient =
        new OkHttpClient()
            .newBuilder()
            .connectTimeout(Duration.ofMillis(configuration.getSettingsClientTimeoutInMillis()))
            .build();
  }

  @VisibleForTesting
  DefaultSettingsClient(Configuration configuration, OkHttpClient httpClient) {
    this.configuration = configuration;
    this.httpClient = httpClient;
  }

  @Override
  public SettingsResponse getSettings(long version) {
    try {
      Request request =
          new Request.Builder()
              .url(
                  String.format(
                      "%s/api/v2/projects/config?v=%s&access_token=%s",
                      configuration.getServerUrl(), version, configuration.getApiKey()))
              .get()
              .build();

      Response response = httpClient.newCall(request).execute();

      ResponseBody body = response.body();
      String bodyStr = body == null ? null : body.string();
      if (bodyStr == null) {
        return SettingsResponse.builder().code(response.code()).body("").build();
      }
      if (!response.isSuccessful()) {
        return SettingsResponse.builder().code(response.code()).body(bodyStr).build();
      }

      ServerSettings serverSettings =
          Utils.JSON_MAPPER.readValue(bodyStr, new TypeReference<ServerSettings>() {});
      return SettingsResponse.builder()
          .code(response.code())
          .body(bodyStr)
          .settings(serverSettings)
          .build();
    } catch (Exception e) {
      throw new SettingsClientException(e);
    }
  }
}
