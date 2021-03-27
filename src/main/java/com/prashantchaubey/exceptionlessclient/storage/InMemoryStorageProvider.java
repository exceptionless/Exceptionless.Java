package com.prashantchaubey.exceptionlessclient.storage;

import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import lombok.Builder;
import lombok.Getter;

@Builder(builderClassName = "InMemoryStroageProviderInternalBuilder")
@Getter
public class InMemoryStorageProvider implements StorageProviderIF {
  @Builder.Default private int maxQueueItems = 250;

  // lombok ignored fields
  private StorageIF<Event> $eventsQueue;
  private StorageIF<ServerSettings> $settingsStore;

  @Override
  public StorageIF<Event> getQueue() {
    return $eventsQueue;
  }

  @Override
  public StorageIF<ServerSettings> getSettings() {
    return $settingsStore;
  }

  public static InMemoryStorageProviderBuilder builder() {
    return new InMemoryStorageProviderBuilder();
  }

  public static class InMemoryStorageProviderBuilder
      extends InMemoryStroageProviderInternalBuilder {
    @Override
    public InMemoryStorageProvider build() {
      InMemoryStorageProvider provider = super.build();
      provider.init();

      return provider;
    }
  }

  private void init() {
    $eventsQueue = InMemoryStorage.<Event>builder().maxItems(maxQueueItems).build();
    $settingsStore = InMemoryStorage.<ServerSettings>builder().maxItems(1).build();
  }
}
