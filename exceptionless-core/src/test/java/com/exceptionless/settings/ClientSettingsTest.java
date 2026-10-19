package com.exceptionless.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.LogLevel;
import com.exceptionless.internal.Strings;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClientSettingsTest {
  @Test
  void keysAreCaseInsensitive() {
    ClientSettings settings = ClientSettings.of(Map.of("@@DataExclusions", "a,b"));
    assertThat(settings.getString("@@dataexclusions")).isEqualTo("a,b");
  }

  @Test
  void overridesWin() {
    ClientSettings settings =
        ClientSettings.of(Map.of("k", "local", "only", "x"), Map.of("K", "server"));
    assertThat(settings.getString("k")).isEqualTo("server");
    assertThat(settings.getString("only")).isEqualTo("x");
  }

  @ParameterizedTest
  @CsvSource({
    "true,true",
    "TRUE,true",
    "yes,true",
    "1,true",
    "false,false",
    "No,false",
    "0,false",
    "junk,"
  })
  void parsesBooleans(String value, Boolean expected) {
    ClientSettings settings = ClientSettings.of(Map.of("k", value));
    if (expected == null) {
      assertThat(settings.getBoolean("k", true)).isTrue();
      assertThat(settings.getBoolean("k", false)).isFalse();
    } else {
      assertThat(settings.getBoolean("k", !expected)).isEqualTo(expected);
    }
  }

  @Test
  void splitsLists() {
    assertThat(Strings.splitList(" *password* , ;secret\n\r token ,"))
        .containsExactly("*password*", "secret", "token");
    assertThat(Strings.splitList(null)).isEmpty();
  }

  @Test
  void minLogLevelUsesExactSourceFirst() {
    ClientSettings settings =
        ClientSettings.of(
            Map.of(
                "@@log:*",
                "error",
                "@@log:com.example.*",
                "info",
                "@@log:com.example.Noisy",
                "off"));
    assertThat(settings.getMinLogLevel("com.example.Noisy")).isEqualTo(LogLevel.OFF);
    assertThat(settings.getMinLogLevel("com.example.Service")).isEqualTo(LogLevel.INFO);
    assertThat(settings.getMinLogLevel("org.other.Thing")).isEqualTo(LogLevel.ERROR);
  }

  @Test
  void longestWildcardWins() {
    ClientSettings settings =
        ClientSettings.of(
            Map.of(
                "@@log:com.*",
                "warn",
                "@@log:com.example.api.*",
                "trace",
                "@@log:com.example.*",
                "info"));
    assertThat(settings.getMinLogLevel("com.example.api.Users")).isEqualTo(LogLevel.TRACE);
    assertThat(settings.getMinLogLevel("com.example.db.Pool")).isEqualTo(LogLevel.INFO);
    assertThat(settings.getMinLogLevel("com.zzz")).isEqualTo(LogLevel.WARN);
  }

  @Test
  void minLogLevelIsNullWhenNothingMatches() {
    ClientSettings settings = ClientSettings.of(Map.of("@@log:com.example.*", "info"));
    assertThat(settings.getMinLogLevel("org.other")).isNull();
    assertThat(settings.getMinLogLevel("org.other")).isNull(); // cached
    assertThat(ClientSettings.empty().getMinLogLevel(null)).isNull();
  }

  @Test
  void blankSourceUsesGlobalSetting() {
    ClientSettings settings = ClientSettings.of(Map.of("@@log:*", "Warn"));
    assertThat(settings.getMinLogLevel(null)).isEqualTo(LogLevel.WARN);
    assertThat(settings.getMinLogLevel("")).isEqualTo(LogLevel.WARN);
  }

  @Test
  void logSettingKeysAreCaseInsensitive() {
    ClientSettings settings = ClientSettings.of(Map.of("@@LOG:COM.EXAMPLE.*", "Info"));
    assertThat(settings.getMinLogLevel("com.example.Foo")).isEqualTo(LogLevel.INFO);
  }

  @Test
  void typeAndSourceSettings() {
    ClientSettings settings =
        ClientSettings.of(
            Map.of(
                "@@error:java.io.IOException", "false",
                "@@error:org.hibernate.*", "false",
                "@@usage:Export", "off",
                "@@404:*", "true"));
    assertThat(settings.getTypeAndSourceSetting("error", "java.io.IOException", true)).isFalse();
    assertThat(
            settings.getTypeAndSourceSetting(
                "error", "org.hibernate.LazyInitializationException", true))
        .isFalse();
    assertThat(settings.getTypeAndSourceSetting("error", "java.lang.IllegalStateException", true))
        .isTrue();
    assertThat(settings.getTypeAndSourceSetting("usage", "Export", true)).isFalse();
    assertThat(settings.getTypeAndSourceSetting("usage", "Import", true)).isTrue();
    assertThat(settings.getTypeAndSourceSetting("404", "/missing", false)).isTrue();
    assertThat(settings.getTypeAndSourceSetting("error", null, true)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "trace,TRACE",
    "Debug,DEBUG",
    "info,INFO",
    "Information,INFO",
    "warn,WARN",
    "Warning,WARN",
    "error,ERROR",
    "fatal,FATAL",
    "critical,FATAL",
    "off,OFF",
    "false,OFF",
    "true,TRACE",
    "severe,ERROR",
    "fine,DEBUG",
    "finest,TRACE"
  })
  void parsesLevels(String value, LogLevel expected) {
    assertThat(LogLevel.parse(value)).isEqualTo(expected);
  }

  @Test
  void unknownLevelsAreNull() {
    assertThat(LogLevel.parse("bogus")).isNull();
    assertThat(LogLevel.parse(null)).isNull();
  }

  @Test
  void levelComparison() {
    assertThat(LogLevel.ERROR.isAtLeast(LogLevel.WARN)).isTrue();
    assertThat(LogLevel.WARN.isAtLeast(LogLevel.WARN)).isTrue();
    assertThat(LogLevel.INFO.isAtLeast(LogLevel.WARN)).isFalse();
    assertThat(LogLevel.OFF.isAtLeast(LogLevel.TRACE)).isFalse();
    assertThat(LogLevel.FATAL.isAtLeast(LogLevel.OFF)).isFalse();
  }
}
