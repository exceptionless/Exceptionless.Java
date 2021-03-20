package com.prashantchaubey.exceptionlessclient.logging;

public class NullLog implements LogIF {
  @Override
  public void trace(String message) {}

  @Override
  public void info(String message) {}

  @Override
  public void warn(String message) {}

  @Override
  public void error(String message) {}
}
