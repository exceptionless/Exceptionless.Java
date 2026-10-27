package com.exceptionless.settings;

import com.exceptionless.LogLevel;
import com.exceptionless.internal.Strings;
import com.exceptionless.internal.Wildcard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * An immutable, case-insensitive view of the effective client configuration values: local settings
 * overlaid with the project's server settings.
 *
 * <p>Implements the {@code @@log:<source>}, {@code @@<type>:<source>} and {@code @@error:<type>}
 * conventions shared by all Exceptionless clients.
 */
public final class ClientSettings {
  public static final String DATA_EXCLUSIONS = "@@DataExclusions";
  public static final String INCLUDE_PRIVATE_INFORMATION = "@@IncludePrivateInformation";
  public static final String USER_AGENT_BOT_PATTERNS = "@@UserAgentBotPatterns";
  public static final String LOG_PREFIX = "@@log:";

  private static final ClientSettings EMPTY = new ClientSettings(Map.of());

  private final Map<String, String> values;
  // Wildcard-keyed @@type:source settings ordered longest first, so the most specific wins.
  private final List<Map.Entry<String, String>> wildcardEntries;
  private final Map<String, Optional<LogLevel>> minLogLevelCache = new ConcurrentHashMap<>();

  private ClientSettings(Map<String, String> values) {
    TreeMap<String, String> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    map.putAll(values);
    this.values = Collections.unmodifiableMap(map);
    List<Map.Entry<String, String>> wildcards = new ArrayList<>();
    for (Map.Entry<String, String> entry : map.entrySet()) {
      if (entry.getKey().startsWith("@@") && entry.getKey().contains("*")) {
        wildcards.add(Map.entry(entry.getKey(), entry.getValue()));
      }
    }
    wildcards.sort(
        Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed());
    this.wildcardEntries = List.copyOf(wildcards);
  }

  public static ClientSettings empty() {
    return EMPTY;
  }

  /** Creates settings from {@code base} overlaid with {@code overrides}. */
  public static ClientSettings of(Map<String, String> base, Map<String, String> overrides) {
    if (base.isEmpty() && overrides.isEmpty()) {
      return EMPTY;
    }
    TreeMap<String, String> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    merged.putAll(base);
    merged.putAll(overrides);
    return new ClientSettings(merged);
  }

  public static ClientSettings of(Map<String, String> values) {
    return values.isEmpty() ? EMPTY : new ClientSettings(values);
  }

  public Map<String, String> asMap() {
    return values;
  }

  public @Nullable String getString(String key) {
    return values.get(key);
  }

  public boolean getBoolean(String key, boolean defaultValue) {
    Boolean value = parseBoolean(values.get(key));
    return value != null ? value : defaultValue;
  }

  /** A comma, semicolon or newline separated list setting. */
  public List<String> getStringList(String key) {
    return Strings.splitList(values.get(key));
  }

  /**
   * The minimum log level for {@code source}: the {@code @@log:<source>} setting, otherwise the
   * most specific matching wildcard ({@code @@log:com.example.*}, {@code @@log:*}).
   *
   * @return the configured level or {@code null} when nothing matches
   */
  public @Nullable LogLevel getMinLogLevel(@Nullable String source) {
    String key = source == null || source.isBlank() ? "*" : source;
    if (minLogLevelCache.size() > 10_000) {
      minLogLevelCache.clear();
    }
    return minLogLevelCache
        .computeIfAbsent(
            key,
            k -> {
              String setting = getTypeAndSourceSettingValue("log", k);
              return Optional.ofNullable(setting != null ? LogLevel.parse(setting) : null);
            })
        .orElse(null);
  }

  /**
   * The boolean {@code @@<type>:<source>} setting, used to turn event types and sources on and off
   * (for example {@code @@error:java.io.IOException=false} or {@code @@usage:Export=false}).
   */
  public boolean getTypeAndSourceSetting(
      String type, @Nullable String source, boolean defaultValue) {
    if (source == null) {
      return defaultValue;
    }
    String value = getTypeAndSourceSettingValue(type, source);
    Boolean parsed = parseBoolean(value);
    if (parsed != null) {
      return parsed;
    }
    // Log level values ("off", "trace"...) are also accepted for non-log types.
    LogLevel level = LogLevel.parse(value);
    if (level != null) {
      return level != LogLevel.OFF;
    }
    return defaultValue;
  }

  private @Nullable String getTypeAndSourceSettingValue(String type, String source) {
    String prefix = "@@" + type + ":";
    String exact = values.get(prefix + source);
    if (exact != null) {
      return exact;
    }
    String prefixLower = prefix.toLowerCase(Locale.ROOT);
    for (Map.Entry<String, String> entry : wildcardEntries) {
      String key = entry.getKey();
      if (key.toLowerCase(Locale.ROOT).startsWith(prefixLower)
          && Wildcard.matches(source, key.substring(prefix.length()))) {
        return entry.getValue();
      }
    }
    return null;
  }

  static @Nullable Boolean parseBoolean(@Nullable String value) {
    if (value == null) {
      return null;
    }
    return switch (value.strip().toLowerCase(Locale.ROOT)) {
      case "true", "yes", "1", "on" -> Boolean.TRUE;
      case "false", "no", "0", "off" -> Boolean.FALSE;
      default -> null;
    };
  }

  @Override
  public String toString() {
    return values.toString();
  }
}
