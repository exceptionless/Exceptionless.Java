package com.exceptionless.spring.boot;

import com.exceptionless.LogLevel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exceptionless settings ({@code exceptionless.*}). Values that are not set fall back to {@code
 * exceptionless.properties}, {@code EXCEPTIONLESS_*} environment variables and {@code
 * exceptionless.*} system properties.
 *
 * <pre>{@code
 * exceptionless:
 *   api-key: YOUR_API_KEY
 *   default-tags: [backend]
 *   logging:
 *     minimum-level: info
 * }</pre>
 */
@ConfigurationProperties("exceptionless")
public class ExceptionlessProperties {
  /** Whether Exceptionless is enabled. When false, nothing is auto-configured. */
  private boolean enabled = true;

  /** The project API key. */
  private @Nullable String apiKey;

  /** The server URL, for self hosted installs. Also used for settings and heartbeats. */
  private @Nullable String serverUrl;

  /** The application version. Defaults to the version from build-info (BuildProperties). */
  private @Nullable String version;

  /**
   * The default deployment environment, such as production or staging. When unset, the only active
   * Spring profile is used.
   */
  private @Nullable String environment;

  /** Tags added to every event. */
  private List<String> defaultTags = new ArrayList<>();

  /** Data added to every event. */
  private Map<String, Object> defaultData = new LinkedHashMap<>();

  /**
   * Local client settings. The project's settings take precedence. Keys with special characters
   * need brackets, e.g. {@code exceptionless.settings.[@@log\:com.example.*]=info} in a properties
   * file or {@code "[@@log:com.example.*]": info} in YAML.
   */
  private Map<String, String> settings = new LinkedHashMap<>();

  /**
   * Whether private information (user names, IP addresses, headers, cookies, query strings and form
   * data) is sent. Unset keeps the client default (true).
   */
  private @Nullable Boolean includePrivateInformation;

  /** A directory to persist queued events and settings in, so events survive restarts. */
  private @Nullable Path storagePath;

  /** Whether to track sessions, with heartbeats. */
  private boolean useSessions;

  /** Whether exceptions that reach the default uncaught exception handler are reported. */
  private boolean captureUncaughtExceptions = true;

  /** Whether 404 responses are reported (Spring MVC and WebFlux). */
  private boolean captureNotFound = true;

  /**
   * The order of the web filter that reports unhandled exceptions and 404s. The default runs just
   * after Spring Security's filter chain (-100), so the signed in user is known.
   */
  private int filterOrder = -99;

  private final Logging logging = new Logging();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public @Nullable String getApiKey() {
    return apiKey;
  }

  public void setApiKey(@Nullable String apiKey) {
    this.apiKey = apiKey;
  }

  public @Nullable String getServerUrl() {
    return serverUrl;
  }

  public void setServerUrl(@Nullable String serverUrl) {
    this.serverUrl = serverUrl;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(@Nullable String version) {
    this.version = version;
  }

  public @Nullable String getEnvironment() {
    return environment;
  }

  public void setEnvironment(@Nullable String environment) {
    this.environment = environment;
  }

  public List<String> getDefaultTags() {
    return defaultTags;
  }

  public void setDefaultTags(List<String> defaultTags) {
    this.defaultTags = defaultTags;
  }

  public Map<String, Object> getDefaultData() {
    return defaultData;
  }

  public void setDefaultData(Map<String, Object> defaultData) {
    this.defaultData = defaultData;
  }

  public Map<String, String> getSettings() {
    return settings;
  }

  public void setSettings(Map<String, String> settings) {
    this.settings = settings;
  }

  public @Nullable Boolean getIncludePrivateInformation() {
    return includePrivateInformation;
  }

  public void setIncludePrivateInformation(@Nullable Boolean includePrivateInformation) {
    this.includePrivateInformation = includePrivateInformation;
  }

  public @Nullable Path getStoragePath() {
    return storagePath;
  }

  public void setStoragePath(@Nullable Path storagePath) {
    this.storagePath = storagePath;
  }

  public boolean isUseSessions() {
    return useSessions;
  }

  public void setUseSessions(boolean useSessions) {
    this.useSessions = useSessions;
  }

  public boolean isCaptureUncaughtExceptions() {
    return captureUncaughtExceptions;
  }

  public void setCaptureUncaughtExceptions(boolean captureUncaughtExceptions) {
    this.captureUncaughtExceptions = captureUncaughtExceptions;
  }

  public boolean isCaptureNotFound() {
    return captureNotFound;
  }

  public void setCaptureNotFound(boolean captureNotFound) {
    this.captureNotFound = captureNotFound;
  }

  public int getFilterOrder() {
    return filterOrder;
  }

  public void setFilterOrder(int filterOrder) {
    this.filterOrder = filterOrder;
  }

  public Logging getLogging() {
    return logging;
  }

  /** Logging integration settings ({@code exceptionless.logging.*}). */
  public static class Logging {
    /** Whether log events are sent through an appender added to the Logback root logger. */
    private boolean enabled = true;

    /**
     * The minimum level sent when the project has no matching @@log setting: trace, debug, info,
     * warn, error, fatal or off.
     */
    private LogLevel minimumLevel = LogLevel.WARN;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public LogLevel getMinimumLevel() {
      return minimumLevel;
    }

    public void setMinimumLevel(LogLevel minimumLevel) {
      this.minimumLevel = minimumLevel;
    }
  }
}
