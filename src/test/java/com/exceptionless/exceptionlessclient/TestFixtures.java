package com.exceptionless.exceptionlessclient;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.queue.DefaultEventQueue;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import org.mockito.Mockito;

public final class TestFixtures {
  private TestFixtures() {}

  public static ConfigurationManager.ConfigurationManagerBuilder aDefaultConfigurationManager() {
    return ConfigurationManager.builder()
        .apiKey("12456790abcdef")
        .queue(Mockito.mock(DefaultEventQueue.class))
        .storageProvider(InMemoryStorageProvider.builder().build());
  }
}
