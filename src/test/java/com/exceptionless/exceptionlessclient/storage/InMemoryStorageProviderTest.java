package com.exceptionless.exceptionlessclient.storage;

import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.settings.ServerSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InMemoryStorageProviderTest {
  @Test
  public void itCanSetStorageCorrectly() {
    InMemoryStorageProvider storageProvider =
        InMemoryStorageProvider.builder().maxQueueItems(2).build();

    storageProvider.getQueue().save(Event.builder().referenceId("123-abcdef").build());
    storageProvider.getQueue().save(Event.builder().referenceId("456-abcdef").build());
    storageProvider.getQueue().save(Event.builder().referenceId("789-abcdef").build());

    storageProvider.getSettings().save(ServerSettings.builder().version(1L).build());
    storageProvider.getSettings().save(ServerSettings.builder().version(2L).build());

    assertThat(storageProvider.getSettings().peek().getValue().getVersion()).isEqualTo(2);
    assertThat(storageProvider.getQueue().peek().getValue().getReferenceId())
        .isEqualTo("456-abcdef");
  }
}
