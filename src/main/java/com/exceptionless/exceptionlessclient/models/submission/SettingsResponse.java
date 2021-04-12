package com.exceptionless.exceptionlessclient.models.submission;

import com.exceptionless.exceptionlessclient.settings.ServerSettings;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
public class SettingsResponse {
  @Builder.Default Boolean success = false;
  ServerSettings settings;
  Exception exception;
  String message;

  public Boolean isSuccess() {
    return success;
  }
}
