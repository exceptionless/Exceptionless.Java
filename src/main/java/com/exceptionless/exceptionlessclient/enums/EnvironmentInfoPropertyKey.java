package com.exceptionless.exceptionlessclient.enums;

public enum EnvironmentInfoPropertyKey {
  LOAD_AVG("loadavg"),
  TMP_DIR("tmpdir"),
  UP_TIME("uptime"),
  ENDIANESS("endianess");

  private final String value;

  EnvironmentInfoPropertyKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
