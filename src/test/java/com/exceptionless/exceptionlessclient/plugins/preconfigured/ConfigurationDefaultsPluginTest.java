package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationDefaultsPluginTest {
  private ConfigurationDefaultsPlugin plugin;
  private EventPluginContext context;
  private ConfigurationManager configurationManager;

  @BeforeEach
  public void setup() {
    plugin = ConfigurationDefaultsPlugin.builder().build();
    context = EventPluginContext.from(Event.builder().build());
    configurationManager =
        ConfigurationManager.builder()
            .configuration(Configuration.builder().apiKey("12456790abcdef").build())
            .build();
  }

  @Test
  public void itAddsDefaultTags() {
    configurationManager.addDefaultTags("test1", "test2");

    plugin.run(context, configurationManager);

    Event event = context.getEvent();
    assertThat(event.getTags()).containsAll(Arrays.asList("test1", "test2"));
  }

  @Test
  public void itAddsDefaultData() {
    configurationManager.setVersion("123");
    configurationManager.setUserIdentity("test-name", "test-identity");

    plugin.run(context, configurationManager);

    Event event = context.getEvent();
    assertThat(event.getData())
        .isEqualTo(
            Map.of(
                "@version",
                "123",
                "@user",
                UserInfo.builder().identity("test-identity").name("test-name").build()));
  }
}
