package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.enums.EventType;
import com.exceptionless.exceptionlessclient.models.services.error.Error;
import com.exceptionless.exceptionlessclient.settings.ServerSettings;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorage;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class EventExclusionPluginTest {
  @Mock private InMemoryStorageProvider storageProvider;

  private ConfigurationManager configurationManager;
  private EventPluginContext context;
  private EventExclusionPlugin plugin;

  @BeforeEach
  public void setup() {
    configurationManager =
        TestFixtures.aDefaultConfigurationManager().storageProvider(storageProvider).build();
    plugin = EventExclusionPlugin.builder().build();
  }

  @Test
  public void itShouldNotCancelLogEventIfNoLogSettingInServerSettings() {
    context = EventPluginContext.from(Event.builder().type(EventType.LOG.value()).build());
    doReturn(InMemoryStorage.builder().build()).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelLogEventIfThereIsNoLogLevel() {
    context =
        EventPluginContext.from(Event.builder().type(EventType.LOG.value()).source("test").build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(ServerSettings.builder().settings(Map.of("@@log:test", "trace")).build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelLogEventIfLogPriorityIsNotIdentifiedForServerSettings() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.LOG.value())
                .source("test")
                .property(EventPropertyKey.LOG_LEVEL.value(), "trace")
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(ServerSettings.builder().settings(Map.of("@@log:test", "xxx")).build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelLogEventIfLogPriorityIsNotIdentifiedForLogLevel() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.LOG.value())
                .source("test")
                .property(EventPropertyKey.LOG_LEVEL.value(), "xxx")
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(ServerSettings.builder().settings(Map.of("@@log:test", "trace")).build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelIfLogLevelIsMoreThanLevelSetInServerSettings() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.LOG.value())
                .source("test")
                .property(EventPropertyKey.LOG_LEVEL.value(), "info")
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(ServerSettings.builder().settings(Map.of("@@log:test", "trace")).build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelIfLogLevelIsEqualToLevelSetInServerSettings() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.LOG.value())
                .source("test")
                .property(EventPropertyKey.LOG_LEVEL.value(), "info")
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(ServerSettings.builder().settings(Map.of("@@log:test", "info")).build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldCancelIfLogLevelIsLessThanLevelSetInServerSettings() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.LOG.value())
                .source("test")
                .property(EventPropertyKey.LOG_LEVEL.value(), "trace")
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(ServerSettings.builder().settings(Map.of("@@log:test", "info")).build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isTrue();
  }

  @Test
  public void itShouldNotCancelErrorEventIfErrorIsNotSet() {
    context = EventPluginContext.from(Event.builder().type(EventType.ERROR.value()).build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelErrorEventIfServerSettingsIsNotSet() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.ERROR.value())
                .property(EventPropertyKey.ERROR.value(), Error.builder().build())
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelErrorEventIfServerSettingIsSetToTrue() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.ERROR.value())
                .property(
                    EventPropertyKey.ERROR.value(), Error.builder().type("test-error").build())
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    doReturn(storage).when(storageProvider).getSettings();
    storage.save(ServerSettings.builder().settings(Map.of("@@error:test-error", "true")).build());
    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldCancelErrorEventIfServerSettingIsSetToFalse() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.ERROR.value())
                .property(
                    EventPropertyKey.ERROR.value(), Error.builder().type("test-error").build())
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    doReturn(storage).when(storageProvider).getSettings();
    storage.save(ServerSettings.builder().settings(Map.of("@@error:test-error", "false")).build());
    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isTrue();
  }

  @Test
  public void itShouldNotCancelErrorEventIfServerSettingIsSetToTrueForInnerError() {
    context =
        EventPluginContext.from(
            Event.builder()
                .type(EventType.ERROR.value())
                .property(
                    EventPropertyKey.ERROR.value(),
                    Error.builder()
                        .type("test-error")
                        .inner(Error.builder().type("test-inner-error").build())
                        .build())
                .build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    doReturn(storage).when(storageProvider).getSettings();
    storage.save(
        ServerSettings.builder().settings(Map.of("@@error:test-inner-error", "false")).build());
    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isTrue();
  }

  @Test
  public void itShouldNotCancelEventOtherThanLogAndErrorIfServerSettingsIsNotPresent() {
    context =
        EventPluginContext.from(
            Event.builder().type(EventType.USAGE.value()).source("test").build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelEventOtherThanLogAndErrorIfServerSettingsIsTrue() {
    context =
        EventPluginContext.from(
            Event.builder().type(EventType.USAGE.value()).source("test").build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(ServerSettings.builder().settings(Map.of("@@usage:test", "true")).build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isFalse();
  }

  @Test
  public void itShouldNotCancelEventOtherThanLogAndErrorIfServerSettingsIsFalse() {
    context =
        EventPluginContext.from(
            Event.builder().type(EventType.USAGE.value()).source("test").build());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(ServerSettings.builder().settings(Map.of("@@usage:test", "false")).build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getContext().isEventCancelled()).isTrue();
  }
}
