package com.exceptionless.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.exceptionless.EventBuilder;
import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

/**
 * A Logback appender that sends log messages and exceptions to Exceptionless.
 *
 * <pre>{@code
 * <configuration>
 *   <appender name="EXCEPTIONLESS" class="com.exceptionless.logback.ExceptionlessAppender">
 *     <!-- Optional: without an API key the default client (Exceptionless.getDefault()) is used. -->
 *     <apiKey>YOUR_API_KEY</apiKey>
 *     <!-- Optional, for self hosted installs. -->
 *     <serverUrl>https://exceptionless.example.com</serverUrl>
 *     <!-- Optional (default warn). The project's @@log settings override it. -->
 *     <minimumLevel>warn</minimumLevel>
 *   </appender>
 *
 *   <root level="INFO">
 *     <appender-ref ref="CONSOLE" />
 *     <appender-ref ref="EXCEPTIONLESS" />
 *   </root>
 * </configuration>
 * }</pre>
 *
 * <p>Events with an exception become error events; the rest become log events whose source is the
 * logger name. MDC entries, SLF4J key-value pairs and the thread name are added as properties and
 * markers as tags. A message is sent when its level is at least {@code minimumLevel}, unless the
 * project's {@code @@log:<logger>} settings say otherwise (in either direction). Logback's own
 * logger levels and filters still apply first, so a logger must be enabled at a level for the
 * project settings to lower the threshold to it.
 *
 * <p><b>Client.</b> With an {@code apiKey}, the appender creates its own client (configured from
 * the environment like {@link ExceptionlessConfiguration#fromEnvironment()}, without the uncaught
 * exception handler) on first use and closes it, sending queued events, when the appender stops.
 * Otherwise it uses {@link Exceptionless#getDefault() the default client} once the application has
 * started it, so logging never starts or locks the default client's configuration; messages logged
 * before then are not sent. {@link #setClient} uses a specific client instead.
 *
 * <p>The client's own diagnostics (loggers under {@code com.exceptionless} and anything logged on
 * its background threads) and messages logged while an event is being submitted are never sent.
 * Appending never blocks on the network and never throws.
 */
