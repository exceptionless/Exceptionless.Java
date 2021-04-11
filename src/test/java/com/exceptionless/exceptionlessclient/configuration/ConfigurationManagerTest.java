package com.exceptionless.exceptionlessclient.configuration;

import com.exceptionless.exceptionlessclient.TestFixtures;
import com.exceptionless.exceptionlessclient.exceptions.InvalidApiKeyException;
import com.exceptionless.exceptionlessclient.logging.LogCapturerIF;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.UserInfo;
import com.exceptionless.exceptionlessclient.models.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.models.settings.ServerSettings;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorage;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import com.exceptionless.exceptionlessclient.submission.DefaultSubmissionClient;
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
public class ConfigurationManagerTest {
  private static final Logger LOG = LoggerFactory.getLogger(ConfigurationManagerTest.class);

  @Mock private InMemoryStorageProvider storageProvider;
  @Mock private DefaultSubmissionClient submissionClient;
  @Mock private Consumer<ConfigurationManager> handler;
  @Mock private LogCapturerIF logCapturer;
  private ConfigurationManager configurationManager;
  private InMemoryStorage<ServerSettings> storage;

  @BeforeEach
  public void setup() {
    storage = InMemoryStorage.<ServerSettings>builder().build();
    configurationManager =
        TestFixtures.aDefaultConfigurationManager()
            .storageProvider(storageProvider)
            .submissionClient(submissionClient)
            .build();
  }

  @Test
  public void itThrowsInvalidApiKeyExceptionForInvalidApiKeys() {
    assertThatThrownBy(
            () ->
                TestFixtures.aDefaultConfigurationManager()
                    .configuration(Configuration.builder().apiKey("xxx").build())
                    .build())
        .isInstanceOf(InvalidApiKeyException.class)
        .hasMessage("Apikey is not valid: [xxx]");
  }

  @Test
  public void itCanAddALogCapturer() {
    configurationManager =
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
    configurationManager.addDefaultTags("tag1", "tag2");

    assertThat(configurationManager.getDefaultTags()).isEqualTo(Set.of("tag1", "tag2"));
  }

  @Test
  public void itCanAddDataExclusions() {
    doReturn(storage).when(storageProvider).getSettings();
    storage.save(
        ServerSettings.builder()
            .version(1L)
            .settings(Map.of("@@DataExclusions", "exclusion1,exclusion2"))
            .build());

    configurationManager.addDataExclusions("exclusion3", "exclusion4");

    assertThat(configurationManager.getDataExclusions())
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

    configurationManager.addUserAgentBotPatterns("pattern3", "pattern4");

    assertThat(configurationManager.getUserAgentBotPatterns())
        .isEqualTo(Set.of("pattern1", "pattern2", "pattern3", "pattern4"));
  }

  @Test
  public void itCanSubmitSessionHeartBeat() {
    configurationManager.submitSessionHeartbeat("test-user-id");

    verify(submissionClient, times(1)).sendHeartBeat("test-user-id", false);
  }

  @Test
  public void itCanAddAndRemovePlugins() {
    configurationManager.addPlugin(
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
              EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {}
        });

    assertThat(
            configurationManager.getPlugins().stream()
                .anyMatch(plugin -> plugin.getName().equals("testPlugin")))
        .isTrue();

    configurationManager.removePlugin("testPlugin");

    assertThat(
            configurationManager.getPlugins().stream()
                .anyMatch(plugin -> plugin.getName().equals("testPlugin")))
        .isFalse();
  }

  @Test
  public void itCanAddVersionInDefaultData() {
    configurationManager.setVersion("123");

    assertThat(configurationManager.getDefaultData())
        .isEqualTo(Map.of(EventPropertyKey.VERSION.value(), "123"));
  }

  @Test
  public void itCanAddAndRemoveUserIdentityInDefaultData() {
    UserInfo userInfo = UserInfo.builder().identity("test-identity").name("test-name").build();

    configurationManager.setUserIdentity(userInfo);

    assertThat(configurationManager.getDefaultData())
        .isEqualTo(Map.of(EventPropertyKey.USER.value(), userInfo));

    configurationManager.removeUserIdentity();

    assertThat(configurationManager.getDefaultData()).isEqualTo(Map.of());
  }

  @Test
  public void itCanUseSessions() {
    configurationManager.useSessions();
    List<EventPluginIF> plugins =
        configurationManager.getPlugins().stream()
            .filter(plugin -> plugin.getName().contains("HeartbeatPlugin"))
            .collect(Collectors.toList());

    assertThat(plugins).hasSize(1);
  }

  @Test
  public void itCanDetectChanges() {
    configurationManager.onChanged(handler);
    configurationManager.getConfiguration().setApiKey("test-api-key");

    verify(handler, times(1)).accept(configurationManager);
  }
}
