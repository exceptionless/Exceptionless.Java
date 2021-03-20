package com.prashantchaubey.exceptionlessclient.configuration;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ConfigurationSettings {
  public static final String USER_AGENT = "exceptionless-java/1.0";

  private String apiKey;
  @Builder.Default private String serverUrl = "https://collector.exceptionless.io";
  @Builder.Default private String configServerUrl = "https://config.exceptionless.io";
  @Builder.Default private String heartbeatServerUrl = "https://heartbeat.exceptionless.io";
  @Builder.Default private long updateSettingsWhenIdleInterval = 120000;
  @Builder.Default private boolean includePrivateInformation = true;
  @Builder.Default private int submissionBatchSize = 50;

  public boolean isApiKeyValid() {
    return apiKey != null && apiKey.length() > 10;
  }
}
