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
import com.exceptionless.logging.LogEventSupport;
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
 * before then are not sent. {@link #setClient} uses a specific client instead. See {@link
 * LogEventSupport}, which the Log4j 2 and {@code java.util.logging} integrations share.
 *
 * <p>The client's own diagnostics (loggers under {@code com.exceptionless} and anything logged on
 * its background threads) and messages logged while an event is being submitted are never sent.
 * Appending never blocks on the network and never throws; the first failure after the appender
 * starts is reported to Logback's status manager.
 */
public final class ExceptionlessAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  /** The submission method recorded on events sent by this appender. */
  public static final String SUBMISSION_METHOD = "logback";

  private final LogEventSupport support = new LogEventSupport(SUBMISSION_METHOD, this::addError);
  private volatile boolean includeMdc = true;

  public @Nullable String getApiKey() {
    return support.getApiKey();
  }

  /** The project API key. When set, the appender creates and owns its own client. */
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
   * The minimum level sent when the project has no matching {@code @@log} setting (default {@code
   * warn}). Accepts Exceptionless and Logback level names; {@code off} sends only what the project
   * settings enable. A String (not {@link LogLevel}) so it can be set from {@code logback.xml}.
   */
  public void setMinimumLevel(@Nullable String minimumLevel) {
    if (!support.setMinimumLevel(minimumLevel)) {
      addWarn("Unknown minimumLevel '" + minimumLevel + "'; using " + support.getMinimumLevel());
    }
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
    return support.getClient();
  }

  /**
   * Sends to the given client. The appender doesn't close it. Takes precedence over the API key.
   */
  public void setClient(@Nullable ExceptionlessClient client) {
    support.setClient(client);
  }

  @Override
  public void start() {
    support.start();
    super.start();
  }

  /** Stops the appender and closes the client it created, sending queued events. */
  @Override
  public void stop() {
    super.stop();
    support.close();
  }

  @Override
  protected void append(ILoggingEvent event) {
    LogLevel level = toLogLevel(event.getLevel());
    support.submit(
        event.getLoggerName(), event.getThreadName(), level, client -> createEvent(client, event));
  }

  private EventBuilder createEvent(ExceptionlessClient client, ILoggingEvent event) {
    IThrowableProxy proxy = event.getThrowableProxy();
    Throwable exception = proxy instanceof ThrowableProxy p ? p.getThrowable() : null;
    EventBuilder builder =
        support.createEvent(
            client,
            event.getLoggerName(),
            event.getFormattedMessage(),
            toLogLevel(event.getLevel()),
            exception,
            event.getThreadName());
    if (exception == null && proxy != null) {
      // Deserialized events (e.g. from a socket appender) only carry the formatted stack trace.
      builder.setProperty("exception", ThrowableProxyUtil.asString(proxy));
    }

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

  /**
   * Maps a Logback level to an Exceptionless level. Logback has no fatal level, so {@code OFF} (the
   * most severe) maps to error and {@code ALL} to trace; {@code null} maps to info.
   */
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
}
