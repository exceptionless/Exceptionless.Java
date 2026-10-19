package com.exceptionless.log4j2;

import com.exceptionless.EventBuilder;
import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.ReadOnlyStringMap;
import org.jspecify.annotations.Nullable;

/**
 * A Log4j 2 appender that sends log messages and exceptions to Exceptionless.
 *
 * <pre>{@code
 * <Configuration>
 *   <Appenders>
 *     <!-- apiKey is optional: without it the default client (Exceptionless.getDefault()) is used.
 *          serverUrl is for self hosted installs. minimumLevel defaults to warn; the project's
 *          @@log settings override it. -->
 *     <Exceptionless name="Exceptionless" apiKey="YOUR_API_KEY" minimumLevel="warn" />
 *   </Appenders>
 *   <Loggers>
 *     <Root level="info">
 *       <AppenderRef ref="Console" />
 *       <AppenderRef ref="Exceptionless" />
 *     </Root>
 *   </Loggers>
 * </Configuration>
 * }</pre>
 *
 * <p>Events with an exception become error events; the rest become log events whose source is the
 * logger name. Thread context (MDC) entries and the thread name are added as properties and the
 * marker as a tag. A message is sent when its level is at least {@code minimumLevel}, unless the
 * project's {@code @@log:<logger>} settings say otherwise (in either direction). Log4j's own logger
 * levels and filters still apply first, so a logger must be enabled at a level for the project
 * settings to lower the threshold to it.
 *
 * <p><b>Client.</b> With an {@code apiKey}, the appender creates its own client (configured from
 * the environment like {@link ExceptionlessConfiguration#fromEnvironment()}, without the uncaught
 * exception handler) on first use and closes it, sending queued events, when the appender stops.
 * Otherwise it uses {@link Exceptionless#getDefault() the default client} once the application has
 * started it, so logging never starts or locks the default client's configuration; messages logged
 * before then are not sent. {@link Builder#setClient} uses a specific client instead.
 *
 * <p>The client's own diagnostics (loggers under {@code com.exceptionless} and anything logged on
 * its background threads) and messages logged while an event is being submitted are never sent.
 * Appending never blocks on the network and never throws.
 */
@Plugin(
    name = ExceptionlessAppender.PLUGIN_NAME,
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE,
    printObject = true)
public final class ExceptionlessAppender extends AbstractAppender {
  /** The element name used in configuration files. */
  public static final String PLUGIN_NAME = "Exceptionless";

  /** The submission method recorded on events sent by this appender. */
  public static final String SUBMISSION_METHOD = "log4j2";

  private static final String INTERNAL_LOGGER_PREFIX = "com.exceptionless";
  private static final String INTERNAL_THREAD_PREFIX = "exceptionless-";
  private static final ThreadLocal<Boolean> APPENDING = new ThreadLocal<>();

  private final @Nullable String apiKey;
  private final @Nullable String serverUrl;
  private final LogLevel minimumLevel;
  private final boolean includeMdc;
  private final @Nullable ExceptionlessClient client;

  private final Object clientLock = new Object();
  private volatile @Nullable ExceptionlessClient ownedClient;
  private volatile boolean clientFailed;

  private ExceptionlessAppender(
      String name,
      @Nullable Filter filter,
      boolean ignoreExceptions,
      Property @Nullable [] properties,
      @Nullable String apiKey,
      @Nullable String serverUrl,
      LogLevel minimumLevel,
      boolean includeMdc,
      @Nullable ExceptionlessClient client) {
    super(name, filter, null, ignoreExceptions, properties);
    this.apiKey = apiKey;
    this.serverUrl = serverUrl;
    this.minimumLevel = minimumLevel;
    this.includeMdc = includeMdc;
    this.client = client;
  }

  /** Creates a builder; used by Log4j to create the appender from configuration. */
  @PluginBuilderFactory
  public static <B extends Builder<B>> B newBuilder() {
    return new Builder<B>().asBuilder();
  }

  public @Nullable String getApiKey() {
    return apiKey;
  }

  public @Nullable String getServerUrl() {
    return serverUrl;
  }

  public LogLevel getMinimumLevel() {
    return minimumLevel;
  }

