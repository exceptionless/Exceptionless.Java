package com.exceptionless;

import com.exceptionless.internal.DeploymentEnvironment;
import com.exceptionless.internal.Exclusions;
import com.exceptionless.internal.Log;
import com.exceptionless.internal.Strings;
import com.exceptionless.models.Event;
import com.exceptionless.models.KnownDataKeys;
import com.exceptionless.models.UserInfo;
import com.exceptionless.plugins.CancelSessionsWithNoUserPlugin;
import com.exceptionless.plugins.ConfigurationDefaultsPlugin;
import com.exceptionless.plugins.DuplicateCheckerPlugin;
import com.exceptionless.plugins.EnvironmentInfoPlugin;
import com.exceptionless.plugins.ErrorPlugin;
import com.exceptionless.plugins.EventExclusionPlugin;
import com.exceptionless.plugins.EventPlugin;
import com.exceptionless.plugins.EventPluginContext;
import com.exceptionless.plugins.HeartbeatPlugin;
import com.exceptionless.plugins.IgnoreUserAgentPlugin;
import com.exceptionless.plugins.ReferenceIdPlugin;
import com.exceptionless.plugins.RequestInfoPlugin;
import com.exceptionless.plugins.SessionIdManagementPlugin;
import com.exceptionless.plugins.SubmissionMethodPlugin;
import com.exceptionless.plugins.VersionPlugin;
import com.exceptionless.settings.ClientSettings;
import com.exceptionless.submission.SubmissionClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.net.ssl.SSLContext;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for an {@link ExceptionlessClient}. All setters return {@code this} so the
 * configuration can be built fluently:
 *
 * <pre>{@code
 * ExceptionlessClient client = ExceptionlessClient.create(c -> c
 *     .apiKey("YOUR_API_KEY")
 *     .version("1.2.3")
 *     .addDefaultTags("backend")
 *     .useSessions());
 * }</pre>
 *
 * <p>The connection settings (API key and server URLs) can't be changed once the client has
 * started. Everything else can be changed at any time and is thread-safe.
 */
public final class ExceptionlessConfiguration {
  public static final String DEFAULT_SERVER_URL = "https://collector.exceptionless.io";
  public static final String DEFAULT_CONFIG_SERVER_URL = "https://config.exceptionless.io";
  public static final String DEFAULT_HEARTBEAT_SERVER_URL = "https://heartbeat.exceptionless.io";

  static final Duration MIN_UPDATE_SETTINGS_INTERVAL = Duration.ofMinutes(2);
  static final Duration MIN_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

  private static final Log LOG = Log.get(ExceptionlessConfiguration.class);

  private volatile @Nullable String apiKey;
  private volatile boolean apiKeyValid;
  private volatile String serverUrl = DEFAULT_SERVER_URL;
  private volatile String configServerUrl = DEFAULT_CONFIG_SERVER_URL;
  private volatile String heartbeatServerUrl = DEFAULT_HEARTBEAT_SERVER_URL;
  private volatile boolean enabled = true;
  private volatile boolean locked;
  private volatile @Nullable String version;

  private final Object environmentLock = new Object();
  private volatile EnvironmentSetting environment = EnvironmentSetting.UNSET;

  private final Set<String> defaultTags = new CopyOnWriteArraySet<>();
  private final Map<String, Object> defaultData = new ConcurrentHashMap<>();
  private final Map<String, String> settings = new ConcurrentHashMap<>();
  private final List<String> dataExclusions = new CopyOnWriteArrayList<>();
  private final List<String> userAgentBotPatterns = new CopyOnWriteArrayList<>();
  private final List<Predicate<Event>> eventExclusions = new CopyOnWriteArrayList<>();
  private final List<EventPlugin> plugins = new CopyOnWriteArrayList<>();

  private volatile ClientSettings serverSettings = ClientSettings.empty();
  private volatile ClientSettings effectiveSettings = ClientSettings.empty();
  // Compiled from the local data exclusions and the effective settings; rebuilt when either
  // changes.
  private volatile Exclusions compiledDataExclusions = Exclusions.none();
  private final Object settingsLock = new Object();

  private volatile boolean includePrivateInformation = true;
  private volatile boolean includeUserName = true;
  private volatile boolean includeMachineName = true;
  private volatile boolean includeIpAddress = true;
  private volatile boolean includeHeaders = true;
  private volatile boolean includeCookies = true;
  private volatile boolean includePostData = true;
  private volatile boolean includeQueryString = true;
  private volatile boolean includeModules = true;

