package com.exceptionless.exceptionlessclient.settings;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class SettingsResponse {
  int code;
  String body;
  ServerSettings settings;
  Exception exception;

  public boolean isSuccess() {
    return code >= 200 && code <= 299;
  }

  public boolean isNotModified() {
    return code == 304;
  }

  public boolean hasException() {
    return exception != null;
  }
}
