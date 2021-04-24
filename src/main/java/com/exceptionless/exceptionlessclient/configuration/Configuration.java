package com.exceptionless.exceptionlessclient.configuration;

import lombok.Builder;
import lombok.Getter;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class Configuration {
  private static final String DEFAULT_SERVER_URL = "https://collector.exceptionless.io";
  private static final String DEFAULT_HEARTBEAT_SERVER_URL = "https://heartbeat.exceptionless.io";
  private static final String DEFAULT_CONFIG_SERVER_URL = "https://config.exceptionless.io";
  private static final Long DEFAULT_UPDATE_SETTINGS_WHEN_IDLE_INTERVAL = 12000L;
  private static final Integer DEFAULT_SUBMISSION_BATCH_SIZE = 50;
  private static final Integer DEFAULT_SUBMISSION_CLIENT_TIMEOUT_IN_MILLIS = 500;
  private static final Integer DEFAULT_SETTINGS_CLIENT_TIMEOUT_IN_MILLIS = 500;

  public static class Property {
    public static final String API_KEY = "apiKey";
    public static final String SERVER_URL = "serverUrl";
    public static final String HEART_BEAT_SERVER_URL = "heartbeatServerUrl";
    public static final String UPDATE_SETTINGS_WHEN_IDLE_INTERVAL =
        "updateSettingsWhenIdleInterval";
    public static final String SUBMISSION_BATCH_SIZE = "submissionBatchSize";
    public static final String SUBMISSION_CLIENT_TIMEOUT_IN_MILLIS =
        "submissionClientTimeoutInMillis";
    public static final String SETTINGS_CLIENT_TIMEOUT_IN_MILLIS = "settingsClientTimeoutInMillis";
  }

  @Getter private String apiKey;
  @Getter private String serverUrl;
  @Getter private String heartbeatServerUrl;
  @Getter private String configServerUrl;
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
    this.serverUrl = serverUrl == null ? DEFAULT_SERVER_URL : serverUrl;
    this.heartbeatServerUrl =
        heartbeatServerUrl == null
            ? (serverUrl == null ? DEFAULT_HEARTBEAT_SERVER_URL : serverUrl)
            : heartbeatServerUrl;
    this.configServerUrl =
        configServerUrl == null
            ? (serverUrl == null ? DEFAULT_CONFIG_SERVER_URL : serverUrl)
            : configServerUrl;
    this.updateSettingsWhenIdleInterval =
        updateSettingsWhenIdleInterval == null
            ? DEFAULT_UPDATE_SETTINGS_WHEN_IDLE_INTERVAL
            : updateSettingsWhenIdleInterval;
    this.submissionBatchSize =
        submissionBatchSize == null ? DEFAULT_SUBMISSION_BATCH_SIZE : submissionBatchSize;
    this.submissionClientTimeoutInMillis =
        submissionClientTimeoutInMillis == null
            ? DEFAULT_SUBMISSION_CLIENT_TIMEOUT_IN_MILLIS
            : submissionClientTimeoutInMillis;
    this.settingsClientTimeoutInMillis =
        settingsClientTimeoutInMillis == null
            ? DEFAULT_SETTINGS_CLIENT_TIMEOUT_IN_MILLIS
            : settingsClientTimeoutInMillis;
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
    propertyChangeSupport.firePropertyChange(Property.API_KEY, prevValue, apiKey);
  }

  public void setServerUrl(String serverUrl) {
    String prevValue = this.serverUrl;
    this.serverUrl = serverUrl;
    propertyChangeSupport.firePropertyChange(Property.SERVER_URL, prevValue, serverUrl);
  }

  public void setHeartbeatServerUrl(String heartbeatServerUrl) {
    String prevValue = this.heartbeatServerUrl;
    this.heartbeatServerUrl = heartbeatServerUrl;
    propertyChangeSupport.firePropertyChange(
        Property.HEART_BEAT_SERVER_URL, prevValue, heartbeatServerUrl);
  }

  public void setUpdateSettingsWhenIdleInterval(Long updateSettingsWhenIdleInterval) {
    Long prevValue = this.updateSettingsWhenIdleInterval;
    this.updateSettingsWhenIdleInterval = updateSettingsWhenIdleInterval;
    propertyChangeSupport.firePropertyChange(
        Property.UPDATE_SETTINGS_WHEN_IDLE_INTERVAL, prevValue, updateSettingsWhenIdleInterval);
  }

  public void setSubmissionBatchSize(Integer submissionBatchSize) {
    Integer prevValue = this.submissionBatchSize;
    this.submissionBatchSize = submissionBatchSize;
    propertyChangeSupport.firePropertyChange(
        Property.SUBMISSION_BATCH_SIZE, prevValue, submissionBatchSize);
  }

  public void setSubmissionClientTimeoutInMillis(Integer submissionClientTimeoutInMillis) {
    Integer prevValue = this.submissionClientTimeoutInMillis;
    this.submissionClientTimeoutInMillis = submissionClientTimeoutInMillis;
    propertyChangeSupport.firePropertyChange(
        Property.SUBMISSION_CLIENT_TIMEOUT_IN_MILLIS, prevValue, submissionClientTimeoutInMillis);
  }

  public void setSettingsClientTimeoutInMillis(Integer settingsClientTimeoutInMillis) {
    Integer prevValue = this.settingsClientTimeoutInMillis;
    this.settingsClientTimeoutInMillis = settingsClientTimeoutInMillis;
    propertyChangeSupport.firePropertyChange(
        Property.SETTINGS_CLIENT_TIMEOUT_IN_MILLIS, prevValue, settingsClientTimeoutInMillis);
  }
}
