package com.exceptionless.exceptionlessclient;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.queue.DefaultEventQueue;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import org.mockito.Mockito;

public final class TestFixtures {
  private TestFixtures() {}

  public static Configuration.ConfigurationBuilder aDefaultConfiguration() {
    return Configuration.builder().apiKey("12456790abcdef");
  }

  public static ConfigurationManager.ConfigurationManagerBuilder aDefaultConfigurationManager() {
    return ConfigurationManager.builder()
        .apiKey("12456790abcdef")
        .configuration(aDefaultConfiguration().build())
        .queue(Mockito.mock(DefaultEventQueue.class))
        .storageProvider(InMemoryStorageProvider.builder().build());
  }
}
