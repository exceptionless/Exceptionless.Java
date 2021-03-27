package com.prashantchaubey.exceptionlessclient.models.enums;

public enum PluginContextKey {
  EXCEPTION("@@exception"),
  IS_UNHANDLED_ERROR("@@isUnhandledError"),
  SUBMISSION_METHOD("@@submissionMethod"),
  REQUEST_INFO("@@request");

  private String value;

  PluginContextKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