  private volatile boolean referenceIdsEnabled = true;
  private volatile boolean sessionsEnabled;
  private volatile boolean sendHeartbeats;
  private volatile Duration heartbeatInterval = Duration.ofSeconds(60);
  private volatile @Nullable String currentSessionIdentifier;

  private volatile @Nullable LogLevel defaultMinLogLevel;

  private volatile int submissionBatchSize = 50;
  private volatile Duration queueProcessInterval = Duration.ofSeconds(10);
  private volatile int queueMaxAttempts = 3;
  private volatile Duration queueMaxAge = Duration.ofDays(2);
  private volatile int maxQueueSize = 1000;
  private volatile Duration updateSettingsWhenIdleInterval = MIN_UPDATE_SETTINGS_INTERVAL;
  private volatile @Nullable Path storagePath;

  private volatile Duration connectTimeout = Duration.ofSeconds(10);
  private volatile Duration requestTimeout = Duration.ofSeconds(30);
  private volatile @Nullable ProxySelector proxy;
  private volatile @Nullable SSLContext sslContext;
  private volatile @Nullable HttpClient httpClient;
  private volatile @Nullable SubmissionClient submissionClient;

  private volatile boolean captureUncaughtExceptions = true;
  private volatile boolean registerShutdownHook = true;
  private volatile Duration shutdownTimeout = Duration.ofSeconds(5);

  /**
   * The default deployment environment and where it came from. {@code configured} is true for a
   * value from code or external configuration, even an invalid one that leaves events without an
   * environment; only a fallback is replaced by later sources.
   */
  private record EnvironmentSetting(@Nullable String name, boolean configured, boolean setInCode) {
    static final EnvironmentSetting UNSET = new EnvironmentSetting(null, false, false);
  }

  public ExceptionlessConfiguration() {
    addDefaultPlugins();
  }

  /**
   * Creates a configuration populated from the environment, in increasing order of precedence:
   * {@code exceptionless.properties} on the class path, {@code EXCEPTIONLESS_*} environment
   * variables and {@code exceptionless.*} system properties.
   *
   * <p>Supported keys: {@code apiKey}, {@code serverUrl}, {@code configServerUrl}, {@code
   * heartbeatServerUrl}, {@code enabled}, {@code version}, {@code environment}, {@code
   * storagePath}, {@code includePrivateInformation}, {@code defaultTags} and {@code
   * settings.<name>}. Environment variables use the upper snake case form, e.g. {@code
   * EXCEPTIONLESS_API_KEY} and {@code EXCEPTIONLESS_ENVIRONMENT}.
   *
   * <p>A deployment environment set in code with {@link #environment(String)} takes precedence over
   * these sources, whichever is applied first.
   */
  public static ExceptionlessConfiguration fromEnvironment() {
    ExceptionlessConfiguration config = new ExceptionlessConfiguration();
    config.readFromEnvironment();
    return config;
  }

  /** Applies {@code exceptionless.properties}, environment variables and system properties. */
  public ExceptionlessConfiguration readFromEnvironment() {
    readFromClasspathProperties();
    readFromValues(System.getenv(), ExceptionlessConfiguration::environmentName);
    Map<String, String> systemProperties = new LinkedHashMap<>();
    for (String name : System.getProperties().stringPropertyNames()) {
      systemProperties.put(name, System.getProperty(name));
    }
    readFromValues(systemProperties, key -> "exceptionless." + key);
    return this;
  }

