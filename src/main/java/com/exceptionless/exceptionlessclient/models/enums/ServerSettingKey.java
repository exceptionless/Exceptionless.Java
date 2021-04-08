package com.exceptionless.exceptionlessclient.models.enums;

public enum ServerSettingKey {
  DATA_EXCLUSIONS("@@DataExclusions"),
  USER_AGENT_BOT_PATTERNS("@@UserAgentBotPatterns");

  private final String value;

  ServerSettingKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
