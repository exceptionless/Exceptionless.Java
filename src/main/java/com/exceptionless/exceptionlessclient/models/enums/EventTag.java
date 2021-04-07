package com.exceptionless.exceptionlessclient.models.enums;

public enum EventTag {
  CRITICAL("Critical");

  private String value;

  EventTag(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}