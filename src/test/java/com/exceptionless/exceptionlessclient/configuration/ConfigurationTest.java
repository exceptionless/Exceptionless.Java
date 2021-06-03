package com.exceptionless.exceptionlessclient.configuration;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.exceptions.InvalidApiKeyException;
import com.exceptionless.exceptionlessclient.logging.LogCapturerIF;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.UserInfo;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.settings.ServerSettings;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorage;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import com.exceptionless.exceptionlessclient.submission.DefaultSubmissionClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConfigurationTest {
  private static final Logger LOG = LoggerFactory.getLogger(ConfigurationTest.class);

  @Mock private InMemoryStorageProvider storageProvider;
  @Mock private DefaultSubmissionClient submissionClient;
  @Mock private Consumer<Configuration> handler;
  @Mock private LogCapturerIF logCapturer;
  private Configuration configuration;
  private InMemoryStorage<ServerSettings> storage;

  @BeforeEach
  public void setup() {
    storage = InMemoryStorage.<ServerSettings>builder().build();
    configuration =
        TestFixtures.aDefaultConfigurationManager()
            .storageProvider(storageProvider)
            .submissionClient(submissionClient)
            .build();
  }

  @Test
  public void itThrowsInvalidApiKeyExceptionForInvalidApiKeys() {
    assertThatThrownBy(() -> TestFixtures.aDefaultConfigurationManager().apiKey("xxx").build())
        .isInstanceOf(InvalidApiKeyException.class)
        .hasMessage("Apikey is not valid: [xxx]");
  }

  @Test
  public void itCanAddALogCapturer() {
    configuration =
        TestFixtures.aDefaultConfigurationManager().logCatpurer(logCapturer).build();
    // trace is disabled by default
    LOG.debug("debug message");
    LOG.info("info message");
    LOG.warn("warn message");
    LOG.error("error message");
    Exception e = new RuntimeException("test");
    LOG.error("exception message", e);

    verify(logCapturer, times(1)).debug("debug message");
    verify(logCapturer, times(1)).info("info message");
    verify(logCapturer, times(1)).warn("warn message");
    verify(logCapturer, times(1)).error("error message");
    verify(logCapturer, times(1)).error("exception message", e);
  }

  @Test
  public void itCanAddDefaultTags() {
    configuration.addDefaultTags("tag1", "tag2");

    assertThat(configuration.getDefaultTags()).isEqualTo(Set.of("tag1", "tag2"));
  }

  @Test
  public void itCanAddDataExclusions() {
    doReturn(storage).when(storageProvider).getSettings();
    storage.save(
        ServerSettings.builder()
            .version(1L)
            .settings(Map.of("@@DataExclusions", "exclusion1,exclusion2"))
            .build());

    configuration.addDataExclusions("exclusion3", "exclusion4");

    assertThat(configuration.getDataExclusions())
        .isEqualTo(Set.of("exclusion1", "exclusion2", "exclusion3", "exclusion4"));
  }

  @Test
  public void itCanAddUserAgentBotPatterns() {
    doReturn(storage).when(storageProvider).getSettings();
    storage.save(
        ServerSettings.builder()
            .version(1L)
            .settings(Map.of("@@UserAgentBotPatterns", "pattern1,pattern2"))
            .build());

    configuration.addUserAgentBotPatterns("pattern3", "pattern4");

    assertThat(configuration.getUserAgentBotPatterns())
        .isEqualTo(Set.of("pattern1", "pattern2", "pattern3", "pattern4"));
  }

  @Test
  public void itCanSubmitSessionHeartBeat() {
    configuration.submitSessionHeartbeat("test-user-id");

    verify(submissionClient, times(1)).sendHeartBeat("test-user-id", false);
  }

  @Test
  public void itCanAddAndRemovePlugins() {
    configuration.addPlugin(
        new EventPluginIF() {
          @Override
          public int getPriority() {
            return Integer.MAX_VALUE;
          }

          @Override
          public String getName() {
            return "testPlugin";
          }

          @Override
          public void run(
              EventPluginContext eventPluginContext, Configuration configuration) {}
        });

    assertThat(
            configuration.getPlugins().stream()
                .anyMatch(plugin -> plugin.getName().equals("testPlugin")))
        .isTrue();

    configuration.removePlugin("testPlugin");

    assertThat(
            configuration.getPlugins().stream()
                .anyMatch(plugin -> plugin.getName().equals("testPlugin")))
        .isFalse();
  }

  @Test
  public void itCanAddVersionInDefaultData() {
    configuration.setVersion("123");

    assertThat(configuration.getDefaultData())
        .isEqualTo(Map.of(EventPropertyKey.VERSION.value(), "123"));
  }

  @Test
  public void itCanAddAndRemoveUserIdentityInDefaultData() {
    UserInfo userInfo = UserInfo.builder().identity("test-identity").name("test-name").build();

    configuration.setUserIdentity(userInfo);

    assertThat(configuration.getDefaultData())
        .isEqualTo(Map.of(EventPropertyKey.USER.value(), userInfo));

    configuration.removeUserIdentity();

    assertThat(configuration.getDefaultData()).isEqualTo(Map.of());
  }

  @Test
  public void itCanUseSessions() {
    configuration.useSessions();
    List<EventPluginIF> plugins =
        configuration.getPlugins().stream()
            .filter(plugin -> plugin.getName().contains("HeartbeatPlugin"))
            .collect(Collectors.toList());

    assertThat(plugins).hasSize(1);
  }

  @Test
  public void itCanDetectChanges() {
    configuration.onChanged(handler);
    configuration.setApiKey("test-api-key");

    verify(handler, times(1)).accept(configuration);
  }

  @Test
  public void itCanSetDefaultValueHeartBeatServerUrlToServerUrlIfAbsent() {
    Configuration configuration =
        Configuration.builder()
            .serverUrl("test-server-url")
            .apiKey("12345678abcdef")
            .build();

    Assertions.assertThat(configuration.getHeartbeatServerUrl().get())
        .isEqualTo("test-server-url");
  }

  @Test
  public void itCanSetDefaultValueConfigServerUrlToServerUrlIfAbsent() {
    Configuration configuration =
            Configuration.builder()
                    .serverUrl("test-server-url")
                    .apiKey("12345678abcdef")
                    .build();

    Assertions.assertThat(configuration.getConfigServerUrl().get())
            .isEqualTo("test-server-url");
  }
}
