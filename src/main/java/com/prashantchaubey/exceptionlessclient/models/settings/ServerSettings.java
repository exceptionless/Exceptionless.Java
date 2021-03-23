package com.prashantchaubey.exceptionlessclient.models.settings;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Builder
@Getter
public class ServerSettings {
  private long version;
  private Map<String, String> settings;
}
