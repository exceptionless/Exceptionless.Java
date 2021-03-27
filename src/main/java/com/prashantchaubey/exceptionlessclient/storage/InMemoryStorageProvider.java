package com.prashantchaubey.exceptionlessclient.storage;

import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import lombok.Builder;

public class InMemoryStorageProvider implements StorageProviderIF {
  private StorageIF<Event> eventQueue;
  private StorageIF<ServerSettings> settingsStore;

  @Builder
  private InMemoryStorageProvider(Integer maxQueueItems) {
    this.eventQueue =
        InMemoryStorage.<Event>builder()
            .maxItems(maxQueueItems == null ? 250 : maxQueueItems)
            .build();
    this.settingsStore = InMemoryStorage.<ServerSettings>builder().maxItems(1).build();
  }

  @Override
  public StorageIF<Event> getQueue() {
    return eventQueue;
  }

  @Override
  public StorageIF<ServerSettings> getSettings() {
    return settingsStore;
  }
}
