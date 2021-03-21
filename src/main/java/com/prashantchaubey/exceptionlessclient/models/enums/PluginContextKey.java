package com.prashantchaubey.exceptionlessclient.models.enums;

public enum PluginContextKey {
  EXCEPTION("@@_Exception"),
  IS_UNHANDLED_ERROR("@@_IsUnhandledError"),
  SUBMISSION_METHOD("@@_SubmissionMethod"),
  REQUEST_INFO("@request");

  private String value;

  PluginContextKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
