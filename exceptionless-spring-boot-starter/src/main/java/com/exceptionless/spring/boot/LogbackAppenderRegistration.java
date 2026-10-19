package com.exceptionless.spring.boot;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.logback.ExceptionlessAppender;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Adds an {@link ExceptionlessAppender} for the auto-configured client to the Logback root logger
 * while the application context runs. Does nothing when Logback is not the SLF4J backend, or when
 * the root logger already has an {@link ExceptionlessAppender} (e.g. from {@code
 * logback-spring.xml}).
 */
public final class LogbackAppenderRegistration implements DisposableBean {
  /** The name of the appender added to the root logger. */
  public static final String APPENDER_NAME = "EXCEPTIONLESS";

  /** Appenders added by registrations, to tell them apart from configured ones. */
  private static final Set<Appender<?>> REGISTERED =
      Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

  private final @Nullable ExceptionlessAppender appender;
  private final @Nullable Logger root;

  public LogbackAppenderRegistration(ExceptionlessClient client, String minimumLevel) {
    ILoggerFactory factory = LoggerFactory.getILoggerFactory();
    if (!(factory instanceof LoggerContext context)) {
      this.appender = null;
      this.root = null;
      return;
    }
    Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    for (Iterator<Appender<ILoggingEvent>> it = rootLogger.iteratorForAppenders(); it.hasNext(); ) {
      Appender<ILoggingEvent> existing = it.next();
      if (existing instanceof ExceptionlessAppender && !REGISTERED.contains(existing)) {
        // Configured explicitly; leave it alone.
        this.appender = null;
        this.root = null;
        return;
      }
    }
    ExceptionlessAppender added = new ExceptionlessAppender();
    added.setContext(context);
    added.setName(APPENDER_NAME);
    added.setClient(client);
    added.setMinimumLevel(minimumLevel);
    added.start();
    REGISTERED.add(added);
    rootLogger.addAppender(added);
    this.appender = added;
    this.root = rootLogger;
  }

  /** The appender added to the root logger, or {@code null} when none was added. */
  public @Nullable ExceptionlessAppender getAppender() {
    return appender;
  }

  @Override
  public void destroy() {
    if (appender != null && root != null) {
      root.detachAppender(appender);
      appender.stop();
      REGISTERED.remove(appender);
    }
  }
}
