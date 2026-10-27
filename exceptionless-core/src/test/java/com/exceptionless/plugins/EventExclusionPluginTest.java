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
import org.jspecify.annotations.Nullable;
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

  private static Event log(String source, @Nullable String level) {
    Event event = event("log", source);
    event.setLevel(level);
    return event;
  }

  private static Event event(String type, @Nullable String source) {
    Event event = new Event();
    event.setType(type);
    event.setSource(source);
    return event;
  }

  private static Event message(String message) {
    Event event = event("log", null);
    event.setMessage(message);
    return event;
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
    assertThat(cancelled(log("com.example.X", null))).isFalse();
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
    Event event = event("error", null);
    event.setError(
        ErrorConverter.convert(
            new IllegalStateException("x", new FileNotFoundException("f")), n -> false, List.of()));
    assertThat(cancelled(event)).isTrue();

    Event other = event("error", null);
    other.setError(ErrorConverter.convert(new IllegalStateException("x"), n -> false, List.of()));
    assertThat(cancelled(other)).isFalse();
  }

  @Test
  void excludesErrorTypesByWildcard() {
    config.applyServerSettings(Map.of("@@error:org.springframework.web.*", "false"));
    Event event = event("error", null);
    com.exceptionless.models.ErrorInfo error = new com.exceptionless.models.ErrorInfo();
    error.setType("org.springframework.web.HttpMediaTypeNotSupportedException");
    event.setError(error);
    assertThat(cancelled(event)).isTrue();
  }

  @Test
  void excludesTypesAndSources() {
    config.applyServerSettings(
        Map.of("@@usage:Export", "false", "@@404:/favicon.ico", "false", "@@404:/wp-*", "false"));
    assertThat(cancelled(event("usage", "Export"))).isTrue();
    assertThat(cancelled(event("usage", "Import"))).isFalse();
    assertThat(cancelled(event("404", "/favicon.ico"))).isTrue();
    assertThat(cancelled(event("404", "/wp-admin"))).isTrue();
    assertThat(cancelled(event("404", "/api"))).isFalse();
  }

  @Test
  void customExclusions() {
    config.addEventExclusion(e -> "secret".equals(e.getMessage()));
    config.addEventExclusion(
        e -> {
          throw new IllegalStateException("failing callbacks are ignored");
        });
    assertThat(cancelled(message("secret"))).isTrue();
    assertThat(cancelled(message("public"))).isFalse();
  }

  @Test
  void nullTypeIsSafe() {
    assertThat(cancelled(new Event())).isFalse();
  }
}
