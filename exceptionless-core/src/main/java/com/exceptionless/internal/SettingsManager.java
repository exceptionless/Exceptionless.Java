package com.exceptionless.internal;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.storage.KeyValueStorage;
import com.exceptionless.submission.SettingsResponse;
import com.exceptionless.submission.SubmissionClient;
import java.util.Objects;
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
      SettingsJson.Parsed parsed = SettingsJson.parse(saved);
      config.applyServerSettings(parsed.settings());
      version = Objects.requireNonNullElse(parsed.version(), 0);
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
      SettingsResponse response = submissionClient.getSettings(version);
      if (response instanceof SettingsResponse.Failed failed) {
        LOG.debug(failed::message);
      } else if (response instanceof SettingsResponse.Updated updated) {
        config.applyServerSettings(updated.settings());
        version = updated.version();
        storage.write(STORAGE_KEY, SettingsJson.write(updated.settings(), updated.version()));
        LOG.debug(() -> "Applied settings version " + updated.version());
      }
    } catch (RuntimeException e) {
      LOG.warn("Unable to update settings", e);
    } finally {
      updateLock.unlock();
    }
  }
}
