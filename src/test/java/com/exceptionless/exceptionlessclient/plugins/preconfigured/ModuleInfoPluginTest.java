package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.models.Module;
import com.exceptionless.exceptionlessclient.models.error.Error;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class ModuleInfoPluginTest {
  private ConfigurationManager configurationManager;
  private ModuleInfoPlugin plugin;
  private EventPluginContext context;

  @BeforeEach
  public void setup() {
    configurationManager = TestFixtures.aDefaultConfigurationManager().build();
    plugin = ModuleInfoPlugin.builder().build();
  }

  @Test
  public void itShouldNotAddModulesIfErrorIsNotFound() {
    context = EventPluginContext.from(Event.builder().build());
    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getError()).isEmpty();
  }

  @Test
  public void itShouldNotAddModulesIfPresent() {
    List<Module> modules = List.of(Module.builder().moduleId(123L).build());
    context =
        EventPluginContext.from(
            Event.builder()
                .property(EventPropertyKey.ERROR.value(), Error.builder().modules(modules).build())
                .build());
    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getError()).isPresent();
    assertThat(context.getEvent().getError().get().getModules()).isEqualTo(modules);
  }

  @Test
  public void itShouldAddModulesIfAbsent() {
    context =
        EventPluginContext.from(
            Event.builder()
                .property(EventPropertyKey.ERROR.value(), Error.builder().build())
                .build());
    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getError()).isPresent();
  }
}