  public boolean isIncludeMdc() {
    return includeMdc;
  }

  /**
   * The client the appender sends to: the one passed to {@link Builder#setClient}, the one it
   * created for its {@code apiKey}, or {@code null} when it uses the default client or hasn't sent
   * anything yet.
   */
  public @Nullable ExceptionlessClient getClient() {
    ExceptionlessClient explicit = client;
    return explicit != null ? explicit : ownedClient;
  }

  @Override
  public void append(LogEvent event) {
    if (APPENDING.get() != null || isInternal(event.getLoggerName(), event.getThreadName())) {
      return;
    }
    APPENDING.set(Boolean.TRUE);
    try {
      ExceptionlessClient target = resolveClient();
      if (target == null) {
        return;
      }
      LogLevel level = toLogLevel(event.getLevel());
      if (!target.isLogLevelEnabled(event.getLoggerName(), level, minimumLevel)
          || !target.getConfiguration().isEnabled()) {
        return;
      }
      createEvent(target, event, level).submit();
    } catch (RuntimeException | LinkageError e) {
      error("Unable to send a log event to Exceptionless", event, e);
    } finally {
      APPENDING.remove();
    }
  }

  private EventBuilder createEvent(ExceptionlessClient target, LogEvent event, LogLevel level) {
    String loggerName = event.getLoggerName();
    Message logMessage = event.getMessage();
    String message = logMessage != null ? logMessage.getFormattedMessage() : null;
    if (message == null) {
      message = "";
    }
    Throwable exception = event.getThrown();

    EventBuilder builder;
    if (exception != null) {
      builder =
          target
              .createException(exception)
              .setSource(loggerName)
              .setMessage(message)
              .setLevel(level);
    } else {
      builder = target.createLog(loggerName, message, level);
    }
    builder.setSubmissionMethod(SUBMISSION_METHOD);

    if (includeMdc) {
      ReadOnlyStringMap contextData = event.getContextData();
      if (contextData != null && !contextData.isEmpty()) {
        contextData.forEach((key, value) -> builder.setProperty(key, value));
      }
    }
    String thread = event.getThreadName();
    if (thread != null) {
      builder.setProperty("thread", thread);
    }
    Marker marker = event.getMarker();
    if (marker != null) {
      builder.addTags(marker.getName());
    }
    return builder;
  }

  private @Nullable ExceptionlessClient resolveClient() {
    ExceptionlessClient explicit = client;
    if (explicit != null) {
      return explicit;
    }
    String key = apiKey;
    if (key == null) {
      if (!Exceptionless.hasDefault()) {
        return null;
      }
      ExceptionlessClient defaultClient = Exceptionless.getDefault();
      return defaultClient.isStarted() ? defaultClient : null;
    }
    ExceptionlessClient owned = ownedClient;
    if (owned != null || clientFailed) {
      return owned;
    }
    synchronized (clientLock) {
      if (ownedClient == null && !clientFailed && isStarted()) {
        try {
          ExceptionlessConfiguration config =
              ExceptionlessConfiguration.fromEnvironment()
                  .apiKey(key)
                  .captureUncaughtExceptions(false);
          String url = serverUrl;
          if (url != null) {
            config.serverUrl(url);
          }
          ownedClient = new ExceptionlessClient(config).startup();
        } catch (RuntimeException e) {
          clientFailed = true;
          error("Unable to create the Exceptionless client", e);
        }
      }
      return ownedClient;
    }
  }

  /** Stops the appender and closes the client it created, sending queued events. */
  @Override
  public boolean stop(long timeout, TimeUnit timeUnit) {
    setStopping();
    boolean stopped = super.stop(timeout, timeUnit, false);
    ExceptionlessClient owned;
    synchronized (clientLock) {
      owned = ownedClient;
      ownedClient = null;
    }
    if (owned != null) {
      try {
        owned.close();
      } catch (RuntimeException e) {
        error("Unable to close the Exceptionless client", e);
      }
    }
    setStopped();
    return stopped;
  }

