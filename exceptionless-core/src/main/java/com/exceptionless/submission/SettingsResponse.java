package com.exceptionless.submission;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The result of fetching project settings.
 *
 * @param success whether new settings were received
 * @param notModified true when the server's settings version matches ours (HTTP 304)
 * @param settings the settings, when successful
 * @param version the settings version, when successful
 * @param message an error message, when not successful
 */
public record SettingsResponse(
    boolean success,
    boolean notModified,
    Map<String, String> settings,
    int version,
    @Nullable String message) {

  public static SettingsResponse notModifiedResponse() {
    return new SettingsResponse(false, true, Map.of(), -1, null);
  }

  public static SettingsResponse failed(String message) {
    return new SettingsResponse(false, false, Map.of(), -1, message);
  }

  public static SettingsResponse of(Map<String, String> settings, int version) {
    return new SettingsResponse(true, false, Map.copyOf(settings), version, null);
  }
}
