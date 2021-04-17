package com.exceptionless.exceptionlessclient.models.submission;

import com.exceptionless.exceptionlessclient.settings.ServerSettings;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
public class SettingsResponse {
  int code;
  String body;
  ServerSettings settings;

  public boolean isSuccess() {
    return code >= 200 && code <= 299;
  }

  public boolean isNotModified() {
    return code == 304;
  }
}