  private void readFromClasspathProperties() {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader == null) {
      loader = ExceptionlessConfiguration.class.getClassLoader();
    }
    if (loader == null) {
      return;
    }
    try (InputStream in = loader.getResourceAsStream("exceptionless.properties")) {
      if (in == null) {
        return;
      }
      Properties properties = new Properties();
      properties.load(in);
      Map<String, String> values = new LinkedHashMap<>();
      for (String name : properties.stringPropertyNames()) {
        values.put(name, properties.getProperty(name));
      }
      readFromValues(values, key -> "exceptionless." + key);
      readFromValues(values, key -> key);
    } catch (IOException | IllegalArgumentException e) {
      LOG.warn("Unable to read exceptionless.properties", e);
    }
  }

  /**
   * Reads configuration values from a map, using {@code keyMapper} to turn a setting name (such as
   * {@code apiKey}) into the map key.
   */
  public ExceptionlessConfiguration readFromValues(
      Map<String, String> values, Function<String, String> keyMapper) {
    apply(values, keyMapper, "apiKey", this::apiKey);
    apply(values, keyMapper, "serverUrl", this::serverUrl);
    apply(values, keyMapper, "configServerUrl", this::configServerUrl);
    apply(values, keyMapper, "heartbeatServerUrl", this::heartbeatServerUrl);
    apply(values, keyMapper, "enabled", value -> enabled(Boolean.parseBoolean(value)));
    apply(values, keyMapper, "version", this::version);
    // An empty value is still a choice: it leaves events without an environment.
    String configuredEnvironment = values.get(keyMapper.apply("environment"));
    if (configuredEnvironment != null) {
      applyConfiguredEnvironment(configuredEnvironment);
    }
    apply(values, keyMapper, "storagePath", value -> useFileStorage(Path.of(value)));
    apply(
        values,
        keyMapper,
        "includePrivateInformation",
        value -> includePrivateInformation(Boolean.parseBoolean(value)));
    apply(
        values,
        keyMapper,
        "defaultTags",
        value -> addDefaultTags(Strings.splitList(value).toArray(String[]::new)));
    String settingsPrefix = keyMapper.apply("settings.");
    for (Map.Entry<String, String> entry : values.entrySet()) {
      if (entry.getKey().startsWith(settingsPrefix)
          && entry.getKey().length() > settingsPrefix.length()) {
        setting(entry.getKey().substring(settingsPrefix.length()), entry.getValue());
      }
    }
    return this;
  }

  /**
   * {@code apiKey} becomes {@code EXCEPTIONLESS_API_KEY}; {@code settings.} becomes {@code
   * EXCEPTIONLESS_SETTINGS_}.
   */
  static String environmentName(String key) {
    StringBuilder sb = new StringBuilder("EXCEPTIONLESS_");
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (c == '.') {
        sb.append('_');
      } else if (Character.isUpperCase(c)) {
        sb.append('_').append(c);
      } else {
        sb.append(Character.toUpperCase(c));
      }
    }
    return sb.toString();
  }

  /** Passes the stripped value of {@code name} to {@code setter}, when it is set. */
  private static void apply(
      Map<String, String> values,
      Function<String, String> keyMapper,
      String name,
      Consumer<String> setter) {
    String value = Strings.stripToNull(values.get(keyMapper.apply(name)));
    if (value != null) {
      setter.accept(value);
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Connection

  public @Nullable String getApiKey() {
    return apiKey;
  }

  /** Sets the project API key. Required. */
  public ExceptionlessConfiguration apiKey(@Nullable String apiKey) {
    ensureUnlocked("apiKey");
    String key = Strings.stripToNull(apiKey);
    this.apiKey = key;
    this.apiKeyValid = isValidApiKey(key);
    return this;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  /**
   * Sets the server URL (for self hosted installs). Also sets the config and heartbeat server URLs,
   * which can then be overridden individually.
   */
  public ExceptionlessConfiguration serverUrl(String serverUrl) {
    ensureUnlocked("serverUrl");
    String url = normalizeUrl(serverUrl);
    this.serverUrl = url;
    this.configServerUrl = url;
    this.heartbeatServerUrl = url;
    return this;
  }

  public String getConfigServerUrl() {
    return configServerUrl;
  }

  public ExceptionlessConfiguration configServerUrl(String configServerUrl) {
    ensureUnlocked("configServerUrl");
    this.configServerUrl = normalizeUrl(configServerUrl);
    return this;
  }

  public String getHeartbeatServerUrl() {
    return heartbeatServerUrl;
  }

  public ExceptionlessConfiguration heartbeatServerUrl(String heartbeatServerUrl) {
    ensureUnlocked("heartbeatServerUrl");
    this.heartbeatServerUrl = normalizeUrl(heartbeatServerUrl);
    return this;
  }

  private static String normalizeUrl(String url) {
    String stripped = Strings.stripToNull(url);
    if (stripped == null) {
      throw new IllegalArgumentException("A server URL is required");
    }
    while (stripped.endsWith("/")) {
      stripped = stripped.substring(0, stripped.length() - 1);
    }
    if (!stripped.startsWith("http://") && !stripped.startsWith("https://")) {
      throw new IllegalArgumentException("Server URL must start with http:// or https://: " + url);
    }
    return stripped;
  }

  /** Whether events are sent. A configuration without a valid API key is never enabled. */
  public boolean isEnabled() {
    return enabled && apiKeyValid;
  }

  public ExceptionlessConfiguration enabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  /** Returns a list of configuration problems; empty when the configuration is valid. */
  public List<String> validate() {
    List<String> problems = new ArrayList<>();
    String key = apiKey;
    if (key == null) {
      problems.add("An API key is required");
    } else if (!isValidApiKey(key)) {
      problems.add("The API key is invalid");
    }
    return problems;
  }

  private static boolean isValidApiKey(@Nullable String key) {
    return key != null
        && !key.equals("API_KEY_HERE")
        && key.length() >= 10
        && key.chars().noneMatch(Character::isWhitespace);
  }

  boolean isLocked() {
    return locked;
  }

  void lock() {
    locked = true;
  }

  private void ensureUnlocked(String property) {
    if (locked) {
      throw new IllegalStateException(
          "The " + property + " can't be changed after the Exceptionless client has started");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Event defaults

  public @Nullable String getVersion() {
    return version;
  }

  /** Sets the application version, attached to every event as {@code @version}. */
  public ExceptionlessConfiguration version(@Nullable String version) {
    this.version = Strings.stripToNull(version);
    return this;
  }

  /** {@return the default deployment environment sent with events, or null when unspecified} */
  public @Nullable String getEnvironment() {
    return environment.name();
  }

  /**
   * Whether a deployment environment was configured in code or by external configuration. An
   * invalid configured value counts: events are then sent without an environment, and {@link
   * #environmentFallback(String)} has no effect.
   */
  public boolean isEnvironmentConfigured() {
    return environment.configured();
  }

  /**
   * Sets the default deployment environment, such as {@code production} or {@code staging}, for
   * events that don't set their own with {@link EventBuilder#setEnvironment}. A value set here wins
   * over {@code exceptionless.properties}, system properties and environment variables, whichever
   * is read last.
   *
   * <p>The name is trimmed and keeps its casing. Empty names, names longer than 64 characters and
   * names with control characters leave events without an environment (and still win over external
   * configuration). Pass null to forget a value set here.
   */
  public ExceptionlessConfiguration environment(@Nullable String environment) {
    synchronized (environmentLock) {
      this.environment =
          environment == null
              ? EnvironmentSetting.UNSET
              : new EnvironmentSetting(DeploymentEnvironment.normalize(environment), true, true);
    }
    return this;
  }

  /**
   * Sets a deployment environment that applies only when none is configured, in code or by external
   * configuration. Framework integrations use it for the environment name the framework already
   * has, such as a Spring profile. It has no effect once an environment is configured, and a later
   * configured value replaces it.
   */
  public ExceptionlessConfiguration environmentFallback(@Nullable String environment) {
    synchronized (environmentLock) {
      if (!this.environment.configured()) {
        this.environment =
            new EnvironmentSetting(DeploymentEnvironment.normalize(environment), false, false);
      }
    }
    return this;
  }

  private void applyConfiguredEnvironment(String environment) {
    synchronized (environmentLock) {
      if (!this.environment.setInCode()) {
        this.environment =
            new EnvironmentSetting(DeploymentEnvironment.normalize(environment), true, false);
      }
    }
  }

  /** {@return a snapshot of the tags added to every event} */
  public Set<String> getDefaultTags() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(defaultTags));
  }

  public ExceptionlessConfiguration addDefaultTags(String... tags) {
    for (String tag : tags) {
      String stripped = Strings.stripToNull(tag);
      if (stripped != null) {
        defaultTags.add(stripped);
      }
    }
    return this;
  }

  public ExceptionlessConfiguration removeDefaultTags(String... tags) {
    for (String tag : tags) {
      defaultTags.remove(tag);
    }
    return this;
  }

  /** {@return a snapshot of the data added to every event} */
  public Map<String, Object> getDefaultData() {
    return Map.copyOf(defaultData);
  }

  /** Adds data to every event. The value is normalized when events are created. */
  public ExceptionlessConfiguration addDefaultData(String key, @Nullable Object value) {
    if (value == null) {
      defaultData.remove(key);
    } else {
      defaultData.put(key, value);
    }
    return this;
  }

  /** Sets the user attached to every event (for example in a desktop or CLI app). */
  public ExceptionlessConfiguration userIdentity(@Nullable String identity) {
    return userIdentity(identity, null);
  }

  public ExceptionlessConfiguration userIdentity(@Nullable String identity, @Nullable String name) {
    if (Strings.isBlank(identity) && Strings.isBlank(name)) {
      return addDefaultData(KnownDataKeys.USER_INFO, null);
    }
    return addDefaultData(KnownDataKeys.USER_INFO, new UserInfo(identity, name));
  }

  // ---------------------------------------------------------------------------------------------
  // Settings, exclusions and privacy

  /**
   * {@return the effective configuration values: local settings overlaid with the project's
   * settings}
   */
  public ClientSettings getSettings() {
    return effectiveSettings;
  }

  /** {@return the settings most recently received from the server} */
  public ClientSettings getServerSettings() {
    return serverSettings;
  }

  /**
   * Sets a local configuration value, e.g. {@code setting("@@log:com.example.*", "info")}. Values
   * received from the server take precedence.
   */
  public ExceptionlessConfiguration setting(String key, @Nullable String value) {
    if (value == null) {
      settings.remove(key);
    } else {
      settings.put(key, value);
    }
    recomputeSettings();
    return this;
  }

  /**
   * Applies settings received from the server. Called by the client when it loads or fetches the
   * project settings; applications normally don't need to call it.
   */
  public void applyServerSettings(Map<String, String> values) {
    this.serverSettings = ClientSettings.of(values);
    recomputeSettings();
  }

  private void recomputeSettings() {
    synchronized (settingsLock) {
      effectiveSettings = ClientSettings.of(Map.copyOf(settings), serverSettings.asMap());
      compiledDataExclusions = Exclusions.of(getDataExclusions());
    }
  }

  /**
   * Excludes data with matching names (wildcards supported, e.g. {@code *password*}) from event
   * data, exception properties, cookies, query strings and form data.
   */
  public ExceptionlessConfiguration addDataExclusions(String... patterns) {
    for (String pattern : patterns) {
      String stripped = Strings.stripToNull(pattern);
      if (stripped != null) {
        dataExclusions.add(stripped);
      }
    }
    synchronized (settingsLock) {
      compiledDataExclusions = Exclusions.of(getDataExclusions());
    }
    return this;
  }

  /** Local data exclusions plus the project's {@code @@DataExclusions} setting. */
  public List<String> getDataExclusions() {
    List<String> server = effectiveSettings.getStringList(ClientSettings.DATA_EXCLUSIONS);
    if (server.isEmpty()) {
      return List.copyOf(dataExclusions);
    }
    List<String> all = new ArrayList<>(dataExclusions);
    all.addAll(server);
    return List.copyOf(all);
  }

  /**
   * Whether data named {@code name} matches the {@link #getDataExclusions() data exclusions}. The
   * patterns are compiled once and recompiled when exclusions or settings change.
   */
  public boolean isDataExcluded(String name) {
    return compiledDataExclusions.test(name);
  }

  /** A snapshot of the compiled data exclusions. */
  Exclusions compiledDataExclusions() {
    return compiledDataExclusions;
  }

  /** Ignores requests whose user agent matches (wildcards supported, e.g. {@code *bot*}). */
  public ExceptionlessConfiguration addUserAgentBotPatterns(String... patterns) {
    for (String pattern : patterns) {
      String stripped = Strings.stripToNull(pattern);
      if (stripped != null) {
        userAgentBotPatterns.add(stripped);
      }
    }
    return this;
  }

  /** Local bot patterns plus the project's {@code @@UserAgentBotPatterns} setting. */
  public List<String> getUserAgentBotPatterns() {
    List<String> server = effectiveSettings.getStringList(ClientSettings.USER_AGENT_BOT_PATTERNS);
    if (server.isEmpty()) {
      return List.copyOf(userAgentBotPatterns);
    }
    List<String> all = new ArrayList<>(userAgentBotPatterns);
    all.addAll(server);
    return List.copyOf(all);
  }

  /** Adds a callback that returns {@code true} for events that should be discarded. */
  public ExceptionlessConfiguration addEventExclusion(Predicate<Event> exclusion) {
    eventExclusions.add(exclusion);
    return this;
  }

  public List<Predicate<Event>> getEventExclusions() {
    return List.copyOf(eventExclusions);
  }

  /**
   * Turns all private information on or off: user names, machine name, IP addresses, headers,
   * cookies, form data and query strings. The project's {@code @@IncludePrivateInformation} setting
   * can also turn it off.
   */
  public ExceptionlessConfiguration includePrivateInformation(boolean include) {
    this.includePrivateInformation = include;
    this.includeUserName = include;
    this.includeMachineName = include;
    this.includeIpAddress = include;
    this.includeHeaders = include;
    this.includeCookies = include;
    this.includePostData = include;
    this.includeQueryString = include;
    return this;
  }

  public boolean isIncludePrivateInformation() {
    return includePrivateInformation
        && effectiveSettings.getBoolean(ClientSettings.INCLUDE_PRIVATE_INFORMATION, true);
  }

  public boolean isIncludeUserName() {
    return includeUserName && isIncludePrivateInformationSetting();
  }

  public ExceptionlessConfiguration includeUserName(boolean include) {
    this.includeUserName = include;
    return this;
  }

  public boolean isIncludeMachineName() {
    return includeMachineName && isIncludePrivateInformationSetting();
  }

  public ExceptionlessConfiguration includeMachineName(boolean include) {
    this.includeMachineName = include;
    return this;
  }

  public boolean isIncludeIpAddress() {
    return includeIpAddress && isIncludePrivateInformationSetting();
  }

  public ExceptionlessConfiguration includeIpAddress(boolean include) {
    this.includeIpAddress = include;
    return this;
  }

  public boolean isIncludeHeaders() {
    return includeHeaders && isIncludePrivateInformationSetting();
  }

  public ExceptionlessConfiguration includeHeaders(boolean include) {
    this.includeHeaders = include;
    return this;
  }

  public boolean isIncludeCookies() {
    return includeCookies && isIncludePrivateInformationSetting();
  }

  public ExceptionlessConfiguration includeCookies(boolean include) {
    this.includeCookies = include;
    return this;
  }

  public boolean isIncludePostData() {
    return includePostData && isIncludePrivateInformationSetting();
  }

  public ExceptionlessConfiguration includePostData(boolean include) {
    this.includePostData = include;
    return this;
  }

  public boolean isIncludeQueryString() {
    return includeQueryString && isIncludePrivateInformationSetting();
  }

  public ExceptionlessConfiguration includeQueryString(boolean include) {
    this.includeQueryString = include;
    return this;
  }

  private boolean isIncludePrivateInformationSetting() {
    return effectiveSettings.getBoolean(ClientSettings.INCLUDE_PRIVATE_INFORMATION, true);
  }

  /** Whether the libraries on the class path are attached to errors. */
  public boolean isIncludeModules() {
    return includeModules;
  }

  public ExceptionlessConfiguration includeModules(boolean include) {
    this.includeModules = include;
    return this;
  }

  /**
   * The minimum level for log events from {@code source} when no {@code @@log:*} setting matches.
   * {@code null} (the default) sends all log events.
   */
  public @Nullable LogLevel getDefaultMinLogLevel() {
    return defaultMinLogLevel;
  }

  public ExceptionlessConfiguration defaultMinLogLevel(@Nullable LogLevel level) {
    this.defaultMinLogLevel = level;
    return this;
  }

  /**
   * Whether a log message from {@code source} at {@code level} would be sent, using the project's
   * {@code @@log:} settings and falling back to {@code defaultMinimum}.
   */
  public boolean isLogLevelEnabled(
      @Nullable String source, LogLevel level, @Nullable LogLevel defaultMinimum) {
    LogLevel minimum = effectiveSettings.getMinLogLevel(source);
    if (minimum == null) {
      minimum = defaultMinimum;
    }
    return minimum == null || level.isAtLeast(minimum);
  }

  // ---------------------------------------------------------------------------------------------
  // Reference ids and sessions

  /** Whether error events get a random reference id (on by default). */
  public boolean isReferenceIdsEnabled() {
    return referenceIdsEnabled;
  }

  public ExceptionlessConfiguration useReferenceIds(boolean enabled) {
    this.referenceIdsEnabled = enabled;
    return this;
  }

  /** Enables sessions with heartbeats every 60 seconds. */
  public ExceptionlessConfiguration useSessions() {
    return useSessions(true, Duration.ofSeconds(60), false);
  }

  /**
   * Enables session tracking.
   *
   * @param sendHeartbeats keep the session alive while the app runs
   * @param heartbeatInterval how often to send heartbeats (at least 30 seconds)
   * @param useSessionIdManagement generate session ids on the client instead of using the user
   *     identity (for apps without a signed-in user)
   */
  public ExceptionlessConfiguration useSessions(
      boolean sendHeartbeats, Duration heartbeatInterval, boolean useSessionIdManagement) {
    this.sessionsEnabled = true;
    this.sendHeartbeats = sendHeartbeats;
    this.heartbeatInterval =
        heartbeatInterval.compareTo(MIN_HEARTBEAT_INTERVAL) < 0
            ? MIN_HEARTBEAT_INTERVAL
            : heartbeatInterval;
    if (sendHeartbeats) {
      addPlugin(new HeartbeatPlugin());
    }
    if (useSessionIdManagement) {
      addPlugin(new SessionIdManagementPlugin());
    }
    return this;
  }

  public boolean isSessionsEnabled() {
    return sessionsEnabled;
  }

  public boolean isSendHeartbeats() {
    return sendHeartbeats;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  /** The session id or user identity heartbeats are sent for. Managed by the session plugins. */
  public @Nullable String getCurrentSessionIdentifier() {
    return currentSessionIdentifier;
  }

  public ExceptionlessConfiguration currentSessionIdentifier(@Nullable String identifier) {
    this.currentSessionIdentifier = identifier;
    return this;
  }

  // ---------------------------------------------------------------------------------------------
  // Queue, storage and settings polling

  public int getSubmissionBatchSize() {
    return submissionBatchSize;
  }

  public ExceptionlessConfiguration submissionBatchSize(int size) {
    if (size < 1) {
      throw new IllegalArgumentException("submissionBatchSize must be at least 1");
    }
    this.submissionBatchSize = size;
    return this;
  }

  public Duration getQueueProcessInterval() {
    return queueProcessInterval;
  }

  /** How often queued events are sent (default 10 seconds). */
  public ExceptionlessConfiguration queueProcessInterval(Duration interval) {
    if (interval.isNegative() || interval.isZero()) {
      throw new IllegalArgumentException("queueProcessInterval must be positive");
    }
    this.queueProcessInterval = interval;
    return this;
  }

  public int getQueueMaxAttempts() {
    return queueMaxAttempts;
  }

  /** How many times an event is retried before it is discarded (default 3). */
  public ExceptionlessConfiguration queueMaxAttempts(int attempts) {
    this.queueMaxAttempts = Math.max(1, attempts);
    return this;
  }

  public Duration getQueueMaxAge() {
    return queueMaxAge;
  }

  /**
   * How long an event may wait in the queue before it is discarded (default 2 days). The server
   * discards events older than 3 days.
   */
  public ExceptionlessConfiguration queueMaxAge(Duration maxAge) {
    this.queueMaxAge = maxAge;
    return this;
  }

  public int getMaxQueueSize() {
    return maxQueueSize;
  }

  /** The maximum number of queued events; the oldest are dropped when full (default 1000). */
  public ExceptionlessConfiguration maxQueueSize(int maxQueueSize) {
    this.maxQueueSize = Math.max(1, maxQueueSize);
    return this;
  }

  public Duration getUpdateSettingsWhenIdleInterval() {
    return updateSettingsWhenIdleInterval;
  }

  /**
   * How often to check for new project settings (at least 2 minutes). {@link Duration#ZERO}
   * disables polling; settings are still refreshed when the server reports a new version.
   */
  public ExceptionlessConfiguration updateSettingsWhenIdleInterval(Duration interval) {
    if (interval.isZero() || interval.isNegative()) {
      this.updateSettingsWhenIdleInterval = Duration.ZERO;
    } else {
      this.updateSettingsWhenIdleInterval =
          interval.compareTo(MIN_UPDATE_SETTINGS_INTERVAL) < 0
              ? MIN_UPDATE_SETTINGS_INTERVAL
              : interval;
    }
    return this;
  }

  public @Nullable Path getStoragePath() {
    return storagePath;
  }

  /**
   * Persists queued events and settings under {@code path}, so events survive restarts. Each API
   * key gets its own sub directory.
   */
  public ExceptionlessConfiguration useFileStorage(@Nullable Path path) {
    ensureUnlocked("storagePath");
    this.storagePath = path;
    return this;
  }

  // ---------------------------------------------------------------------------------------------
  // HTTP

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public ExceptionlessConfiguration connectTimeout(Duration timeout) {
    this.connectTimeout = timeout;
    return this;
  }

  public Duration getRequestTimeout() {
    return requestTimeout;
  }

  public ExceptionlessConfiguration requestTimeout(Duration timeout) {
    this.requestTimeout = timeout;
    return this;
  }

  public @Nullable ProxySelector getProxy() {
    return proxy;
  }

  /** Sends requests through a proxy, e.g. {@code ProxySelector.of(new InetSocketAddress(...))}. */
  public ExceptionlessConfiguration proxy(@Nullable ProxySelector proxy) {
    ensureUnlocked("proxy");
    this.proxy = proxy;
    return this;
  }

  public @Nullable SSLContext getSslContext() {
    return sslContext;
  }

  /** Uses a custom TLS configuration (for example to trust a private CA). */
  public ExceptionlessConfiguration sslContext(@Nullable SSLContext sslContext) {
    ensureUnlocked("sslContext");
    this.sslContext = sslContext;
    return this;
  }

  public @Nullable HttpClient getHttpClient() {
    return httpClient;
  }

  /** Uses your own {@link HttpClient}; the proxy, TLS and connect timeout options are ignored. */
  public ExceptionlessConfiguration httpClient(@Nullable HttpClient httpClient) {
    ensureUnlocked("httpClient");
    this.httpClient = httpClient;
    return this;
  }

  public @Nullable SubmissionClient getSubmissionClient() {
    return submissionClient;
  }

  /** Replaces how events are sent. Mostly useful for tests. */
  public ExceptionlessConfiguration submissionClient(@Nullable SubmissionClient submissionClient) {
    ensureUnlocked("submissionClient");
    this.submissionClient = submissionClient;
    return this;
  }

  // ---------------------------------------------------------------------------------------------
  // Lifecycle

  public boolean isCaptureUncaughtExceptions() {
    return captureUncaughtExceptions;
  }

  /** Reports exceptions that reach the default uncaught exception handler (on by default). */
  public ExceptionlessConfiguration captureUncaughtExceptions(boolean capture) {
    this.captureUncaughtExceptions = capture;
    return this;
  }

  public boolean isRegisterShutdownHook() {
    return registerShutdownHook;
  }

  /** Sends queued events when the JVM shuts down (on by default). */
  public ExceptionlessConfiguration registerShutdownHook(boolean register) {
    this.registerShutdownHook = register;
    return this;
  }

  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  /** How long {@link ExceptionlessClient#close()} waits for queued events to be sent. */
  public ExceptionlessConfiguration shutdownTimeout(Duration timeout) {
    this.shutdownTimeout = timeout;
    return this;
  }

  // ---------------------------------------------------------------------------------------------
  // Plugins

  /** {@return the plugins in the order they run (ascending priority)} */
  public List<EventPlugin> getPlugins() {
    return plugins.stream().sorted(Comparator.comparingInt(EventPlugin::getPriority)).toList();
  }

  /** Adds a plugin, replacing any existing plugin with the same name. */
  public ExceptionlessConfiguration addPlugin(EventPlugin plugin) {
    removePlugin(plugin.getName());
    plugins.add(plugin);
    return this;
  }

  /** Adds a plugin from a lambda. Lower priorities run first. See {@link EventPlugin#of}. */
  public ExceptionlessConfiguration addPlugin(
      String name, int priority, Consumer<EventPluginContext> plugin) {
    return addPlugin(EventPlugin.of(name, priority, plugin));
  }

  public ExceptionlessConfiguration removePlugin(String name) {
    plugins.removeIf(p -> p.getName().equals(name));
    return this;
  }

  public ExceptionlessConfiguration removePlugin(Class<? extends EventPlugin> type) {
    plugins.removeIf(type::isInstance);
    return this;
  }

  public boolean hasPlugin(String name) {
    return plugins.stream().anyMatch(p -> p.getName().equals(name));
  }

  private void addDefaultPlugins() {
    Collection<EventPlugin> defaults =
        List.of(
            new ConfigurationDefaultsPlugin(),
            new ErrorPlugin(),
            new ReferenceIdPlugin(),
            new EventExclusionPlugin(),
            new EnvironmentInfoPlugin(),
            new RequestInfoPlugin(),
            new IgnoreUserAgentPlugin(),
            new VersionPlugin(),
            new SubmissionMethodPlugin(),
            new CancelSessionsWithNoUserPlugin(),
            new DuplicateCheckerPlugin());
    plugins.addAll(defaults);
  }
}
