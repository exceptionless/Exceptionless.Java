package com.exceptionless.exceptionlessclient.settings;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerSettingsTest {
  @Test
  public void itCanGetTypeAndSourceSetting() {
    ServerSettings serverSettings =
        ServerSettings.builder()
            .settings(Map.of("@@test-type:test-source", "test-setting"))
            .build();

    Optional<String> maybeSetting =
        serverSettings.getTypeAndSourceSetting("test-type", "test-source");

    assertThat(maybeSetting).isPresent();
    assertThat(maybeSetting.get()).isEqualTo("test-setting");
  }

  @Test
  public void itCanGetNearestMatchForTypeAndSourceSettingFromTheLongestSetting() {
    ServerSettings serverSettings =
        ServerSettings.builder()
            .settings(Map.of("@@test-type:test-source.*", "test-setting"))
            .build();

    Optional<String> maybeSetting =
        serverSettings.getTypeAndSourceSetting("test-type", "test-source");

    assertThat(maybeSetting).isPresent();
    assertThat(maybeSetting.get()).isEqualTo("test-setting");

    serverSettings =
        ServerSettings.builder()
            .settings(Map.of("@@test-type:.*source*", "test-setting-2"))
            .build();

    maybeSetting = serverSettings.getTypeAndSourceSetting("test-type", "test-source");

    assertThat(maybeSetting).isPresent();
    assertThat(maybeSetting.get()).isEqualTo("test-setting-2");

    serverSettings =
        ServerSettings.builder()
            .settings(
                Map.of(
                    "@@test-type:test-source.*",
                    "test-setting",
                    "@@test-type:.*source*",
                    "test-setting-2"))
            .build();

    maybeSetting = serverSettings.getTypeAndSourceSetting("test-type", "test-source");

    assertThat(maybeSetting).isPresent();
    assertThat(maybeSetting.get()).isEqualTo("test-setting");
  }

  @Test
  public void itCanGetNearestMatchForTypeAndSettingForAlphabeticallySmallerSetting() {
    ServerSettings serverSettings =
        ServerSettings.builder().settings(Map.of("@@test-type:test.*", "test-setting")).build();

    Optional<String> maybeSetting =
        serverSettings.getTypeAndSourceSetting("test-type", "test-source");

    assertThat(maybeSetting).isPresent();
    assertThat(maybeSetting.get()).isEqualTo("test-setting");

    serverSettings =
        ServerSettings.builder().settings(Map.of("@@test-type:.*so.*", "test-setting-2")).build();

    maybeSetting = serverSettings.getTypeAndSourceSetting("test-type", "test-source");

    assertThat(maybeSetting).isPresent();
    assertThat(maybeSetting.get()).isEqualTo("test-setting-2");

    serverSettings =
        ServerSettings.builder()
            .settings(
                Map.of(
                    "@@test-type:test.*", "test-setting", "@@test-type:.*so.*", "test-setting-2"))
            .build();

    maybeSetting = serverSettings.getTypeAndSourceSetting("test-type", "test-source");

    assertThat(maybeSetting).isPresent();
    // '.'(46) is alphabetically smaller that 't'(116)
    assertThat(maybeSetting.get()).isEqualTo("test-setting-2");
  }

  @Test
  public void itCanGetDataExclusionsIfEmpty() {
    ServerSettings serverSettings = ServerSettings.builder().settings(Map.of()).build();

    assertThat(serverSettings.getDataExclusions()).isEmpty();
  }

  @Test
  public void itCanGetDataExclusions() {
    ServerSettings serverSettings =
        ServerSettings.builder()
            .settings(Map.of("@@DataExclusions", "exclusion1,exclusion2"))
            .build();

    assertThat(serverSettings.getDataExclusions()).isEqualTo(Set.of("exclusion1", "exclusion2"));
  }

  @Test
  public void itCanGetUserAgentBotPatterns() {
    ServerSettings serverSettings =
        ServerSettings.builder()
            .settings(Map.of("@@UserAgentBotPatterns", "pattern1,pattern2"))
            .build();

    assertThat(serverSettings.getUserAgentBotPatterns()).isEqualTo(Set.of("pattern1", "pattern2"));
  }

  @Test
  public void itCanGetUserAgentBotPatternsIfEmpty() {
    ServerSettings serverSettings = ServerSettings.builder().settings(Map.of()).build();

    assertThat(serverSettings.getUserAgentBotPatterns()).isEmpty();
  }

  @Test
  public void itCanGetAsBoolean() {
    assertThat(ServerSettings.getAsBoolean("true")).isTrue();
    assertThat(ServerSettings.getAsBoolean("yes")).isTrue();
    assertThat(ServerSettings.getAsBoolean("1")).isTrue();
    assertThat(ServerSettings.getAsBoolean("xxx")).isFalse();
  }
}
