package com.prashantchaubey.exceptionlessclient.submission;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SettingsResponse {
  private boolean success;
  private Object settings;
  @Builder.Default private int settingsVersion = -1;
  private String message;
  private Object exception;
}
