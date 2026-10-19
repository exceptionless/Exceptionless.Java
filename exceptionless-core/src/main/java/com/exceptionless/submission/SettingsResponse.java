package com.exceptionless.submission;

import java.util.Map;

/**
 * The result of fetching project settings: {@link Updated new settings}, {@link NotModified no
 * change}, or {@link Failed a failure}.
 *
 * <pre>{@code
 * if (client.getSettings(version) instanceof SettingsResponse.Updated updated) {
 *   apply(updated.settings(), updated.version());
 * }
 * }</pre>
 */
public sealed interface SettingsResponse
    permits SettingsResponse.Updated, SettingsResponse.NotModified, SettingsResponse.Failed {

  /**
   * New settings were received.
   *
   * @param settings the project settings
   * @param version the settings version
   */
  record Updated(Map<String, String> settings, int version) implements SettingsResponse {
    public Updated {
      settings = Map.copyOf(settings);
    }
  }

  /** The server's settings version matches ours (HTTP 304). */
  record NotModified() implements SettingsResponse {}

  /**
   * The settings could not be fetched.
   *
   * @param message why
   */
  record Failed(String message) implements SettingsResponse {}
}
