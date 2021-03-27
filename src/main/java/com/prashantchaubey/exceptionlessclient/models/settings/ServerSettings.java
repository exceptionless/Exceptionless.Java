package com.prashantchaubey.exceptionlessclient.models.settings;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.*;

@Builder
@Value
@NonFinal
public class ServerSettings {
  private long version;
  @Builder.Default private Map<String, String> settings = new HashMap<>();

  public Optional<String> getTypeAndSourceSetting(String type, String source) {
    String prefix = String.format("@@%s", type);
    String value = settings.get(String.format("%s%s", prefix, source));
    if (value != null) {
      return Optional.of(value);
    }

    List<String> keys = new ArrayList<>(settings.keySet());
    // Sort keys longest first, then alphabetically
    keys.sort(
        (o1, o2) -> o1.length() != o2.length() ? o2.length() - o1.length() : o1.compareTo(o2));
    for (String key : keys) {
      if (!key.startsWith(prefix) || key.length() <= prefix.length()) {
        continue;
      }
      // todo check that wildcard match work with this or not
      if (source.matches(key.substring(prefix.length()))) {
        return Optional.of(settings.get(key));
      }
    }

    return Optional.empty();
  }

  public static boolean getAsBoolean(String setting) {
    switch (setting) {
      case "true":
      case "yes":
      case "1":
        return true;
      default:
        return false;
    }
  }
}
