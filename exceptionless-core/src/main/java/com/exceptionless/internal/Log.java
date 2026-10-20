package com.exceptionless.internal;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Internal diagnostics logger. Uses {@link System.Logger} so the core library has no logging
 * dependency; it reaches SLF4J/Log4j/JUL through whatever bridge the application already has.
 *
 * <p>All Exceptionless internal loggers live under the {@code com.exceptionless} name, which the
 * logging integrations ignore so the client never reports its own diagnostics.
 */
public final class Log {
  public static final String ROOT_NAME = "com.exceptionless";

  private final System.Logger logger;

  private Log(String name) {
    this.logger = System.getLogger(name);
  }

  public static Log get(Class<?> type) {
    return new Log(ROOT_NAME + "." + type.getSimpleName());
  }

  public boolean isDebugEnabled() {
    return logger.isLoggable(System.Logger.Level.DEBUG);
  }

  public void trace(Supplier<String> message) {
    logger.log(System.Logger.Level.TRACE, message);
  }

  public void debug(String message) {
    logger.log(System.Logger.Level.DEBUG, message);
  }

  public void debug(Supplier<String> message) {
    logger.log(System.Logger.Level.DEBUG, message);
  }

  public void info(String message) {
    logger.log(System.Logger.Level.INFO, message);
  }

  public void warn(String message) {
    logger.log(System.Logger.Level.WARNING, message);
  }

  public void warn(String message, @Nullable Throwable error) {
    logger.log(System.Logger.Level.WARNING, message, error);
  }

  public void error(String message) {
    logger.log(System.Logger.Level.ERROR, message);
  }

  public void error(String message, @Nullable Throwable error) {
    logger.log(System.Logger.Level.ERROR, message, error);
  }
}
