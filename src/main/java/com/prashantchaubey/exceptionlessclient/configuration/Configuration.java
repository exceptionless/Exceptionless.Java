package com.prashantchaubey.exceptionlessclient.configuration;

import lombok.Builder;
import lombok.Getter;

@Builder(toBuilder = true)
@Getter
public class Configuration {
  public static final String USER_AGENT = "exceptionless-java/1.0";

  private String apiKey;
  @Builder.Default private String serverUrl = "https://collector.exceptionless.io";
  @Builder.Default private String configServerUrl = "https://config.exceptionless.io";
  @Builder.Default private String heartbeatServerUrl = "https://heartbeat.exceptionless.io";
  @Builder.Default private long updateSettingsWhenIdleInterval = 120000;
  @Builder.Default private boolean includePrivateInformation = true;
  @Builder.Default private int submissionBatchSize = 50;
  @Builder.Default private int submissionClientTimeoutInMillis = 100;
  @Builder.Default private int settingsClientTimeoutInMillis = 100;

  public boolean isApiKeyValid() {
    return apiKey != null && apiKey.length() > 10;
  }

  public static Configuration defaultConfiguration() {
    return Configuration.builder().build();
  }
}
