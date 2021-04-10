package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.PluginContext;
import com.exceptionless.exceptionlessclient.models.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.models.enums.ServerSettingKey;
import com.exceptionless.exceptionlessclient.models.services.RequestInfo;
import com.exceptionless.exceptionlessclient.models.settings.ServerSettings;
import com.exceptionless.exceptionlessclient.plugins.preconfigured.RequestInfoPlugin;
import com.exceptionless.exceptionlessclient.services.DefaultRequestInfoCollector;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorage;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RequestInfoPluginTest {
  @Mock private DefaultRequestInfoCollector requestInfoCollector;
  @Mock private InMemoryStorageProvider storageProvider;
  @Mock private HttpRequest httpRequest;
  ConfigurationManager configurationManager;
  private RequestInfoPlugin plugin;
  private EventPluginContext context;

  @BeforeEach
  public void setup() {
    plugin = RequestInfoPlugin.builder().build();
    configurationManager =
        TestFixtures.aDefaultConfigurationManager()
            .storageProvider(storageProvider)
            .requestInfoCollector(requestInfoCollector)
            .build();
  }

  @Test
  public void itShouldNotDoAnythingIfRequestInfoIsPresentInEvent() {
    RequestInfo requestInfo = RequestInfo.builder().userAgent("test-agent").build();
    context =
        EventPluginContext.from(
            Event.builder().property(EventPropertyKey.REQUEST_INFO.value(), requestInfo).build());

    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getRequestInfo()).isPresent();
    assertThat(context.getEvent().getRequestInfo().get()).isEqualTo(requestInfo);

    verifyZeroInteractions(requestInfoCollector);
  }

  @Test
  public void itShouldNotDoAnythingIfRequestInfoIsNotPresentInEventButAbsentFromContext() {
    context = EventPluginContext.from(Event.builder().build());

    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getRequestInfo()).isNotPresent();
    verifyZeroInteractions(requestInfoCollector);
  }

  @Test
  public void itShouldCancelEventIfUserAgentIsABotPattern() {
    context =
        EventPluginContext.builder()
            .event(Event.builder().build())
            .context(PluginContext.builder().request(httpRequest).build())
            .build();

    RequestInfo requestInfo = RequestInfo.builder().userAgent("test-agent").build();
    doReturn(requestInfo).when(requestInfoCollector).getRequestInfo(eq(httpRequest), any());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    storage.save(
        ServerSettings.builder()
            .settings(Map.of(ServerSettingKey.USER_AGENT_BOT_PATTERNS.value(), "test-agent"))
            .build());
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getRequestInfo()).isNotPresent();
    verify(requestInfoCollector, times(1)).getRequestInfo(eq(httpRequest), any());
    assertThat(context.getContext().isEventCancelled()).isTrue();
  }

  @Test
  public void itShouldAddRequestInfoToEvent() {
    context =
        EventPluginContext.builder()
            .event(Event.builder().build())
            .context(PluginContext.builder().request(httpRequest).build())
            .build();

    RequestInfo requestInfo = RequestInfo.builder().userAgent("test-agent").build();
    doReturn(requestInfo).when(requestInfoCollector).getRequestInfo(eq(httpRequest), any());

    InMemoryStorage<ServerSettings> storage = InMemoryStorage.<ServerSettings>builder().build();
    doReturn(storage).when(storageProvider).getSettings();

    plugin.run(context, configurationManager);

    assertThat(context.getEvent().getRequestInfo()).isPresent();
    assertThat(context.getEvent().getRequestInfo().get()).isEqualTo(requestInfo);
  }
}
