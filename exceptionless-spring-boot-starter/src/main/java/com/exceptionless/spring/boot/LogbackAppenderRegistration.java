package com.exceptionless.spring.boot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.Appender;
import com.exceptionless.ExceptionlessClient;
import com.exceptionless.LogLevel;
import com.exceptionless.logback.ExceptionlessAppender;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Adds an {@link ExceptionlessAppender} for the auto-configured client to the Logback root logger
 * once the application context's singletons exist, and removes it when the context closes. Does
 * nothing when Logback is not the SLF4J backend, or when the root logger already has an {@link
 * ExceptionlessAppender} (e.g. from {@code logback-spring.xml}).
 *
 * <p>Resetting the Logback context (for example when Logback reloads a changed configuration file
 * with {@code scan="true"}) removes all appenders, so the appender is added again on every reset.
 */
final class LogbackAppenderRegistration
    implements SmartInitializingSingleton, DisposableBean, LoggerContextListener {
  /** The name of the appender added to the root logger. */
  static final String APPENDER_NAME = "EXCEPTIONLESS";

  /** Appenders added by registrations, to tell them apart from configured ones. */
  private static final Set<Appender<?>> REGISTERED =
      Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

  private final ExceptionlessClient client;
  private final LogLevel minimumLevel;
  private final Object lock = new Object();
  private @Nullable LoggerContext context;
  private @Nullable ExceptionlessAppender appender;

  LogbackAppenderRegistration(ExceptionlessClient client, LogLevel minimumLevel) {
    this.client = client;
    this.minimumLevel = minimumLevel;
  }

  /** The appender currently on the root logger, or {@code null} when none was added. */
  @Nullable ExceptionlessAppender getAppender() {
    synchronized (lock) {
      return appender;
    }
  }

  @Override
  public void afterSingletonsInstantiated() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
      return;
    }
    synchronized (lock) {
      if (context != null || hasConfiguredAppender(root(loggerContext))) {
        return;
      }
      context = loggerContext;
      attach(loggerContext);
      loggerContext.addListener(this);
    }
  }

  @Override
  public void destroy() {
    synchronized (lock) {
      LoggerContext loggerContext = context;
      if (loggerContext != null) {
        loggerContext.removeListener(this);
        detach(loggerContext);
        context = null;
      }
    }
  }

  /** Logback removed and stopped every appender: add a new one. */
  @Override
  @SuppressWarnings("ReferenceEquality") // Only events from the context we attached to.
  public void onReset(LoggerContext loggerContext) {
    synchronized (lock) {
      if (loggerContext == context && loggerContext.isStarted()) {
        detach(loggerContext);
        attach(loggerContext);
      }
    }
  }

  /** The Logback context stopped (the JVM is shutting down): nothing more to send. */
  @Override
  @SuppressWarnings("ReferenceEquality") // Only events from the context we attached to.
  public void onStop(LoggerContext loggerContext) {
    synchronized (lock) {
      if (loggerContext == context) {
        detach(loggerContext);
        context = null;
      }
    }
  }

  @Override
  public boolean isResetResistant() {
    return true;
  }

  @Override
  public void onStart(LoggerContext loggerContext) {}

  @Override
  public void onLevelChange(Logger logger, Level level) {}

  private void attach(LoggerContext loggerContext) {
    ExceptionlessAppender added = new ExceptionlessAppender();
    added.setContext(loggerContext);
    added.setName(APPENDER_NAME);
    added.setClient(client);
    added.setMinimumLevel(minimumLevel.value());
    added.start();
    REGISTERED.add(added);
    root(loggerContext).addAppender(added);
    appender = added;
  }

  private void detach(LoggerContext loggerContext) {
    ExceptionlessAppender added = appender;
    if (added != null) {
      root(loggerContext).detachAppender(added);
      added.stop();
      REGISTERED.remove(added);
      appender = null;
    }
  }

  private static boolean hasConfiguredAppender(Logger root) {
    for (Iterator<Appender<ILoggingEvent>> it = root.iteratorForAppenders(); it.hasNext(); ) {
      Appender<ILoggingEvent> existing = it.next();
      if (existing instanceof ExceptionlessAppender && !REGISTERED.contains(existing)) {
        return true;
      }
    }
    return false;
  }

  private static Logger root(LoggerContext loggerContext) {
    return loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
  }
}
