package com.exceptionless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExceptionlessConfigurationTest {
  @Test
  void defaultsMatchTheHostedService() {
    ExceptionlessConfiguration config = new ExceptionlessConfiguration();
    assertThat(config.getServerUrl()).isEqualTo("https://collector.exceptionless.io");
    assertThat(config.getConfigServerUrl()).isEqualTo("https://config.exceptionless.io");
    assertThat(config.getHeartbeatServerUrl()).isEqualTo("https://heartbeat.exceptionless.io");
    assertThat(config.isIncludePrivateInformation()).isTrue();
    assertThat(config.isReferenceIdsEnabled()).isTrue();
    assertThat(config.getSubmissionBatchSize()).isEqualTo(50);
    assertThat(config.getUpdateSettingsWhenIdleInterval()).isEqualTo(Duration.ofMinutes(2));
    assertThat(config.isEnabled()).isFalse(); // no API key
  }

  @Test
  void serverUrlSetsAllUrlsAndIsNormalized() {
    ExceptionlessConfiguration config =
        new ExceptionlessConfiguration().serverUrl(" https://ex.example.com/// ");
    assertThat(config.getServerUrl()).isEqualTo("https://ex.example.com");
    assertThat(config.getConfigServerUrl()).isEqualTo("https://ex.example.com");
    assertThat(config.getHeartbeatServerUrl()).isEqualTo("https://ex.example.com");
    config.heartbeatServerUrl("https://hb.example.com");
    assertThat(config.getHeartbeatServerUrl()).isEqualTo("https://hb.example.com");
    assertThatThrownBy(() -> config.serverUrl("ftp://x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> config.serverUrl(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"API_KEY_HERE", "short", "has spaces in it"})
  void invalidApiKeysDisableTheClient(String key) {
    ExceptionlessConfiguration config = new ExceptionlessConfiguration().apiKey(key);
    assertThat(config.validate()).isNotEmpty();
    assertThat(config.isEnabled()).isFalse();
  }

  @Test
  void validApiKeyEnables() {
    ExceptionlessConfiguration config =
        new ExceptionlessConfiguration().apiKey(" " + TestClients.API_KEY + " ");
    assertThat(config.getApiKey()).isEqualTo(TestClients.API_KEY);
    assertThat(config.isEnabled()).isTrue();
    assertThat(config.enabled(false).isEnabled()).isFalse();
  }

  @Test
  void connectionSettingsLockAfterStartup() {
    ExceptionlessConfiguration config = TestClients.config();
    try (ExceptionlessClient client = new ExceptionlessClient(config).startup()) {
      assertThatThrownBy(() -> config.apiKey("other-key-123456"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("apiKey");
      assertThatThrownBy(() -> config.serverUrl("https://x"))
          .isInstanceOf(IllegalStateException.class);
      // Everything else can still change.
      config.addDefaultTags("later").version("2.0").includeCookies(false);
      assertThat(client.getConfiguration().getDefaultTags()).contains("later");
    }
  }

  @Test
  void privacyMasterSwitchAndIndividualToggles() {
    ExceptionlessConfiguration config =
        new ExceptionlessConfiguration().includePrivateInformation(false);
    assertThat(config.isIncludeCookies()).isFalse();
    assertThat(config.isIncludeHeaders()).isFalse();
    assertThat(config.isIncludeMachineName()).isFalse();
    config.includeHeaders(true);
    assertThat(config.isIncludeHeaders()).isTrue();
    assertThat(config.isIncludeCookies()).isFalse();
  }

  @Test
  void intervalsAreClamped() {
    ExceptionlessConfiguration config = new ExceptionlessConfiguration();
    assertThat(
            config
                .updateSettingsWhenIdleInterval(Duration.ofSeconds(5))
                .getUpdateSettingsWhenIdleInterval())
        .isEqualTo(Duration.ofMinutes(2));
    assertThat(
            config
                .updateSettingsWhenIdleInterval(Duration.ZERO)
                .getUpdateSettingsWhenIdleInterval())
        .isEqualTo(Duration.ZERO);
    assertThat(config.useSessions(true, Duration.ofSeconds(1), false).getHeartbeatInterval())
        .isEqualTo(Duration.ofSeconds(30));
    assertThatThrownBy(() -> config.submissionBatchSize(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void useSessionsAddsPlugins() {
    ExceptionlessConfiguration config =
        new ExceptionlessConfiguration().useSessions(true, Duration.ofMinutes(1), true);
    assertThat(config.isSessionsEnabled()).isTrue();
    assertThat(config.hasPlugin("HeartbeatPlugin")).isTrue();
    assertThat(config.hasPlugin("SessionIdManagementPlugin")).isTrue();
  }

  @Test
  void readsValuesFromMaps() {
    ExceptionlessConfiguration config =
        new ExceptionlessConfiguration()
            .readFromValues(
                Map.of(
                    "EXCEPTIONLESS_API_KEY", TestClients.API_KEY,
                    "EXCEPTIONLESS_SERVER_URL", "https://self.hosted/",
                    "EXCEPTIONLESS_VERSION", "3.1.4",
                    "EXCEPTIONLESS_DEFAULT_TAGS", "a, b",
                    "EXCEPTIONLESS_INCLUDE_PRIVATE_INFORMATION", "false",
                    "EXCEPTIONLESS_STORAGE_PATH", "/tmp/ex",
                    "EXCEPTIONLESS_SETTINGS_FEATURE_X", "on"),
                ExceptionlessConfiguration::environmentName);
    assertThat(config.getApiKey()).isEqualTo(TestClients.API_KEY);
    assertThat(config.getServerUrl()).isEqualTo("https://self.hosted");
    assertThat(config.getVersion()).isEqualTo("3.1.4");
    assertThat(config.getDefaultTags()).containsExactlyInAnyOrder("a", "b");
    assertThat(config.isIncludePrivateInformation()).isFalse();
    assertThat(config.getStoragePath()).isEqualTo(Path.of("/tmp/ex"));
    assertThat(config.getSettings().getString("FEATURE_X")).isEqualTo("on");
  }

  @ParameterizedTest
  @CsvSource({
    "apiKey, EXCEPTIONLESS_API_KEY",
    "serverUrl, EXCEPTIONLESS_SERVER_URL",
    "settings., EXCEPTIONLESS_SETTINGS_",
    "enabled, EXCEPTIONLESS_ENABLED"
  })
  void environmentNames(String key, String expected) {
    assertThat(ExceptionlessConfiguration.environmentName(key)).isEqualTo(expected);
  }

  @Test
  void readsSystemPropertiesAndClasspathProperties() {
    // src/test/resources/exceptionless.properties sets version=from-file and defaultTags=file-tag
    System.setProperty("exceptionless.version", "from-system-property");
    try {
      ExceptionlessConfiguration config = ExceptionlessConfiguration.fromEnvironment();
      assertThat(config.getVersion()).isEqualTo("from-system-property");
      assertThat(config.getDefaultTags()).contains("file-tag");
      assertThat(config.getSettings().getString("@@log:*")).isEqualTo("warn");
    } finally {
      System.clearProperty("exceptionless.version");
    }
  }

  @Test
  void dataExclusionsCombineLocalAndServer() {
    ExceptionlessConfiguration config =
        new ExceptionlessConfiguration().addDataExclusions("*password*", " ");
    config.applyServerSettings(Map.of("@@DataExclusions", "*secret*,ssn"));
    assertThat(config.getDataExclusions()).containsExactly("*password*", "*secret*", "ssn");
  }

  @Test
  void isLogLevelEnabled() {
    ExceptionlessConfiguration config = new ExceptionlessConfiguration();
    assertThat(config.isLogLevelEnabled("x", LogLevel.INFO, LogLevel.WARN)).isFalse();
    assertThat(config.isLogLevelEnabled("x", LogLevel.INFO, null)).isTrue();
    config.applyServerSettings(Map.of("@@log:x", "info"));
    assertThat(config.isLogLevelEnabled("x", LogLevel.INFO, LogLevel.WARN)).isTrue();
    config.applyServerSettings(Map.of("@@log:*", "off"));
    assertThat(config.isLogLevelEnabled("x", LogLevel.FATAL, null)).isFalse();
  }

  @Test
  void userIdentityAddsDefaultUser() {
    ExceptionlessConfiguration config =
        new ExceptionlessConfiguration().userIdentity("id-1", "Ann");
    assertThat(config.getDefaultData().get("@user"))
        .isEqualTo(new com.exceptionless.models.UserInfo("id-1", "Ann"));
    config.userIdentity(null);
    assertThat(config.getDefaultData()).doesNotContainKey("@user");
  }
}
