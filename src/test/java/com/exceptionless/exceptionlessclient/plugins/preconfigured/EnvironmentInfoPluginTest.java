package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.configuration.PrivateInformationInclusions;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.EnvironmentInfo;
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
  public void itShouldNotIncludeMachineNameAndIpAddressUntilExplicitlyTold() {
    assertThat(context.getEvent().getEnvironmentInfo()).isEmpty();

    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getEnvironmentInfo()).isPresent();
    EnvironmentInfo info = context.getEvent().getEnvironmentInfo().get();
    assertThat(info.getMachineName()).isNull();
    assertThat(info.getIpAddress()).isNull();

    assertThat(info.getProcessorCount()).isNotNull();
  }

  @Test
  public void itCanIncludeMachineNameAndIpAddress() {
    assertThat(context.getEvent().getEnvironmentInfo()).isEmpty();
    PrivateInformationInclusions inclusions =
        configurationManager.getPrivateInformationInclusions();
    inclusions.setIpAddress(true);
    inclusions.setMachineName(true);

    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getEnvironmentInfo()).isPresent();
    EnvironmentInfo info = context.getEvent().getEnvironmentInfo().get();

    assertThat(info.getMachineName()).isNotNull();
    assertThat(info.getIpAddress()).isNotNull();

    assertThat(info.getProcessorCount()).isNotNull();
  }
}
