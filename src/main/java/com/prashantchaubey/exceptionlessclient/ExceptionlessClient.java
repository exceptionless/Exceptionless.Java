package com.prashantchaubey.exceptionlessclient;

import com.prashantchaubey.exceptionlessclient.configuration.Configuration;

public class ExceptionlessClient {
  private static ExceptionlessClient INSTANCE;

  public static ExceptionlessClient defaultInstance() {
    if (INSTANCE == null) {
      INSTANCE = new ExceptionlessClient();
    }

    return INSTANCE;
  }

  private Configuration config;

  public Configuration getConfig() {
    return config;
  }
}
