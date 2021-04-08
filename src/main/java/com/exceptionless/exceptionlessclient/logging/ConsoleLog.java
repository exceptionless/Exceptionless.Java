package com.exceptionless.exceptionlessclient.logging;

import lombok.Builder;

public class ConsoleLog implements LogIF {
  @Builder
  public ConsoleLog() {}

  @Override
  public void trace(String message) {
    System.out.printf("TRACE: %s%n", message);
  }

  @Override
  public void info(String message) {
    System.out.printf("INFO: %s%n", message);
  }

  @Override
  public void warn(String message) {
    System.out.printf("WARN: %s%n", message);
  }

  @Override
  public void error(String message) {
    System.out.printf("ERROR: %s%n", message);
  }

  @Override
  public void error(String message, Exception e) {
    System.out.printf("EXCEPTION: %s%n", message);
    e.printStackTrace();
  }
}
