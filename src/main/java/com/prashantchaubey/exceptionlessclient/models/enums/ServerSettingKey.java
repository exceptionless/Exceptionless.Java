package com.prashantchaubey.exceptionlessclient.models.enums;

public enum ServerSettingKey {
  DATA_EXCLUSIONS("@@DataExclusions"),
  USER_AGENT_BOT_PATTERNS("@@UserAgentBotPatterns");

  private String value;

  ServerSettingKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
