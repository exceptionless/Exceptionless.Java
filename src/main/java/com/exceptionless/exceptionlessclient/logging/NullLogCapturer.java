package com.exceptionless.exceptionlessclient.logging;

import lombok.Builder;

public class NullLogCapturer implements LogCapturerIF {
  @Builder
  public NullLogCapturer() {}

  @Override
  public void trace(String message) {}

  @Override
  public void debug(String message) {}

  @Override
  public void info(String message) {}

  @Override
  public void warn(String message) {}

  @Override
  public void error(String message) {}

  @Override
  public void error(String message, Exception e) {}
}
