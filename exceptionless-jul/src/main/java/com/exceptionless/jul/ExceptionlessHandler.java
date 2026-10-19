package com.exceptionless.jul;

import com.exceptionless.EventBuilder;
import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import org.jspecify.annotations.Nullable;

/**
 * A {@code java.util.logging} handler that sends log messages and exceptions to Exceptionless.
 *
 * <p>Configure it in {@code logging.properties}:
 *
 * <pre>{@code
 * handlers = java.util.logging.ConsoleHandler, com.exceptionless.jul.ExceptionlessHandler
 *
 * # Optional: without an API key the default client (Exceptionless.getDefault()) is used.
 * com.exceptionless.jul.ExceptionlessHandler.apiKey = YOUR_API_KEY
 * # Optional, for self hosted installs.
 * com.exceptionless.jul.ExceptionlessHandler.serverUrl = https://exceptionless.example.com
 * # Optional (default warn). The project's @@log settings override it.
 * com.exceptionless.jul.ExceptionlessHandler.minimumLevel = warn
 * # Optional handler level (default ALL) and filter, as for any handler.
 * com.exceptionless.jul.ExceptionlessHandler.level = ALL
 * }</pre>
 *
 * or in code:
 *
 * <pre>{@code
 * Logger.getLogger("").addHandler(new ExceptionlessHandler());
 * }</pre>
 *
 * <p>Records with an exception become error events; the rest become log events whose source is the
 * logger name. The thread name is added as a property. Levels map as {@code FINEST}/{@code FINER}
 * to trace, {@code FINE}/{@code CONFIG} to debug, {@code INFO} to info, {@code WARNING} to warn and
 * {@code SEVERE} to error. A record is sent when its level is at least {@code minimumLevel}, unless
 * the project's {@code @@log:<logger>} settings say otherwise (in either direction). The handler's
 * level and filter and the loggers' levels still apply first.
 *
 * <p><b>Client.</b> With an {@code apiKey}, the handler creates its own client (configured from the
 * environment like {@link ExceptionlessConfiguration#fromEnvironment()}, without the uncaught
 * exception handler) on first use and closes it, sending queued events, in {@link #close()}.
 * Otherwise it uses {@link Exceptionless#getDefault() the default client} once the application has
 * started it, so logging never starts or locks the default client's configuration; records
 * published before then are not sent. {@link #setClient} uses a specific client instead.
 *
 * <p>The client's own diagnostics (loggers under {@code com.exceptionless} and anything logged on
 * its background threads) and records logged while an event is being submitted are never sent.
 * Publishing never blocks on the network and never throws.
 */
public final class ExceptionlessHandler extends Handler {
  /** The submission method recorded on events sent by this handler. */
  public static final String SUBMISSION_METHOD = "jul";

  private static final String INTERNAL_LOGGER_PREFIX = "com.exceptionless";
  private static final String INTERNAL_THREAD_PREFIX = "exceptionless-";
  private static final ThreadLocal<Boolean> PUBLISHING = new ThreadLocal<>();
  private static final Formatter DEFAULT_FORMATTER = new SimpleFormatter();

  private final Object clientLock = new Object();
  private volatile @Nullable String apiKey;
  private volatile @Nullable String serverUrl;
  private volatile LogLevel minimumLevel = LogLevel.WARN;
  private volatile @Nullable ExceptionlessClient client;
  private volatile @Nullable ExceptionlessClient ownedClient;
  private volatile boolean clientFailed;
  private volatile boolean closed;

  /**
   * Creates a handler configured from the {@link LogManager} properties named after this class:
   * {@code apiKey}, {@code serverUrl}, {@code minimumLevel}, {@code level}, {@code filter} and
   * {@code formatter} (used to format messages, default {@link SimpleFormatter}).
   */
  public ExceptionlessHandler() {
    configure(ExceptionlessHandler.class.getName());
  }

  /** Creates a handler that sends to the given client, which it doesn't close. */
  public ExceptionlessHandler(ExceptionlessClient client) {
    this();
    this.client = client;
  }

  private void configure(String prefix) {
    LogManager manager = LogManager.getLogManager();
    apiKey = trimToNull(manager.getProperty(prefix + ".apiKey"));
    serverUrl = trimToNull(manager.getProperty(prefix + ".serverUrl"));
    String minimum = trimToNull(manager.getProperty(prefix + ".minimumLevel"));
    if (minimum != null) {
      setMinimumLevel(minimum);
    }

    String level = trimToNull(manager.getProperty(prefix + ".level"));
    try {
      setLevel(level != null ? Level.parse(level) : Level.ALL);
    } catch (IllegalArgumentException e) {
      reportError("Unknown level '" + level + "'", e, ErrorManager.GENERIC_FAILURE);
      setLevel(Level.ALL);
    }

    String filter = trimToNull(manager.getProperty(prefix + ".filter"));
    if (filter != null) {
      Filter instance = newInstance(filter, Filter.class);
      if (instance != null) {
        setFilter(instance);
      }
    }
    String formatter = trimToNull(manager.getProperty(prefix + ".formatter"));
    if (formatter != null) {
      Formatter instance = newInstance(formatter, Formatter.class);
      if (instance != null) {
        setFormatter(instance);
      }
    }
  }

  private <T> @Nullable T newInstance(String className, Class<T> type) {
    try {
      Class<?> loaded = Class.forName(className, true, ClassLoader.getSystemClassLoader());
      return type.cast(loaded.getDeclaredConstructor().newInstance());
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      reportError(
          "Unable to create " + type.getSimpleName() + " " + className,
          asException(e),
          ErrorManager.GENERIC_FAILURE);
      return null;
    }
  }

