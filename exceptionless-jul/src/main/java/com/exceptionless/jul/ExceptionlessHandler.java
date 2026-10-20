package com.exceptionless.jul;

import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import com.exceptionless.logging.LogEventSupport;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
 * published before then are not sent. {@link #setClient} uses a specific client instead. See {@link
 * LogEventSupport}, which the Logback and Log4j 2 integrations share.
 *
 * <p>The client's own diagnostics (loggers under {@code com.exceptionless} and anything logged on
 * its background threads) and records logged while an event is being submitted are never sent.
 * Publishing never blocks on the network and never throws; the first failure is reported to the
 * handler's {@link ErrorManager}.
 */
public final class ExceptionlessHandler extends Handler {
  /** The submission method recorded on events sent by this handler. */
  public static final String SUBMISSION_METHOD = "jul";

  private static final Formatter DEFAULT_FORMATTER = new SimpleFormatter();

  private final LogEventSupport support =
      new LogEventSupport(
          SUBMISSION_METHOD,
          (message, error) ->
              reportError(message, asException(error), ErrorManager.GENERIC_FAILURE));

  /**
   * Creates a handler configured from the {@link LogManager} properties named after this class:
   * {@code apiKey}, {@code serverUrl}, {@code minimumLevel}, {@code level}, {@code filter} and
   * {@code formatter} (used to format messages, default {@link SimpleFormatter}).
   */
  public ExceptionlessHandler() {
    configure(ExceptionlessHandler.class.getName());
    support.start();
  }

  /** Creates a handler that sends to the given client, which it doesn't close. */
  public ExceptionlessHandler(ExceptionlessClient client) {
    this();
    support.setClient(client);
  }

  private void configure(String prefix) {
    LogManager manager = LogManager.getLogManager();
    support.setApiKey(manager.getProperty(prefix + ".apiKey"));
    support.setServerUrl(manager.getProperty(prefix + ".serverUrl"));
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

  /**
   * Creates a filter or formatter by class name, looking for the class with the thread context
   * class loader (as application servers expect), this handler's class loader, then the system
   * class loader (as {@link LogManager} does).
   */
  private <T> @Nullable T newInstance(String className, Class<T> type) {
    Exception failure = null;
    for (ClassLoader loader : classLoaders()) {
      try {
        Class<?> loaded = Class.forName(className, true, loader);
        return type.cast(loaded.getDeclaredConstructor().newInstance());
      } catch (ClassNotFoundException e) {
        failure = e;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
        failure = asException(e);
        break;
      }
    }
    reportError(
        "Unable to create " + type.getSimpleName() + " " + className,
        failure,
        ErrorManager.GENERIC_FAILURE);
    return null;
  }

  private static Set<ClassLoader> classLoaders() {
    Set<ClassLoader> loaders = new LinkedHashSet<>();
    ClassLoader context = Thread.currentThread().getContextClassLoader();
    if (context != null) {
      loaders.add(context);
    }
    ClassLoader own = ExceptionlessHandler.class.getClassLoader();
    if (own != null) {
      loaders.add(own);
    }
    loaders.add(ClassLoader.getSystemClassLoader());
    return loaders;
  }

  public @Nullable String getApiKey() {
    return support.getApiKey();
  }

  /**
   * The project API key. When set, the handler creates and owns its own client. Changing it closes
   * a client the handler already created.
   */
  public void setApiKey(@Nullable String apiKey) {
    support.setApiKey(apiKey);
  }

  public @Nullable String getServerUrl() {
    return support.getServerUrl();
  }

  /** The server URL for self hosted installs. Only used together with {@link #setApiKey}. */
  public void setServerUrl(@Nullable String serverUrl) {
    support.setServerUrl(serverUrl);
  }

  public LogLevel getMinimumLevel() {
    return support.getMinimumLevel();
  }

  /**
   * The minimum level sent when the project has no matching {@code @@log} setting (default {@link
   * LogLevel#WARN}). {@link LogLevel#OFF} sends only what the project settings enable.
   */
  public void setMinimumLevel(LogLevel minimumLevel) {
    support.setMinimumLevel(minimumLevel);
  }

  /**
   * Sets the minimum level by name. Accepts Exceptionless and {@code java.util.logging} level names
   * such as {@code warn}, {@code warning} or {@code severe}.
   */
  public void setMinimumLevel(String minimumLevel) {
    if (!support.setMinimumLevel(minimumLevel)) {
      reportError(
          "Unknown minimumLevel '" + minimumLevel + "'; using " + support.getMinimumLevel(),
          null,
          ErrorManager.GENERIC_FAILURE);
    }
  }

  /**
   * The client the handler sends to: the one passed to {@link #setClient}, the one it created for
   * its {@code apiKey}, or {@code null} when it uses the default client or hasn't sent anything
   * yet.
   */
  public @Nullable ExceptionlessClient getClient() {
    return support.getClient();
  }

  /** Sends to the given client. The handler doesn't close it. Takes precedence over the API key. */
  public void setClient(@Nullable ExceptionlessClient client) {
    support.setClient(client);
  }

  @Override
  public void publish(@Nullable LogRecord record) {
    if (record == null || !support.isStarted() || !isLoggable(record)) {
      return;
    }
    String loggerName = record.getLoggerName();
    String threadName = Thread.currentThread().getName();
    LogLevel level = toLogLevel(record.getLevel());
    support.submit(
        loggerName,
        threadName,
        level,
        client ->
            support.createEvent(
                client, loggerName, formatMessage(record), level, record.getThrown(), threadName));
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

  /** Starts sending queued events, without waiting. */
  @Override
  public void flush() {
    ExceptionlessClient target = getClient();
    if (target != null) {
      CompletableFuture<Void> unused = target.processQueue();
    }
  }

  /**
   * Stops publishing and closes the client the handler created, sending queued events (waiting up
   * to the client's shutdown timeout). A client passed to {@link #setClient} is left open.
   */
  @Override
  public void close() {
    support.close();
  }

  /**
   * Maps a {@code java.util.logging} level to an Exceptionless level. {@code SEVERE} is the most
   * severe level, so {@code OFF} maps to error and {@code ALL} to trace; {@code null} maps to info.
   */
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
