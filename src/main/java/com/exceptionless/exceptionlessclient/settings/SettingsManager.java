package com.exceptionless.exceptionlessclient.settings;

import com.exceptionless.exceptionlessclient.exceptions.SettingsClientException;
import com.exceptionless.exceptionlessclient.models.storage.StorageItem;
import com.exceptionless.exceptionlessclient.models.submission.SettingsResponse;
import com.exceptionless.exceptionlessclient.storage.StorageProviderIF;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Map;

public class SettingsManager {
  private static final Logger LOG = LoggerFactory.getLogger(SettingsManager.class);
  private static final long DEFAULT_VERSION = 0;

  private final StorageProviderIF storageProvider;
  private final SettingsClientIF settingsClient;
  private Boolean updatingSettings;
  private final PropertyChangeSupport propertyChangeSupport;

  @Builder
  public SettingsManager(StorageProviderIF storageProvider, SettingsClientIF settingsClient) {
    this.storageProvider = storageProvider;
    this.settingsClient = settingsClient;
    this.propertyChangeSupport = new PropertyChangeSupport(this);
    this.updatingSettings = false;
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    propertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void checkVersion(long version) {
    long currentVersion = getVersion();
    if (version <= currentVersion) {
      return;
    }

    LOG.info(String.format("Updating settings from v%s to v%s", currentVersion, version));
    updateSettings();
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

  public void updateSettings() {
    synchronized (this) {
      if (updatingSettings) {
        LOG.trace("Already updating settings; Returning...");
        return;
      }
      updatingSettings = true;
    }

    try {
      long currentVersion = getVersion();
      LOG.info(String.format("Checking for updated settings  from v%s", currentVersion));

      SettingsResponse response = settingsClient.getSettings(currentVersion);
      if (shouldNotUpdate(response)) {
        return;
      }

      ServerSettings prevValue = getSavedServerSettings();
      storageProvider.getSettings().save(response.getSettings());
      propertyChangeSupport.firePropertyChange("settings", prevValue, response.getSettings());
    } catch (SettingsClientException e) {
      LOG.error(String.format("Error retrieving settings for v%s", getVersion()), e);
    } finally {
      synchronized (this) {
        updatingSettings = false;
      }
    }
  }

  private boolean shouldNotUpdate(SettingsResponse response) {
    if (response.isNotModified()) {
      LOG.trace("No need to update, settings are not modified");
      return true;
    }
    if (!response.isSuccess()) {
      LOG.warn(String.format("Unable to update settings: %s", response.getBody()));
      return true;
    }
    if (response.getSettings() == null) {
      LOG.warn("Not settings returned by server!");
      return true;
    }

    return false;
  }
}
