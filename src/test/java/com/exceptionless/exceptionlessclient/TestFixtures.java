package com.exceptionless.exceptionlessclient;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.queue.DefaultEventQueue;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import org.mockito.Mockito;

public final class TestFixtures {
  private TestFixtures() {}

  public static Configuration.ConfigurationBuilder aDefaultConfigurationManager() {
    return Configuration.builder()
        .apiKey("12456790abcdef")
        .queue(Mockito.mock(DefaultEventQueue.class))
        .storageProvider(InMemoryStorageProvider.builder().build());
  }
}
