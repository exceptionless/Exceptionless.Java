package com.prashantchaubey.exceptionlessclient.logging;

import lombok.Builder;

public class NullLog implements LogIF {
  @Builder
  public NullLog() {}

  @Override
  public void trace(String message) {}

  @Override
  public void info(String message) {}

  @Override
  public void warn(String message) {}

  @Override
  public void error(String message) {}

  @Override
  public void error(String message, Exception e) {}
}