public class ExceptionlessAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  /** The submission method recorded on events sent by this appender. */
  public static final String SUBMISSION_METHOD = "logback";

  private static final String INTERNAL_LOGGER_PREFIX = "com.exceptionless";
  private static final String INTERNAL_THREAD_PREFIX = "exceptionless-";
  private static final ThreadLocal<Boolean> APPENDING = new ThreadLocal<>();

  private final Object clientLock = new Object();
  private volatile @Nullable String apiKey;
  private volatile @Nullable String serverUrl;
  private volatile LogLevel minimumLevel = LogLevel.WARN;
  private volatile boolean includeMdc = true;
  private volatile @Nullable ExceptionlessClient client;
  private volatile @Nullable ExceptionlessClient ownedClient;
  private volatile boolean clientFailed;
  private volatile boolean reportedFailure;

  public @Nullable String getApiKey() {
    return apiKey;
  }

  /** The project API key. When set, the appender creates and owns its own client. */
  public void setApiKey(@Nullable String apiKey) {
    this.apiKey = trimToNull(apiKey);
  }

  public @Nullable String getServerUrl() {
    return serverUrl;
  }

  /** The server URL for self hosted installs. Only used together with {@link #setApiKey}. */
  public void setServerUrl(@Nullable String serverUrl) {
    this.serverUrl = trimToNull(serverUrl);
  }

  public String getMinimumLevel() {
    return minimumLevel.value();
  }

  /**
   * The minimum level sent when the project has no matching {@code @@log} setting (default {@code
   * warn}). Accepts Exceptionless and Logback level names; {@code off} sends only what the project
   * settings enable.
   */
  public void setMinimumLevel(@Nullable String minimumLevel) {
    LogLevel level = LogLevel.parse(minimumLevel);
    if (level == null) {
      addWarn("Unknown minimumLevel '" + minimumLevel + "'; using " + this.minimumLevel.value());
      return;
    }
    this.minimumLevel = level;
  }

  public boolean isIncludeMdc() {
    return includeMdc;
  }

  /** Whether MDC entries are added as event properties (default true). */
  public void setIncludeMdc(boolean includeMdc) {
    this.includeMdc = includeMdc;
  }

  /**
   * The client the appender sends to: the one passed to {@link #setClient}, the one it created for
   * its {@code apiKey}, or {@code null} when it uses the default client or hasn't sent anything
   * yet.
   */
  public @Nullable ExceptionlessClient getClient() {
    ExceptionlessClient explicit = client;
    return explicit != null ? explicit : ownedClient;
  }

  /**
   * Sends to the given client. The appender doesn't close it. Takes precedence over the API key.
   */
  public void setClient(@Nullable ExceptionlessClient client) {
    this.client = client;
  }

  @Override
  public void start() {
    clientFailed = false;
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
    ExceptionlessClient owned;
    synchronized (clientLock) {
      owned = ownedClient;
      ownedClient = null;
    }
    if (owned != null) {
      try {
        owned.close();
      } catch (RuntimeException e) {
        addError("Unable to close the Exceptionless client", e);
      }
    }
  }

  @Override
  protected void append(ILoggingEvent event) {
    if (APPENDING.get() != null || isInternal(event.getLoggerName(), event.getThreadName())) {
      return;
    }
    APPENDING.set(Boolean.TRUE);
    try {
      ExceptionlessClient target = resolveClient();
      if (target == null) {
        return;
      }
      String loggerName = event.getLoggerName();
      LogLevel level = toLogLevel(event.getLevel());
      if (!target.isLogLevelEnabled(loggerName, level, minimumLevel)
          || !target.getConfiguration().isEnabled()) {
        return;
      }
      createEvent(target, event, level).submit();
    } catch (RuntimeException | LinkageError e) {
      if (!reportedFailure) {
        reportedFailure = true;
        addError("Unable to send a log event to Exceptionless", e);
      }
    } finally {
      APPENDING.remove();
    }
  }

  private EventBuilder createEvent(
      ExceptionlessClient target, ILoggingEvent event, LogLevel level) {
    String loggerName = event.getLoggerName();
    String message = event.getFormattedMessage();
    if (message == null) {
      message = "";
    }
    IThrowableProxy proxy = event.getThrowableProxy();
    Throwable exception = proxy instanceof ThrowableProxy p ? p.getThrowable() : null;

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
      if (proxy != null) {
        // Deserialized events (e.g. from a socket appender) only carry the formatted stack trace.
        builder.setProperty("exception", ThrowableProxyUtil.asString(proxy));
      }
    }
    builder.setSubmissionMethod(SUBMISSION_METHOD);

    if (includeMdc) {
      Map<String, String> mdc = event.getMDCPropertyMap();
      if (mdc != null) {
        mdc.forEach(builder::setProperty);
      }
    }
    List<KeyValuePair> pairs = event.getKeyValuePairs();
    if (pairs != null) {
      for (KeyValuePair pair : pairs) {
        if (pair != null && pair.key != null) {
          builder.setProperty(pair.key, pair.value);
        }
      }
    }
    String thread = event.getThreadName();
    if (thread != null) {
      builder.setProperty("thread", thread);
    }
    List<Marker> markers = event.getMarkerList();
    if (markers != null) {
      for (Marker marker : markers) {
        if (marker != null) {
          builder.addTags(marker.getName());
        }
      }
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
    if (owned != null || clientFailed || !isStarted()) {
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
          addError("Unable to create the Exceptionless client", e);
        }
      }
      return ownedClient;
    }
  }

  /** Maps a Logback level to an Exceptionless level. */
  static LogLevel toLogLevel(@Nullable Level level) {
    if (level == null) {
      return LogLevel.INFO;
    }
    int value = level.toInt();
    if (value >= Level.ERROR_INT) {
      return LogLevel.ERROR;
    }
    if (value >= Level.WARN_INT) {
      return LogLevel.WARN;
    }
    if (value >= Level.INFO_INT) {
      return LogLevel.INFO;
    }
    if (value >= Level.DEBUG_INT) {
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
}
