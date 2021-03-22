package com.prashantchaubey.exceptionlessclient.models.submission;

import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Builder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SettingsResponse {
  private boolean success;
  private ServerSettings settings;
  private Exception exception;
  private String message;
}
