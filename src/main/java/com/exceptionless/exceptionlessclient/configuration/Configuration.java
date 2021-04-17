package com.exceptionless.exceptionlessclient.configuration;

import lombok.Builder;
import lombok.Getter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class Configuration {
  public static final String USER_AGENT = "exceptionless-java";

  @Getter private String apiKey;
  @Getter private String serverUrl;
  @Getter private String heartbeatServerUrl;
  @Getter private Long updateSettingsWhenIdleInterval;
  @Getter private Integer submissionBatchSize;
  @Getter private Integer submissionClientTimeoutInMillis;
  @Getter private Integer settingsClientTimeoutInMillis;
  private final PropertyChangeSupport propertyChangeSupport;

  @Builder
  public Configuration(
      String apiKey,
      String serverUrl,
      String configServerUrl,
      String heartbeatServerUrl,
      Long updateSettingsWhenIdleInterval,
      Integer submissionBatchSize,
      Integer submissionClientTimeoutInMillis,
      Integer settingsClientTimeoutInMillis) {
    this.apiKey = apiKey;
    this.serverUrl = serverUrl == null ? "https://collector.exceptionless.io" : serverUrl;
    this.heartbeatServerUrl =
        heartbeatServerUrl == null
            ? (serverUrl == null ? "https://heartbeat.exceptionless.io" : serverUrl)
            : heartbeatServerUrl;
    this.updateSettingsWhenIdleInterval =
        updateSettingsWhenIdleInterval == null ? 12000L : updateSettingsWhenIdleInterval;
    this.submissionBatchSize = submissionBatchSize == null ? 50 : submissionBatchSize;
    this.submissionClientTimeoutInMillis =
        submissionClientTimeoutInMillis == null ? 500 : submissionClientTimeoutInMillis;
    this.settingsClientTimeoutInMillis =
        settingsClientTimeoutInMillis == null ? 500 : settingsClientTimeoutInMillis;
    this.propertyChangeSupport = new PropertyChangeSupport(this);
  }

  public void addPropertyChangeListener(PropertyChangeListener listener) {
    propertyChangeSupport.addPropertyChangeListener(listener);
  }

  public static Configuration defaultConfiguration() {
    return Configuration.builder().build();
  }

  public void setApiKey(String apiKey) {
    String prevValue = this.apiKey;
    this.apiKey = apiKey;
    propertyChangeSupport.firePropertyChange("apiKey", prevValue, apiKey);
  }

  public void setServerUrl(String serverUrl) {
    String prevValue = this.serverUrl;
    this.serverUrl = serverUrl;
    propertyChangeSupport.firePropertyChange("serverUrl", prevValue, serverUrl);
  }


  public void setHeartbeatServerUrl(String heartbeatServerUrl) {
    String prevValue = this.heartbeatServerUrl;
    this.heartbeatServerUrl = heartbeatServerUrl;
    propertyChangeSupport.firePropertyChange("heartbeatServerUrl", prevValue, heartbeatServerUrl);
  }

  public void setUpdateSettingsWhenIdleInterval(Long updateSettingsWhenIdleInterval) {
    Long prevValue = this.updateSettingsWhenIdleInterval;
    this.updateSettingsWhenIdleInterval = updateSettingsWhenIdleInterval;
    propertyChangeSupport.firePropertyChange(
        "updateSettingsWhenIdleInterval", prevValue, updateSettingsWhenIdleInterval);
  }

  public void setSubmissionBatchSize(Integer submissionBatchSize) {
    Integer prevValue = this.submissionBatchSize;
    this.submissionBatchSize = submissionBatchSize;
    propertyChangeSupport.firePropertyChange("submissionBatchSize", prevValue, submissionBatchSize);
  }

  public void setSubmissionClientTimeoutInMillis(Integer submissionClientTimeoutInMillis) {
    Integer prevValue = this.submissionClientTimeoutInMillis;
    this.submissionClientTimeoutInMillis = submissionClientTimeoutInMillis;
    propertyChangeSupport.firePropertyChange(
        "submissionClientTimeoutInMillis", prevValue, submissionClientTimeoutInMillis);
  }

  public void setSettingsClientTimeoutInMillis(Integer settingsClientTimeoutInMillis) {
    Integer prevValue = this.settingsClientTimeoutInMillis;
    this.settingsClientTimeoutInMillis = settingsClientTimeoutInMillis;
    propertyChangeSupport.firePropertyChange(
        "settingsClientTimeoutInMillis", prevValue, settingsClientTimeoutInMillis);
  }
}
