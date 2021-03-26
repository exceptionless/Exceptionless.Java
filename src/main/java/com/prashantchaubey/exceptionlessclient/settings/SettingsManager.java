package com.prashantchaubey.exceptionlessclient.settings;

import com.prashantchaubey.exceptionlessclient.configuration.Configuration;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import com.prashantchaubey.exceptionlessclient.models.storage.StorageItem;
import com.prashantchaubey.exceptionlessclient.models.submission.SettingsResponse;
import com.prashantchaubey.exceptionlessclient.storage.StorageProviderIF;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Builder
@Getter
public class SettingsManager {
  private static final long DEFAULT_VERSION = 0;

  private LogIF log;
  private Configuration configuration;
  private StorageProviderIF storageProvider;
  private SettingsClientIF settingsClient;

  // Lombok ignored fields
  private Boolean $updatingSettings = false;

  public void checkVersion(long version) {
    long currentVersion = getVersion();
    if (version <= currentVersion) {
      return;
    }

    log.info(String.format("Updating settings from v%s to v%s", currentVersion, version));
    updateSettingsThreadSafe();
  }

  private long getVersion() {
    return getSavedServerSettings().getVersion();
  }

  public ServerSettings getSavedServerSettings() {
    StorageItem<ServerSettings> storageItem = storageProvider.getSettings().peek();
    if (storageItem == null) {
      return ServerSettings.builder().version(DEFAULT_VERSION).settings(Map.of()).build();
    }

    return storageItem.getValue();
  }

  // This method is thread safe as settings are updated both by the users and by the client at
  // regular intervals
  public synchronized void updateSettingsThreadSafe() {
    if ($updatingSettings) {
      return;
    }

    $updatingSettings = true;
    try {
      updateSettings();
    } finally {
      $updatingSettings = false;
    }
  }

  private void updateSettings() {
    long currentVersion = getVersion();
    log.info(String.format("Checking for updated settings  from: v%s", currentVersion));

    SettingsResponse response = settingsClient.getSettings(currentVersion);
    if (!response.isSuccess()) {
      log.warn(String.format("Unable to update settings: %s:", response.getMessage()));
      return;
    }
    storageProvider.getSettings().save(response.getSettings());
  }
}
