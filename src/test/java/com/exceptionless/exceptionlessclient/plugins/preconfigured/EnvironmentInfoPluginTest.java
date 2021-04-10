package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentInfoPluginTest {
  private EventPluginContext context;
  private ConfigurationManager configurationManager;
  private EnvironmentInfoPlugin plugin;

  @BeforeEach
  public void setup() {
    context = EventPluginContext.from(Event.builder().build());
    configurationManager = TestFixtures.aDefaultConfigurationManager().build();
    plugin = EnvironmentInfoPlugin.builder().build();
  }

  @Test
  public void itCanFillEnvironmentInfo() {
    assertThat(context.getEvent().getEnvironmentInfo()).isEmpty();

    plugin.run(context,configurationManager);

    assertThat(context.getEvent().getEnvironmentInfo()).isPresent();
  }
}
