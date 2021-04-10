package com.exceptionless.exceptionlessclient.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import lombok.Builder;

public class LogCapturerAppender extends AppenderBase<ILoggingEvent> {
  private final LogCapturerIF logCapturer;

  @Builder
  public LogCapturerAppender(LogCapturerIF logCapturer) {
    this.logCapturer = logCapturer;
  }

  @Override
  protected void append(ILoggingEvent loggingEvent) {
    if (loggingEvent.getLevel().equals(Level.TRACE)) {
      logCapturer.trace(loggingEvent.getMessage());
    } else if (loggingEvent.getLevel().equals(Level.INFO)) {
      logCapturer.info(loggingEvent.getMessage());
    } else if (loggingEvent.getLevel().equals(Level.WARN)) {
      logCapturer.warn(loggingEvent.getMessage());
    } else if (loggingEvent.getLevel().equals(Level.ERROR)) {
      IThrowableProxy throwableProxy = loggingEvent.getThrowableProxy();
      if (throwableProxy instanceof ThrowableProxy) {
        Throwable throwable = ((ThrowableProxy) throwableProxy).getThrowable();
        if (throwable instanceof Exception) {
          logCapturer.error(loggingEvent.getMessage(), (Exception) throwable);
        } else {
          logCapturer.error(loggingEvent.getMessage());
        }
      } else {
        logCapturer.error(loggingEvent.getMessage());
      }
    }
  }
}
