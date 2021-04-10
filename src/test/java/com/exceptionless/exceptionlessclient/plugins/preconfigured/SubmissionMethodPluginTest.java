package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.PluginContext;
import com.exceptionless.exceptionlessclient.plugins.preconfigured.SubmissionMethodPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SubmissionMethodPluginTest {
  private ConfigurationManager configurationManager;
  private SubmissionMethodPlugin plugin;
  private EventPluginContext context;

  @BeforeEach
  public void setup() {
    plugin = SubmissionMethodPlugin.builder().build();
    configurationManager = TestFixtures.aDefaultConfigurationManager().build();
  }

  @Test
  public void itShouldNotDoAnythingIfNoSubmissionMethodInTheContext() {
    context = EventPluginContext.from(Event.builder().build());

    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getSubmissionMethod()).isNotPresent();
  }

  @Test
  public void itShouldAddSubmissionMethodToTheEvent() {
    context =
        EventPluginContext.builder()
            .event(Event.builder().build())
            .context(PluginContext.builder().submissionMethod("test-submission-method").build())
            .build();

    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getSubmissionMethod()).isPresent();
    assertThat(context.getEvent().getSubmissionMethod().get()).isEqualTo("test-submission-method");
  }
}
