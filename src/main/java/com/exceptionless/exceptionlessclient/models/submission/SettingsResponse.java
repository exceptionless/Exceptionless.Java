package com.exceptionless.exceptionlessclient.models.submission;

import com.exceptionless.exceptionlessclient.models.settings.ServerSettings;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
public class SettingsResponse {
  private boolean success;
  private ServerSettings settings;
  private Exception exception;
  private String message;
}
