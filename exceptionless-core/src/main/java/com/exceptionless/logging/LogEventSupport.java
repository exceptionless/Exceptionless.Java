package com.exceptionless.logging;

import com.exceptionless.EventBuilder;
import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * The client handling shared by logging integrations (appenders and handlers), so they all behave
 * the same. Each appender owns one instance, configures it from its own settings and calls {@link
 * #submit} for every log event:
 *
 * <pre>{@code
 * public final class MyAppender extends SomeFrameworkAppender {
 *   private final LogEventSupport support = new LogEventSupport("myFramework", this::reportError);
 *
 *   public void setApiKey(String apiKey) { support.setApiKey(apiKey); }
 *
 *   public void start() { support.start(); super.start(); }
 *
 *   public void stop() { super.stop(); support.close(); }
 *
 *   protected void append(MyEvent event) {
 *     LogLevel level = toLogLevel(event.getLevel());
 *     support.submit(event.getLoggerName(), event.getThreadName(), level,
 *         client -> support
 *             .createEvent(client, event.getLoggerName(), event.getMessage(), level,
 *                 event.getThrown(), event.getThreadName())
 *             .setProperty("custom", event.getCustomValue()));
 *   }
 * }
 * }</pre>
 *
 * <p><b>Client.</b> Events go to, in order of precedence:
 *
 * <ol>
 *   <li>the client passed to {@link #setClient}, which is never closed here;
 *   <li>with an {@link #setApiKey API key}, a client created on first use (configured like {@link
 *       ExceptionlessConfiguration#fromEnvironment()}, plus the {@link #setServerUrl server URL},
 *       without the uncaught exception handler) and closed, sending queued events, by {@link
 *       #close()}. If it can't be created, the error is reported once and no new attempt is made
 *       until {@link #start()} or a new API key or server URL;
 *   <li>otherwise {@link Exceptionless#getDefault() the default client}, once the application has
 *       started it, so logging never creates, starts or locks the default client's configuration.
 * </ol>
 *
 * <p><b>Filtering.</b> {@link #submit} drops events while stopped, events from the client's own
 * loggers and background threads ({@link #isInternal}), events logged on a thread that is already
 * submitting an event (for example from a plugin or a submitting event handler; this guard is
 * shared by every integration), events below the minimum level or the project's {@code @@log}
 * settings, and everything while the client is disabled (no API key).
 *
 * <p><b>Errors.</b> {@link #submit} never throws. The first failure after {@link #start()} is
 * reported to the {@link ErrorReporter} (usually the framework's status or error handler); later
 * ones are only returned, so a broken client doesn't flood the framework's own error output. This
 * class is thread safe.
 */
public final class LogEventSupport implements AutoCloseable {
  /** Loggers whose names start with this (the client's own diagnostics) are never sent. */
  public static final String CLIENT_LOGGER_PREFIX = "com.exceptionless";

  /**
   * The client's background threads are named with this prefix ({@code exceptionless-1}, ...);
   * anything logged on them is never sent.
   */
  public static final String CLIENT_THREAD_PREFIX = "exceptionless-";

  /** Set while the current thread submits an event, to drop events logged meanwhile. */
  private static final ThreadLocal<Boolean> SUBMITTING = new ThreadLocal<>();

  /** Receives errors that the framework should show in its own status output. */
  @FunctionalInterface
  public interface ErrorReporter {
    void report(String message, Throwable error);
  }

  /** Creates the event for a log entry, given the client it is sent with. */
  @FunctionalInterface
  public interface EventFactory {
    EventBuilder create(ExceptionlessClient client);
  }

  private final String submissionMethod;
  private final ErrorReporter errorReporter;
  private final Object lock = new Object();
  private final AtomicBoolean reportedSubmitFailure = new AtomicBoolean();

  private volatile @Nullable String apiKey;
  private volatile @Nullable String serverUrl;
  private volatile LogLevel minimumLevel = LogLevel.WARN;
  private volatile @Nullable ExceptionlessClient client;
  private volatile @Nullable ExceptionlessClient ownedClient;
  private volatile boolean ownedClientFailed;
  private volatile boolean started;

  /**
   * @param submissionMethod the {@code @submission_method} recorded on events, e.g. {@code logback}
   * @param errorReporter receives failures to create, use or close the client
   */
  public LogEventSupport(String submissionMethod, ErrorReporter errorReporter) {
    this.submissionMethod = Objects.requireNonNull(submissionMethod, "submissionMethod");
    this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter");
  }

  /** The {@code @submission_method} recorded on events. */
  public String getSubmissionMethod() {
    return submissionMethod;
  }

  public @Nullable String getApiKey() {
    return apiKey;
  }

  /**
   * The project API key. When set (and no {@link #setClient client} is), a client is created and
   * owned for it. Blank values count as unset. Changing it closes a client created for the old key.
   */
  public void setApiKey(@Nullable String apiKey) {
    String value = trimToNull(apiKey);
    if (!Objects.equals(value, this.apiKey)) {
      this.apiKey = value;
      resetOwnedClient();
    }
  }

  public @Nullable String getServerUrl() {
    return serverUrl;
  }

  /**
   * The server URL for self hosted installs, used with {@link #setApiKey}. Blank values count as
   * unset. Changing it closes a client created for the old URL.
   */
  public void setServerUrl(@Nullable String serverUrl) {
    String value = trimToNull(serverUrl);
    if (!Objects.equals(value, this.serverUrl)) {
      this.serverUrl = value;
      resetOwnedClient();
    }
  }

  public LogLevel getMinimumLevel() {
    return minimumLevel;
  }

  /**
   * The minimum level sent when the project has no matching {@code @@log} setting (default {@link
   * LogLevel#WARN}). {@link LogLevel#OFF} sends only what the project settings enable.
   */
  public void setMinimumLevel(LogLevel minimumLevel) {
    this.minimumLevel = Objects.requireNonNull(minimumLevel, "minimumLevel");
  }

  /**
   * Sets the minimum level by name, accepting the names {@link LogLevel#parse} does (Exceptionless,
   * Log4j, Logback and {@code java.util.logging} level names).
   *
   * @return false, leaving the level unchanged, when the name isn't recognized
   */
  public boolean setMinimumLevel(@Nullable String minimumLevel) {
    LogLevel level = LogLevel.parse(minimumLevel);
    if (level == null) {
      return false;
    }
    this.minimumLevel = level;
    return true;
  }

  /**
   * The client set with {@link #setClient}, else the one created for the API key, else {@code null}
   * (the default client is used, or nothing has been sent yet).
   */
  public @Nullable ExceptionlessClient getClient() {
    ExceptionlessClient explicit = client;
    return explicit != null ? explicit : ownedClient;
  }

  /** Sends to the given client, which is never closed here. Takes precedence over the API key. */
  public void setClient(@Nullable ExceptionlessClient client) {
    this.client = client;
  }

  public boolean isStarted() {
    return started;
  }

  /** Starts accepting events and clears earlier failures, so new ones are reported again. */
  public void start() {
    synchronized (lock) {
      ownedClientFailed = false;
      reportedSubmitFailure.set(false);
      started = true;
    }
  }

  /**
   * Stops accepting events and closes the client created for the API key, sending queued events
   * (waiting up to the client's {@link ExceptionlessConfiguration#getShutdownTimeout() shutdown
   * timeout}). Never throws. {@link #start()} can be called again afterwards.
   */
  @Override
  public void close() {
    close(null);
  }

  /**
   * Like {@link #close()}, but waits at most {@code timeout} for queued events to be sent; {@code
   * null} or a non-positive timeout uses the client's shutdown timeout.
   */
  public void close(@Nullable Duration timeout) {
    ExceptionlessClient owned;
    synchronized (lock) {
      started = false;
      owned = ownedClient;
      ownedClient = null;
    }
    closeOwned(owned, timeout);
  }

  /**
   * The client an event would be sent to now (see the class documentation), or {@code null} when
   * there is none yet. May create the client for the API key.
   */
  public @Nullable ExceptionlessClient resolveClient() {
    ExceptionlessClient explicit = client;
    if (explicit != null) {
      return explicit;
    }
    if (apiKey == null) {
      return startedDefaultClient();
    }
    ExceptionlessClient owned = ownedClient;
    if (owned != null || ownedClientFailed || !started) {
      return owned;
    }
    synchronized (lock) {
      String key = apiKey;
      if (ownedClient == null && !ownedClientFailed && started && key != null) {
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
          ownedClientFailed = true;
          errorReporter.report("Unable to create the Exceptionless client", e);
        }
      }
      return ownedClient;
    }
  }

  /** The default client when it exists and the application has started it, else {@code null}. */
  public static @Nullable ExceptionlessClient startedDefaultClient() {
    if (!Exceptionless.hasDefault()) {
      return null;
    }
    ExceptionlessClient defaultClient = Exceptionless.getDefault();
    return defaultClient.isStarted() ? defaultClient : null;
  }

  /**
   * Submits the event {@code factory} creates, unless it is filtered out (see the class
   * documentation). The factory only runs when the event will be sent, so it is the place for
   * expensive work such as formatting the message. Never throws.
   *
   * @param loggerName the logger name, used for the {@code @@log} settings and as the source
   * @param threadName the name of the thread that logged the event
   * @param level the event's level, mapped from the framework's level
   * @return the failure when creating or submitting the event threw, else {@code null}; only the
   *     first failure after {@link #start()} is also sent to the {@link ErrorReporter}
   */
  public @Nullable Throwable submit(
      @Nullable String loggerName,
      @Nullable String threadName,
      LogLevel level,
      EventFactory factory) {
    if (!started || SUBMITTING.get() != null || isInternal(loggerName, threadName)) {
      return null;
    }
    SUBMITTING.set(Boolean.TRUE);
    try {
      ExceptionlessClient target = resolveClient();
      if (target == null
          || !target.getConfiguration().isEnabled()
          || !target.isLogLevelEnabled(loggerName, level, minimumLevel)) {
        return null;
      }
      factory.create(target).submit();
      return null;
    } catch (RuntimeException | LinkageError e) {
      if (reportedSubmitFailure.compareAndSet(false, true)) {
        errorReporter.report("Unable to send a log event to Exceptionless", e);
      }
      return e;
    } finally {
      SUBMITTING.remove();
    }
  }

  /**
   * Creates the event for a log entry: an error event with the logger name as source, the message
   * and the level when there is an exception, otherwise a log event. Sets the submission method and
   * the {@code thread} property.
   */
  public EventBuilder createEvent(
      ExceptionlessClient client,
      @Nullable String loggerName,
      @Nullable String message,
      LogLevel level,
      @Nullable Throwable exception,
      @Nullable String threadName) {
    String text = message != null ? message : "";
    EventBuilder builder =
        exception != null
            ? client
                .createException(exception)
                .setSource(loggerName)
                .setMessage(text)
                .setLevel(level)
            : client.createLog(loggerName, text, level);
    builder.setSubmissionMethod(submissionMethod);
    if (threadName != null) {
      builder.setProperty("thread", threadName);
    }
    return builder;
  }

  /**
   * True for the client's own diagnostics: loggers named {@value #CLIENT_LOGGER_PREFIX} or below
   * it, and threads whose names start with {@value #CLIENT_THREAD_PREFIX}.
   */
  public static boolean isInternal(@Nullable String loggerName, @Nullable String threadName) {
    if (threadName != null && threadName.startsWith(CLIENT_THREAD_PREFIX)) {
      return true;
    }
    return loggerName != null
        && loggerName.startsWith(CLIENT_LOGGER_PREFIX)
        && (loggerName.length() == CLIENT_LOGGER_PREFIX.length()
            || loggerName.charAt(CLIENT_LOGGER_PREFIX.length()) == '.');
  }

  private void resetOwnedClient() {
    ExceptionlessClient owned;
    synchronized (lock) {
      owned = ownedClient;
      ownedClient = null;
      ownedClientFailed = false;
    }
    closeOwned(owned, null);
  }

  private void closeOwned(@Nullable ExceptionlessClient owned, @Nullable Duration timeout) {
    if (owned == null) {
      return;
    }
    if (timeout != null && !timeout.isNegative() && !timeout.isZero()) {
      try {
        // close() waits up to the shutdown timeout for queued events to be sent.
        owned.getConfiguration().shutdownTimeout(timeout);
      } catch (RuntimeException e) {
        // Keep the configured shutdown timeout.
      }
    }
    try {
      owned.close();
    } catch (RuntimeException e) {
      errorReporter.report("Unable to close the Exceptionless client", e);
    }
  }

  private static @Nullable String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
