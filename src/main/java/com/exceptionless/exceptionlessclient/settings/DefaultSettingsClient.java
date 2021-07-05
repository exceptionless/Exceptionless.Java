package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.configuration.ValueProvider;
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
  private final OkHttpClient defaultHttpClient;
  private final ValueProvider<Integer> settingsClientTimeoutInMillis;
  private final ValueProvider<String> configServerUrl;
  private final ValueProvider<String> apiKey;

  @Builder
  public DefaultSettingsClient(
      ValueProvider<Integer> settingsClientTimeoutInMillis,
      ValueProvider<String> configServerUrl,
      ValueProvider<String> apiKey) {
    this(
        new OkHttpClient().newBuilder().build(),
        settingsClientTimeoutInMillis,
        configServerUrl,
        apiKey);
  }

  @VisibleForTesting
  DefaultSettingsClient(
      OkHttpClient defaultHttpClient,
      ValueProvider<Integer> settingsClientTimeoutInMillis,
      ValueProvider<String> configServerUrl,
      ValueProvider<String> apiKey) {
    this.defaultHttpClient = defaultHttpClient;
    this.settingsClientTimeoutInMillis = settingsClientTimeoutInMillis;
    this.configServerUrl = configServerUrl;
    this.apiKey = apiKey;
  }

  @Override
  public SettingsResponse getSettings(long version) {
    try {
      Request request =
          new Request.Builder()
              .url(
                  String.format(
                      "%s/api/v2/projects/config?v=%s&access_token=%s",
                      configServerUrl.get(), version, apiKey.get()))
              .get()
              .build();

      Response response =
          defaultHttpClient
              .newBuilder()
              .connectTimeout(Duration.ofMillis(settingsClientTimeoutInMillis.get()))
              .build()
              .newCall(request)
              .execute();

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
      return SettingsResponse.builder().exception(e).build();
    }
  }
}
