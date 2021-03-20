package com.prashantchaubey.exceptionlessclient.settings;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationSettings;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import com.prashantchaubey.exceptionlessclient.models.storage.StorageItem;
import com.prashantchaubey.exceptionlessclient.models.submission.SettingsResponse;
import com.prashantchaubey.exceptionlessclient.storage.StorageProviderIF;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SettingsManager {
  private static final long DEFAULT_VERSION = 0;

  private LogIF log;
  private ConfigurationSettings settings;
  private StorageProviderIF storageProvider;
  private SettingsClientIF settingsClient;

  // Lombok ignored fields
  private boolean $updatingSettings = false;

  public void checkVersion(long version) {
    long currentVersion = getVersion();
    if (version <= currentVersion) {
      return;
    }

    log.info(String.format("Updating settings from v%s to v%s", currentVersion, version));
    updateSettings();
  }

  private long getVersion() {
    return getSavedServerSettings().getVersion();
  }

  private ServerSettings getSavedServerSettings() {
    List<StorageItem<ServerSettings>> storageItems = storageProvider.getSettings().get();
    if (storageItems.isEmpty()) {
      return ServerSettings.builder().version(DEFAULT_VERSION).settings(Map.of()).build();
    }

    return storageItems.get(0).getValue();
  }

  private void updateSettings() {
    if ($updatingSettings) {
      return;
    }
    if (!settings.isApiKeyValid()) {
      log.error("Unable to update settings: ApiKey is not valid");
      return;
    }

    long currentVersion = getVersion();
    log.info(String.format("Checking for updated settings  from: v%s", currentVersion));

    SettingsResponse response = settingsClient.getSettings(currentVersion);
    //todo implement
  }
}
