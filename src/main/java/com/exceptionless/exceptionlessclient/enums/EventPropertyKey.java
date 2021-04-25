package com.exceptionless.exceptionlessclient.enums;

public enum EventPropertyKey {
  REF("@ref"),
  USER("@user"),
  USER_DESCRIPTION("@user_description"),
  STACK("@stack"),
  LOG_LEVEL("@level"),
  ERROR("@error"),
  ENVIRONMENT("@environment"),
  EXTRA("@ext"),
  SUBMISSION_METHOD("@submission_method"),
  VERSION("@version"),
  REQUEST_INFO("@request");

  private final String value;

  EventPropertyKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