  /**
   * Maps a Log4j level to an Exceptionless level by severity, so custom levels map to the nearest
   * standard level. {@code OFF} (the most severe) maps to fatal and {@code ALL} to trace.
   */
  static LogLevel toLogLevel(@Nullable Level level) {
    if (level == null) {
      return LogLevel.INFO;
    }
    int value = level.intLevel();
    if (value <= Level.FATAL.intLevel()) {
      return LogLevel.FATAL;
    }
    if (value <= Level.ERROR.intLevel()) {
      return LogLevel.ERROR;
    }
    if (value <= Level.WARN.intLevel()) {
      return LogLevel.WARN;
    }
    if (value <= Level.INFO.intLevel()) {
      return LogLevel.INFO;
    }
    if (value <= Level.DEBUG.intLevel()) {
      return LogLevel.DEBUG;
    }
    return LogLevel.TRACE;
  }

  /** True for the Exceptionless client's own loggers and background threads. */
  static boolean isInternal(@Nullable String loggerName, @Nullable String threadName) {
    if (threadName != null && threadName.startsWith(INTERNAL_THREAD_PREFIX)) {
      return true;
    }
    return loggerName != null
        && loggerName.startsWith(INTERNAL_LOGGER_PREFIX)
        && (loggerName.length() == INTERNAL_LOGGER_PREFIX.length()
            || loggerName.charAt(INTERNAL_LOGGER_PREFIX.length()) == '.');
  }

  private static @Nullable String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Builds an {@link ExceptionlessAppender}. Besides {@code name}, {@code filter} and {@code
   * ignoreExceptions}, the configuration attributes are {@code apiKey}, {@code serverUrl}, {@code
   * minimumLevel} and {@code includeMdc}.
   *
   * @param <B> the builder type
   */
  public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
      implements org.apache.logging.log4j.core.util.Builder<ExceptionlessAppender> {
    @PluginBuilderAttribute(sensitive = true)
    private @Nullable String apiKey;

    @PluginBuilderAttribute private @Nullable String serverUrl;

    @PluginBuilderAttribute private @Nullable String minimumLevel;

    @PluginBuilderAttribute private boolean includeMdc = true;

    private @Nullable ExceptionlessClient client;

    /** The project API key. When set, the appender creates and owns its own client. */
    public B setApiKey(@Nullable String apiKey) {
      this.apiKey = apiKey;
      return asBuilder();
    }

    /** The server URL for self hosted installs. Only used together with {@link #setApiKey}. */
    public B setServerUrl(@Nullable String serverUrl) {
      this.serverUrl = serverUrl;
      return asBuilder();
    }

    /**
     * The minimum level sent when the project has no matching {@code @@log} setting (default {@code
     * warn}). Accepts Exceptionless and Log4j level names; {@code off} sends only what the project
     * settings enable.
     */
    public B setMinimumLevel(@Nullable String minimumLevel) {
      this.minimumLevel = minimumLevel;
      return asBuilder();
    }

    /** Whether thread context (MDC) entries are added as event properties (default true). */
    public B setIncludeMdc(boolean includeMdc) {
      this.includeMdc = includeMdc;
      return asBuilder();
    }

    /**
     * Sends to the given client, which the appender doesn't close. Takes precedence over the API
     * key.
     */
    public B setClient(@Nullable ExceptionlessClient client) {
      this.client = client;
      return asBuilder();
    }

    @Override
    public @Nullable ExceptionlessAppender build() {
      String name = getName();
      if (name == null || name.isBlank()) {
        LOGGER.error("No name provided for the Exceptionless appender");
        return null;
      }
      LogLevel minimum = LogLevel.WARN;
      String configured = trimToNull(minimumLevel);
      if (configured != null) {
        LogLevel parsed = LogLevel.parse(configured);
        if (parsed == null) {
          LOGGER.warn(
              "Unknown minimumLevel '{}' for the Exceptionless appender; using warn", configured);
        } else {
          minimum = parsed;
        }
      }
      return new ExceptionlessAppender(
          name,
          getFilter(),
          isIgnoreExceptions(),
          getPropertyArray(),
          trimToNull(apiKey),
          trimToNull(serverUrl),
          minimum,
          includeMdc,
          client);
    }
  }
}
