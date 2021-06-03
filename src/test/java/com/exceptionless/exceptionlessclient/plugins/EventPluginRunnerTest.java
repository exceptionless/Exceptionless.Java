package com.exceptionless.exceptionlessclient.plugins;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.services.DefaultLastReferenceIdManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.queue.DefaultEventQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EventPluginRunnerTest {
  @Mock private DefaultEventQueue eventQueue;
  @Mock private DefaultLastReferenceIdManager lastReferenceIdManager;
  @Mock private EventPluginIF testPlugin;
  private EventPluginRunner pluginRunner;
  private EventPluginContext context;

  @BeforeEach
  public void setup() {
    context =
        EventPluginContext.from(Event.builder().type("test-type").referenceId("123456789").build());
    Configuration configuration =
        TestFixtures.aDefaultConfigurationManager()
            .queue(eventQueue)
            .lastReferenceIdManager(lastReferenceIdManager)
            .build();
    // should run first
    doReturn(Integer.MAX_VALUE).when(testPlugin).getPriority();
    doReturn("test-plugin").when(testPlugin).getName();
    configuration.addPlugin(testPlugin);
    pluginRunner = EventPluginRunner.builder().configuration(configuration).build();
  }

  @Test
  public void itShouldNotRunPluginIfEventIsCancelled() {
    context.getContext().setEventCancelled(true);

    pluginRunner.run(context);

    verify(testPlugin, times(0)).run(any(), any());
    verify(eventQueue, times(0)).enqueue(any());
    verifyZeroInteractions(lastReferenceIdManager);
  }

  @Test
  public void itShouldCancelEventIfExceptionOccursWhileRunningPlugin() {
    doThrow(new RuntimeException("test")).when(testPlugin).run(any(), any());

    pluginRunner.run(context);

    verify(eventQueue, times(0)).enqueue(any());
    verifyZeroInteractions(lastReferenceIdManager);
    assertThat(context.getContext().isEventCancelled()).isTrue();
  }

  @Test
  public void itShouldRunPluginsSuccessfully() {
    pluginRunner.run(context);

    verify(testPlugin, times(1)).run(eq(context), any());
    verify(eventQueue, times(1)).enqueue(context.getEvent());
    verify(lastReferenceIdManager, times(1)).setLast("123456789");
  }
}
