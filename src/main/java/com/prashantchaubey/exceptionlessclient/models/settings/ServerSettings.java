package com.prashantchaubey.exceptionlessclient.models.settings;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Builder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ServerSettings {
  private long version;
  private Map<String, String> settings;
}
