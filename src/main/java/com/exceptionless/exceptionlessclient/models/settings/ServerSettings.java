package com.exceptionless.exceptionlessclient.models.settings;

import com.exceptionless.exceptionlessclient.models.enums.ServerSettingKey;
import com.exceptionless.exceptionlessclient.utils.Utils;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.*;
import java.util.stream.Collectors;

@JsonDeserialize(builder = ServerSettings.ServerSettingsBuilder.class)
@Builder(builderClassName = "ServerSettingsBuilder")
@Value
@NonFinal
public class ServerSettings {
  Long version;
  @Builder.Default Map<String, String> settings = new HashMap<>();

  // Required for Jackson to work with Lombok Immutable(@Value + @Builder)
  @JsonPOJOBuilder(withPrefix = "")
  public static class ServerSettingsBuilder {}

  public Optional<String> getTypeAndSourceSetting(String type, String source) {
    String prefix = String.format("@@%s", type);
    String value = settings.get(String.format("%s:%s", prefix, source));
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
      if (Utils.match(source, key.substring(prefix.length()))) {
        return Optional.of(settings.get(key));
      }
    }

    return Optional.empty();
  }

  public Set<String> getDataExclusions() {
    if (!settings.containsKey(ServerSettingKey.DATA_EXCLUSIONS.value())) {
      return new HashSet<>();
    }

    return Arrays.stream(settings.get(ServerSettingKey.DATA_EXCLUSIONS.value()).split(","))
        .collect(Collectors.toSet());
  }

  public Set<String> getUserAgentBotPatterns() {
    if (!settings.containsKey(ServerSettingKey.USER_AGENT_BOT_PATTERNS.value())) {
      return new HashSet<>();
    }

    return Arrays.stream(settings.get(ServerSettingKey.USER_AGENT_BOT_PATTERNS.value()).split(","))
        .collect(Collectors.toSet());
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
