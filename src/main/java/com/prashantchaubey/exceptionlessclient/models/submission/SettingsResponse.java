package com.prashantchaubey.exceptionlessclient.models.submission;

import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SettingsResponse {
  private boolean success;
  private ServerSettings settings;
  private Exception exception;
  private String message;
}
