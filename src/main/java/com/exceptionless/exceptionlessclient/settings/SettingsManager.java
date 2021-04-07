package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.logging.LogIF;
import com.exceptionless.exceptionlessclient.models.settings.ServerSettings;
import com.exceptionless.exceptionlessclient.models.storage.StorageItem;
import com.exceptionless.exceptionlessclient.models.submission.SettingsResponse;
import com.exceptionless.exceptionlessclient.storage.StorageProviderIF;
import lombok.Builder;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Map;

public class SettingsManager {
  private static final long DEFAULT_VERSION = 0;

  private LogIF log;
  private StorageProviderIF storageProvider;
  private SettingsClientIF settingsClient;
  private Boolean updatingSettings;
  private PropertyChangeSupport propertyChangeSupport;

  @Builder
  public SettingsManager(
      LogIF log, StorageProviderIF storageProvider, SettingsClientIF settingsClient) {
    this.log = log;
    this.storageProvider = storageProvider;
    this.settingsClient = settingsClient;
    this.propertyChangeSupport = new PropertyChangeSupport(this);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    propertyChangeSupport.addPropertyChangeListener(listener);
  }

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
    if (updatingSettings) {
      return;
    }

    updatingSettings = true;
    try {
      updateSettings();
    } finally {
      updatingSettings = false;
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
    ServerSettings prevValue = storageProvider.getSettings().peek().getValue();
    storageProvider.getSettings().save(response.getSettings());
    propertyChangeSupport.firePropertyChange("settings", prevValue, response.getSettings());
  }
}
