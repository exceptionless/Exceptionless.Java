package com.exceptionless.internal;

import com.exceptionless.json.JsonParser;
import com.exceptionless.json.JsonWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reads and writes project settings documents, {@code {"version": 3, "settings": {...}}}, as
 * returned by the server and persisted by {@link SettingsManager}.
 */
public final class SettingsJson {
  private SettingsJson() {}

  /**
   * A parsed settings document.
   *
   * @param settings the settings; values that are {@code null} are dropped
   * @param version the settings version, or {@code null} when it is missing
   */
  public record Parsed(Map<String, String> settings, @Nullable Integer version) {}

  /**
   * Parses a settings document.
   *
   * @throws IllegalArgumentException if {@code json} is not a JSON object
   */
  public static Parsed parse(String json) {
    Map<String, @Nullable Object> document = JsonParser.parseObject(json);
    Map<String, String> settings = new LinkedHashMap<>();
    if (document.get("settings") instanceof Map<?, ?> values) {
      for (Map.Entry<?, ?> entry : values.entrySet()) {
        if (entry.getValue() != null) {
          settings.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
      }
    }
    Integer version = document.get("version") instanceof Number n ? n.intValue() : null;
    return new Parsed(settings, version);
  }

  public static String write(Map<String, String> settings, int version) {
    return JsonWriter.toJson(Map.of("version", version, "settings", settings));
  }
}
