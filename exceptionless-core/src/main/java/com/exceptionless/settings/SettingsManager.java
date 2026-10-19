package com.exceptionless.settings;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.internal.Log;
import com.exceptionless.json.JsonParser;
import com.exceptionless.json.JsonWriter;
import com.exceptionless.storage.KeyValueStorage;
import com.exceptionless.submission.SettingsResponse;
import com.exceptionless.submission.SubmissionClient;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the project settings up to date. Settings are persisted so they apply immediately on the
 * next start, before the first request to the server.
 */
public final class SettingsManager {
  static final String STORAGE_KEY = "server-settings.json";
  private static final Log LOG = Log.get(SettingsManager.class);

  private final ExceptionlessConfiguration config;
  private final SubmissionClient submissionClient;
  private final KeyValueStorage storage;
  private final ReentrantLock updateLock = new ReentrantLock();
  private volatile int version;

  public SettingsManager(
      ExceptionlessConfiguration config,
      SubmissionClient submissionClient,
      KeyValueStorage storage) {
    this.config = config;
    this.submissionClient = submissionClient;
    this.storage = storage;
  }

  /** The version of the settings currently applied (0 when none). */
  public int getVersion() {
    return version;
  }

  /** Applies the persisted settings, if any. Returns true when settings were loaded. */
  public boolean loadSavedSettings() {
    String saved = storage.read(STORAGE_KEY);
    if (saved == null) {
      return false;
    }
    try {
      Map<String, Object> json = JsonParser.parseObject(saved);
      Map<String, String> settings = new LinkedHashMap<>();
      if (json.get("settings") instanceof Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
          if (entry.getValue() != null) {
            settings.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
          }
        }
      }
      int savedVersion = json.get("version") instanceof Number n ? n.intValue() : 0;
      config.applyServerSettings(settings);
      version = savedVersion;
      return true;
    } catch (IllegalArgumentException e) {
      LOG.warn("Ignoring unreadable saved settings", e);
      return false;
    }
  }

  /** Fetches settings when the server reports a newer version than ours. */
  public void checkVersion(@Nullable Integer serverVersion) {
    if (serverVersion != null && serverVersion > version) {
      updateSettings();
    }
  }

  /** Fetches and applies the latest settings. Blocks while the request runs. */
  public void updateSettings() {
    if (!config.isEnabled() || !updateLock.tryLock()) {
      return;
    }
    try {
      SettingsResponse response = submissionClient.getSettings(version, config);
      if (response.notModified()) {
        return;
      }
      if (!response.success()) {
        LOG.debug(() -> String.valueOf(response.message()));
        return;
      }
      config.applyServerSettings(response.settings());
      version = response.version();
      storage.write(
          STORAGE_KEY,
          JsonWriter.toJson(
              Map.of("version", response.version(), "settings", response.settings())));
      LOG.debug(() -> "Applied settings version " + response.version());
    } catch (RuntimeException e) {
      LOG.warn("Unable to update settings", e);
    } finally {
      updateLock.unlock();
    }
  }
}
