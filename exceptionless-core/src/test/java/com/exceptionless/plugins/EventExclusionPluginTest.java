package com.exceptionless.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import com.exceptionless.TestClients;
import com.exceptionless.internal.ErrorConverter;
import com.exceptionless.models.Event;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EventExclusionPluginTest {
  private final ExceptionlessConfiguration config = TestClients.config();
  private final ExceptionlessClient client = PluginTestSupport.client(config);
  private final EventExclusionPlugin plugin = new EventExclusionPlugin();

  @AfterEach
  void close() {
    client.close();
  }

  private boolean cancelled(Event event) {
    return PluginTestSupport.run(plugin, client, event).isCancelled();
  }

  private static Event log(String source, String level) {
    return new Event().setType("log").setSource(source).setLevel(level);
  }

  @ParameterizedTest
  @CsvSource({
    "trace, info, true",
    "debug, info, true",
    "info, info, false",
    "warn, info, false",
    "error, info, false",
    "fatal, error, false",
    "error, off, true",
    "Error, Warn, false",
    "warning, error, true",
  })
  void filtersLogsByServerLevel(String level, String minimum, boolean excluded) {
    config.applyServerSettings(Map.of("@@log:com.example.*", minimum));
    assertThat(cancelled(log("com.example.Service", level))).isEqualTo(excluded);
  }

  @Test
  void logsWithoutLevelOrMatchingSettingAreKept() {
    config.applyServerSettings(Map.of("@@log:com.example.*", "error"));
    assertThat(cancelled(new Event().setType("log").setSource("com.example.X"))).isFalse();
    assertThat(cancelled(log("org.other", "trace"))).isFalse();
  }

  @Test
  void defaultMinLogLevelAppliesWhenNoSettingMatches() {
    config.defaultMinLogLevel(LogLevel.WARN);
    assertThat(cancelled(log("x", "info"))).isTrue();
    assertThat(cancelled(log("x", "warn"))).isFalse();
    config.applyServerSettings(Map.of("@@log:x", "debug"));
    assertThat(cancelled(log("x", "info"))).isFalse();
  }

  @Test
  void excludesErrorTypesAcrossTheCauseChain() {
    config.applyServerSettings(Map.of("@@error:java.io.FileNotFoundException", "false"));
    Event event = new Event().setType("error");
    event.setError(
        ErrorConverter.convert(
            new IllegalStateException("x", new FileNotFoundException("f")), n -> false, List.of()));
    assertThat(cancelled(event)).isTrue();

    Event other = new Event().setType("error");
    other.setError(ErrorConverter.convert(new IllegalStateException("x"), n -> false, List.of()));
    assertThat(cancelled(other)).isFalse();
  }

  @Test
  void excludesErrorTypesByWildcard() {
    config.applyServerSettings(Map.of("@@error:org.springframework.web.*", "false"));
    Event event = new Event().setType("error");
    com.exceptionless.models.ErrorInfo error =
        new com.exceptionless.models.ErrorInfo()
            .setType("org.springframework.web.HttpMediaTypeNotSupportedException");
    event.setError(error);
    assertThat(cancelled(event)).isTrue();
  }

  @Test
  void excludesTypesAndSources() {
    config.applyServerSettings(
        Map.of("@@usage:Export", "false", "@@404:/favicon.ico", "false", "@@404:/wp-*", "false"));
    assertThat(cancelled(new Event().setType("usage").setSource("Export"))).isTrue();
    assertThat(cancelled(new Event().setType("usage").setSource("Import"))).isFalse();
    assertThat(cancelled(new Event().setType("404").setSource("/favicon.ico"))).isTrue();
    assertThat(cancelled(new Event().setType("404").setSource("/wp-admin"))).isTrue();
    assertThat(cancelled(new Event().setType("404").setSource("/api"))).isFalse();
  }

  @Test
  void customExclusions() {
    config.addEventExclusion(e -> "secret".equals(e.getMessage()));
    config.addEventExclusion(
        e -> {
          throw new IllegalStateException("failing callbacks are ignored");
        });
    assertThat(cancelled(new Event().setType("log").setMessage("secret"))).isTrue();
    assertThat(cancelled(new Event().setType("log").setMessage("public"))).isFalse();
  }

  @Test
  void nullTypeIsSafe() {
    assertThat(cancelled(new Event())).isFalse();
  }
}
