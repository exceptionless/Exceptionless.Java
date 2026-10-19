package com.exceptionless.log4j2;

import com.exceptionless.EventBuilder;
import com.exceptionless.Exceptionless;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.LogLevel;
import com.exceptionless.logging.LogEventSupport;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.message.MapMessage;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.StructuredDataMessage;
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
 * logger name. Thread context (MDC) entries, the fields of {@link MapMessage}s (including {@code
 * StructuredDataMessage}s) and the thread name are added as properties and the marker as a tag. A
 * message is sent when its level is at least {@code minimumLevel}, unless the project's
 * {@code @@log:<logger>} settings say otherwise (in either direction). Log4j's own logger levels
 * and filters still apply first, so a logger must be enabled at a level for the project settings to
 * lower the threshold to it.
 *
 * <p><b>Client.</b> With an {@code apiKey}, the appender creates its own client (configured from
 * the environment like {@link ExceptionlessConfiguration#fromEnvironment()}, without the uncaught
 * exception handler) on first use and closes it, sending queued events, when the appender stops.
 * Otherwise it uses {@link Exceptionless#getDefault() the default client} once the application has
 * started it, so logging never starts or locks the default client's configuration; messages logged
 * before then are not sent. {@link Builder#setClient} uses a specific client instead. See {@link
 * LogEventSupport}, which the Logback and {@code java.util.logging} integrations share.
 *
 * <p>The client's own diagnostics (loggers under {@code com.exceptionless} and anything logged on
 * its background threads) and messages logged while an event is being submitted are never sent.
 * Appending never blocks on the network. The first failure after the appender starts is reported to
 * the appender's error handler; with {@code ignoreExceptions="false"} every failure is also thrown
 * as an {@link AppenderLoggingException}, so that, for example, a {@code Failover} appender can
 * take over.
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

  private final LogEventSupport support = new LogEventSupport(SUBMISSION_METHOD, this::error);
  private final boolean includeMdc;

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
    support.setApiKey(apiKey);
    support.setServerUrl(serverUrl);
    support.setMinimumLevel(minimumLevel);
    support.setClient(client);
    this.includeMdc = includeMdc;
  }

  /** Creates a builder; used by Log4j to create the appender from configuration. */
  @PluginBuilderFactory
  public static <B extends Builder<B>> B newBuilder() {
    return new Builder<B>().asBuilder();
  }

  public @Nullable String getApiKey() {
    return support.getApiKey();
  }

  public @Nullable String getServerUrl() {
    return support.getServerUrl();
  }

  public LogLevel getMinimumLevel() {
    return support.getMinimumLevel();
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
    return support.getClient();
  }

  @Override
  public void start() {
    support.start();
    super.start();
  }

  @Override
  public void append(LogEvent event) {
    LogLevel level = toLogLevel(event.getLevel());
    Throwable failure =
        support.submit(
            event.getLoggerName(),
            event.getThreadName(),
            level,
            client -> createEvent(client, event, level));
    if (failure != null && !ignoreExceptions()) {
      throw new AppenderLoggingException("Unable to send a log event to Exceptionless", failure);
    }
  }

  private EventBuilder createEvent(ExceptionlessClient client, LogEvent event, LogLevel level) {
    Message message = event.getMessage();
    String text;
    if (message instanceof StructuredDataMessage structured) {
      // The fields become properties, so only send the free text, not the RFC 5424 rendering.
      text = structured.getFormat();
    } else {
      text = message != null ? message.getFormattedMessage() : null;
    }
    EventBuilder builder =
        support.createEvent(
            client, event.getLoggerName(), text, level, event.getThrown(), event.getThreadName());
    if (message instanceof MapMessage<?, ?> map) {
      map.getData().forEach(builder::setProperty);
    }
    if (includeMdc) {
      ReadOnlyStringMap contextData = event.getContextData();
      if (contextData != null && !contextData.isEmpty()) {
        contextData.forEach((key, value) -> builder.setProperty(key, value));
      }
    }
    Marker marker = event.getMarker();
    if (marker != null) {
      builder.addTags(marker.getName());
    }
    return builder;
  }

  /**
   * Stops the appender and closes the client it created, waiting up to {@code timeout} (or, when it
   * isn't positive, the client's shutdown timeout) for queued events to be sent.
   */
  @Override
  public boolean stop(long timeout, TimeUnit timeUnit) {
    setStopping();
    boolean stopped = super.stop(timeout, timeUnit, false);
    support.close(timeout > 0 ? Duration.ofNanos(timeUnit.toNanos(timeout)) : null);
    setStopped();
    return stopped;
  }

  /**
   * Maps a Log4j level to an Exceptionless level by severity, so custom levels map to the nearest
   * standard level. {@code OFF} (the most severe) maps to fatal, {@code ALL} to trace and {@code
   * null} to info.
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
      if (minimumLevel != null && !minimumLevel.isBlank()) {
        LogLevel parsed = LogLevel.parse(minimumLevel);
        if (parsed == null) {
          LOGGER.warn(
              "Unknown minimumLevel '{}' for the Exceptionless appender; using warn", minimumLevel);
        } else {
          minimum = parsed;
        }
      }
      return new ExceptionlessAppender(
          name,
          getFilter(),
          isIgnoreExceptions(),
          getPropertyArray(),
          apiKey,
          serverUrl,
          minimum,
          includeMdc,
          client);
    }
  }
}
