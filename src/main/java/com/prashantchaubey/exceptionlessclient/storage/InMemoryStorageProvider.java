package com.prashantchaubey.exceptionlessclient.storage;

import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;

public class InMemoryStorageProvider implements StorageProviderIF {
  @Override
  public StorageIF getQueue() {
    return null;
  }

  @Override
  public StorageIF<ServerSettings> getSettings() {
    return null;
  }
}
