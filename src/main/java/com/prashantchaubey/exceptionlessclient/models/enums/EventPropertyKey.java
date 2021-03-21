package com.prashantchaubey.exceptionlessclient.models.enums;

public enum EventPropertyKey {
  REF("@ref"),
  USER("@user"),
  USER_DESCRIPTION("@user_description"),
  STACK("@stack"),
  LOG_LEVEL("@level");

  private String value;

  EventPropertyKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
