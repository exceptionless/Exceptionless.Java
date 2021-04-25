package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.PluginContext;
import com.exceptionless.exceptionlessclient.models.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.models.enums.EventType;
import com.exceptionless.exceptionlessclient.models.error.StackFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorPluginTest {
  private ConfigurationManager configurationManager;
  private ErrorPlugin plugin;

  @BeforeEach
  public void setup() {
    configurationManager = TestFixtures.aDefaultConfigurationManager().build();
    plugin = ErrorPlugin.builder().build();
  }

  @Test
  public void itCanAddExceptionToEventCorrectly() {
    Exception exc = new RuntimeException("test");
    EventPluginContext context =
        EventPluginContext.builder()
            .event(Event.builder().build())
            .context(PluginContext.builder().exception(exc).build())
            .build();

    plugin.run(context, configurationManager);

    Event event = context.getEvent();
    assertThat(event.getType()).isEqualTo(EventType.ERROR.value());
    assertThat(event.getError()).isPresent();
    assertThat(event.getError().get().getMessage()).isEqualTo("test");
    assertThat(event.getError().get().getType()).isEqualTo("java.lang.RuntimeException");
    assertThat(event.getError().get().getStackTrace()).isNotEmpty();

    StackFrame frame = event.getError().get().getStackTrace().get(0);
    // it should be the test file name.
    assertThat(frame.getFilename()).isEqualTo("ErrorPluginTest.java");

    Map<String, Object> data = event.getData();
    assertThat(data).isNotNull();
    assertThat(data).containsKey(EventPropertyKey.EXTRA.value());

    assertThat(data.get(EventPropertyKey.EXTRA.value())).isSameAs(exc);
  }
}
