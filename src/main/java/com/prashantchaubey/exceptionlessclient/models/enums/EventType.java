package com.prashantchaubey.exceptionlessclient.models.enums;

public enum EventType {
  ERROR("error"),
  USAGE("usage"),
  LOG("log"),
  SESSION("session"),
  NOT_FOUND("404");
  private String value;

  EventType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