  public @Nullable String getApiKey() {
    return apiKey;
  }

  /**
   * The project API key. When set, the handler creates and owns its own client. Changing it closes
   * a client the handler already created.
   */
  public void setApiKey(@Nullable String apiKey) {
    this.apiKey = trimToNull(apiKey);
    resetOwnedClient();
  }

  public @Nullable String getServerUrl() {
    return serverUrl;
  }

  /** The server URL for self hosted installs. Only used together with {@link #setApiKey}. */
  public void setServerUrl(@Nullable String serverUrl) {
    this.serverUrl = trimToNull(serverUrl);
    resetOwnedClient();
  }

  public LogLevel getMinimumLevel() {
    return minimumLevel;
  }

  /**
   * The minimum level sent when the project has no matching {@code @@log} setting (default {@link
   * LogLevel#WARN}). {@link LogLevel#OFF} sends only what the project settings enable.
   */
  public void setMinimumLevel(LogLevel minimumLevel) {
    this.minimumLevel = minimumLevel;
  }

  /**
   * Sets the minimum level by name. Accepts Exceptionless and {@code java.util.logging} level names
   * such as {@code warn}, {@code warning} or {@code severe}.
   */
  public void setMinimumLevel(String minimumLevel) {
    LogLevel level = LogLevel.parse(minimumLevel);
    if (level == null) {
      reportError(
          "Unknown minimumLevel '" + minimumLevel + "'; using " + this.minimumLevel.value(),
          null,
          ErrorManager.GENERIC_FAILURE);
      return;
    }
    this.minimumLevel = level;
  }

  /**
   * The client the handler sends to: the one passed to {@link #setClient}, the one it created for
   * its {@code apiKey}, or {@code null} when it uses the default client or hasn't sent anything
   * yet.
   */
  public @Nullable ExceptionlessClient getClient() {
    ExceptionlessClient explicit = client;
    return explicit != null ? explicit : ownedClient;
  }

  /** Sends to the given client. The handler doesn't close it. Takes precedence over the API key. */
  public void setClient(@Nullable ExceptionlessClient client) {
    this.client = client;
  }

  @Override
  public void publish(@Nullable LogRecord record) {
    if (record == null || closed || PUBLISHING.get() != null) {
      return;
    }
    if (isInternal(record.getLoggerName(), Thread.currentThread().getName())
        || !isLoggable(record)) {
      return;
    }
    PUBLISHING.set(Boolean.TRUE);
    try {
      ExceptionlessClient target = resolveClient();
      if (target == null) {
        return;
      }
      String loggerName = record.getLoggerName();
      LogLevel level = toLogLevel(record.getLevel());
      if (!target.isLogLevelEnabled(loggerName, level, minimumLevel)
          || !target.getConfiguration().isEnabled()) {
        return;
      }
      createEvent(target, record, level).submit();
    } catch (RuntimeException | LinkageError e) {
      reportError(
          "Unable to send a log record to Exceptionless",
          asException(e),
          ErrorManager.WRITE_FAILURE);
    } finally {
      PUBLISHING.remove();
    }
  }

  private EventBuilder createEvent(ExceptionlessClient target, LogRecord record, LogLevel level) {
    String loggerName = record.getLoggerName();
    String message = formatMessage(record);
    Throwable exception = record.getThrown();

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
    return builder
        .setSubmissionMethod(SUBMISSION_METHOD)
        .setProperty("thread", Thread.currentThread().getName());
  }

  private String formatMessage(LogRecord record) {
    Formatter formatter = getFormatter();
    String message;
    try {
      message = (formatter != null ? formatter : DEFAULT_FORMATTER).formatMessage(record);
    } catch (RuntimeException e) {
      message = record.getMessage();
    }
    return message != null ? message : "";
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
      if (ownedClient == null && !clientFailed && !closed) {
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
          reportError("Unable to create the Exceptionless client", e, ErrorManager.OPEN_FAILURE);
        }
      }
      return ownedClient;
    }
  }

  private void resetOwnedClient() {
    ExceptionlessClient owned;
    synchronized (clientLock) {
      owned = ownedClient;
      ownedClient = null;
      clientFailed = false;
    }
    if (owned != null) {
      owned.close();
    }
  }

  /** Starts sending queued events, without waiting. */
  @Override
  public void flush() {
    ExceptionlessClient target = getClient();
    if (target != null) {
      target.processQueue();
    }
  }

  /**
   * Stops publishing and closes the client the handler created, sending queued events (waiting up
   * to the client's shutdown timeout). A client passed to {@link #setClient} is left open.
   */
  @Override
  public void close() {
    ExceptionlessClient owned;
    synchronized (clientLock) {
      closed = true;
      owned = ownedClient;
      ownedClient = null;
    }
    if (owned != null) {
      try {
        owned.close();
      } catch (RuntimeException e) {
        reportError("Unable to close the Exceptionless client", e, ErrorManager.CLOSE_FAILURE);
      }
    }
  }

  /** Maps a {@code java.util.logging} level to an Exceptionless level. */
  static LogLevel toLogLevel(@Nullable Level level) {
    if (level == null) {
      return LogLevel.INFO;
    }
    int value = level.intValue();
    if (value >= Level.SEVERE.intValue()) {
      return LogLevel.ERROR;
    }
    if (value >= Level.WARNING.intValue()) {
      return LogLevel.WARN;
    }
    if (value >= Level.INFO.intValue()) {
      return LogLevel.INFO;
    }
    if (value >= Level.FINE.intValue()) {
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

  private static Exception asException(Throwable error) {
    return error instanceof Exception e ? e : new RuntimeException(error);
  }

  private static @Nullable String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
