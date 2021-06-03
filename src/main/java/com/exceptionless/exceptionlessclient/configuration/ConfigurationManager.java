package com.exceptionless.exceptionlessclient.configuration;

import ch.qos.logback.core.Context;
import com.exceptionless.exceptionlessclient.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.exceptions.InvalidApiKeyException;
import com.exceptionless.exceptionlessclient.logging.LogCapturerAppender;
import com.exceptionless.exceptionlessclient.logging.LogCapturerIF;
import com.exceptionless.exceptionlessclient.logging.NullLogCapturer;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.UserInfo;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.plugins.PluginManager;
import com.exceptionless.exceptionlessclient.plugins.preconfigured.HeartbeatPlugin;
import com.exceptionless.exceptionlessclient.queue.DefaultEventQueue;
import com.exceptionless.exceptionlessclient.queue.EventQueueIF;
import com.exceptionless.exceptionlessclient.services.DefaultLastReferenceIdManager;
import com.exceptionless.exceptionlessclient.services.LastReferenceIdManagerIF;
import com.exceptionless.exceptionlessclient.settings.DefaultSettingsClient;
import com.exceptionless.exceptionlessclient.settings.SettingsClientIF;
import com.exceptionless.exceptionlessclient.settings.SettingsManager;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import com.exceptionless.exceptionlessclient.storage.StorageProviderIF;
import com.exceptionless.exceptionlessclient.submission.DefaultSubmissionClient;
import com.exceptionless.exceptionlessclient.submission.SubmissionClientIF;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeSupport;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
public class ConfigurationManager {
  private static final Integer API_KEY_MIN_LENGTH = 11;
  private static final Integer DEFAULT_HEART_BEAT_INTERVAL_IN_SECS = 30;
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
    public static final String CONFIG_SERVER_URL = "configServerUrl";
    public static final String HEART_BEAT_SERVER_URL = "heartbeatServerUrl";
    public static final String UPDATE_SETTINGS_WHEN_IDLE_INTERVAL =
        "updateSettingsWhenIdleInterval";
    public static final String SUBMISSION_BATCH_SIZE = "submissionBatchSize";
    public static final String SUBMISSION_CLIENT_TIMEOUT_IN_MILLIS =
        "submissionClientTimeoutInMillis";
    public static final String SETTINGS_CLIENT_TIMEOUT_IN_MILLIS = "settingsClientTimeoutInMillis";
  }

  @Getter private final LastReferenceIdManagerIF lastReferenceIdManager;
  @Getter private final SubmissionClientIF submissionClient;
  @Getter private final EventQueueIF queue;
  @Getter private final Set<String> defaultTags;
  @Getter private final Map<String, Object> defaultData;
  private final List<Consumer<ConfigurationManager>> onChangedHandlers;
  @Getter private final SettingsManager settingsManager;
  private final Set<String> userAgentBotPatterns;
  @Getter private final PrivateInformationInclusions privateInformationInclusions;
  private final Set<String> dataExclusions;
  private final PluginManager pluginManager;
  @Getter private final StorageProviderIF storageProvider;
  @Getter private final ValueProvider<String> apiKey;
  @Getter private final ValueProvider<String> serverUrl;
  @Getter private final ValueProvider<String> heartbeatServerUrl;
  @Getter private final ValueProvider<String> configServerUrl;
  @Getter private final ValueProvider<Long> updateSettingsWhenIdleInterval;
  @Getter private final ValueProvider<Integer> submissionBatchSize;
  @Getter private final ValueProvider<Integer> submissionClientTimeoutInMillis;
  @Getter private final ValueProvider<Integer> settingsClientTimeoutInMillis;
  private final PropertyChangeSupport propertyChangeSupport;

  @Builder
  public ConfigurationManager(
      LastReferenceIdManagerIF lastReferenceIdManager,
      LogCapturerIF logCatpurer,
      SubmissionClientIF submissionClient,
      SettingsClientIF settingsClient,
      StorageProviderIF storageProvider,
      EventQueueIF queue,
      Integer maxQueueItems,
      Integer processingIntervalInSecs,
      String apiKey,
      String serverUrl,
      String configServerUrl,
      String heartbeatServerUrl,
      Long updateSettingsWhenIdleInterval,
      Integer submissionBatchSize,
      Integer submissionClientTimeoutInMillis,
      Integer settingsClientTimeoutInMillis) {
    this.apiKey = ValueProvider.of(apiKey);
    this.serverUrl =
        serverUrl == null ? ValueProvider.of(DEFAULT_SERVER_URL) : ValueProvider.of(serverUrl);
    this.heartbeatServerUrl =
        heartbeatServerUrl == null
            ? (serverUrl == null
                ? ValueProvider.of(DEFAULT_HEARTBEAT_SERVER_URL)
                : ValueProvider.of(serverUrl))
            : ValueProvider.of(heartbeatServerUrl);
    this.configServerUrl =
        configServerUrl == null
            ? (serverUrl == null
                ? ValueProvider.of(DEFAULT_CONFIG_SERVER_URL)
                : ValueProvider.of(serverUrl))
            : ValueProvider.of(configServerUrl);
    this.updateSettingsWhenIdleInterval =
        updateSettingsWhenIdleInterval == null
            ? ValueProvider.of(DEFAULT_UPDATE_SETTINGS_WHEN_IDLE_INTERVAL)
            : ValueProvider.of(updateSettingsWhenIdleInterval);
    this.submissionBatchSize =
        submissionBatchSize == null
            ? ValueProvider.of(DEFAULT_SUBMISSION_BATCH_SIZE)
            : ValueProvider.of(submissionBatchSize);
    this.submissionClientTimeoutInMillis =
        submissionClientTimeoutInMillis == null
            ? ValueProvider.of(DEFAULT_SUBMISSION_CLIENT_TIMEOUT_IN_MILLIS)
            : ValueProvider.of(submissionClientTimeoutInMillis);
    this.settingsClientTimeoutInMillis =
        settingsClientTimeoutInMillis == null
            ? ValueProvider.of(DEFAULT_SETTINGS_CLIENT_TIMEOUT_IN_MILLIS)
            : ValueProvider.of(settingsClientTimeoutInMillis);
    this.propertyChangeSupport = new PropertyChangeSupport(this);
    this.lastReferenceIdManager =
        lastReferenceIdManager == null
            ? DefaultLastReferenceIdManager.builder().build()
            : lastReferenceIdManager;
    this.storageProvider =
        storageProvider == null
            ? InMemoryStorageProvider.builder().maxQueueItems(maxQueueItems).build()
            : storageProvider;
    this.settingsManager =
        SettingsManager.builder()
            .settingsClient(
                settingsClient == null
                    ? DefaultSettingsClient.builder()
                        .configServerUrl(this.configServerUrl)
                        .settingsClientTimeoutInMillis(this.settingsClientTimeoutInMillis)
                        .apiKey(this.apiKey)
                        .build()
                    : settingsClient)
            .storageProvider(this.storageProvider)
            .build();
    this.userAgentBotPatterns = new HashSet<>();
    this.submissionClient =
        submissionClient == null
            ? DefaultSubmissionClient.builder()
                .settingsManager(this.settingsManager)
                .submissionClientTimeoutInMillis(this.submissionClientTimeoutInMillis)
                .serverUrl(this.serverUrl)
                .apiKey(this.apiKey)
                .heartbeatServerUrl(this.heartbeatServerUrl)
                .build()
            : submissionClient;
    this.queue =
        queue == null
            ? DefaultEventQueue.builder()
                .submissionBatchSize(this.submissionBatchSize)
                .processingIntervalInSecs(processingIntervalInSecs)
                .storageProvider(this.storageProvider)
                .submissionClient(this.submissionClient)
                .build()
            : queue;
    this.pluginManager = PluginManager.builder().build();
    this.defaultData = new HashMap<>();
    this.defaultTags = new HashSet<>();
    this.onChangedHandlers = new ArrayList<>();
    this.dataExclusions = new HashSet<>();
    this.privateInformationInclusions = PrivateInformationInclusions.builder().build();
    checkApiKeyIsValid();
    addPropertyChangeListeners();
    addLogCapturer(logCatpurer);
  }

  private void addPropertyChangeListeners() {
    this.privateInformationInclusions.addPropertyChangeListener(ignored -> changed());
    this.propertyChangeSupport.addPropertyChangeListener(ignored -> changed());
    this.settingsManager.addPropertyChangeListener(ignored -> changed());
  }

  private void checkApiKeyIsValid() {
    if (this.apiKey.get() != null && this.apiKey.get().length() >= API_KEY_MIN_LENGTH) {
      return;
    }

    throw new InvalidApiKeyException(String.format("Apikey is not valid: [%s]", this.apiKey.get()));
  }

  private void addLogCapturer(LogCapturerIF logCatpurer) {
    logCatpurer = logCatpurer == null ? NullLogCapturer.builder().build() : logCatpurer;

    ch.qos.logback.classic.Logger logBackRootLogger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    LogCapturerAppender appender = LogCapturerAppender.builder().logCapturer(logCatpurer).build();
    appender.setContext((Context) LoggerFactory.getILoggerFactory());
    appender.start();
    logBackRootLogger.addAppender(appender);
  }

  public void setApiKey(String apiKey) {
    String prevValue = this.apiKey.get();
    this.apiKey.update(apiKey);
    propertyChangeSupport.firePropertyChange(Property.API_KEY, prevValue, apiKey);
  }

  public void setServerUrl(String serverUrl) {
    String prevValue = this.serverUrl.get();
    this.serverUrl.update(serverUrl);
    propertyChangeSupport.firePropertyChange(
        Property.SERVER_URL, prevValue, serverUrl);
  }

  public void setConfigServerUrl(String configServerUrl) {
    String prevValue = this.configServerUrl.get();
    this.configServerUrl.update(configServerUrl);
    propertyChangeSupport.firePropertyChange(
        Property.CONFIG_SERVER_URL, prevValue, configServerUrl);
  }

  public void setHeartbeatServerUrl(String heartbeatServerUrl) {
    String prevValue = this.heartbeatServerUrl.get();
    this.heartbeatServerUrl.update(heartbeatServerUrl);
    propertyChangeSupport.firePropertyChange(
        Property.HEART_BEAT_SERVER_URL, prevValue, heartbeatServerUrl);
  }

  public void setUpdateSettingsWhenIdleInterval(Long updateSettingsWhenIdleInterval) {
    Long prevValue = this.updateSettingsWhenIdleInterval.get();
    this.updateSettingsWhenIdleInterval.update(updateSettingsWhenIdleInterval);
    propertyChangeSupport.firePropertyChange(
        Property.UPDATE_SETTINGS_WHEN_IDLE_INTERVAL,
        prevValue,
        updateSettingsWhenIdleInterval);
  }

  public void setSubmissionBatchSize(Integer submissionBatchSize) {
    Integer prevValue = this.submissionBatchSize.get();
    this.submissionBatchSize.update(submissionBatchSize);
    propertyChangeSupport.firePropertyChange(
        Property.SUBMISSION_BATCH_SIZE, prevValue, submissionBatchSize);
  }

  public void setSubmissionClientTimeoutInMillis(Integer submissionClientTimeoutInMillis) {
    Integer prevValue = this.submissionClientTimeoutInMillis.get();
    this.submissionClientTimeoutInMillis.update(submissionClientTimeoutInMillis);
    propertyChangeSupport.firePropertyChange(
        Property.SUBMISSION_CLIENT_TIMEOUT_IN_MILLIS,
        prevValue,
        submissionClientTimeoutInMillis);
  }

  public void setSettingsClientTimeoutInMillis(Integer settingsClientTimeoutInMillis) {
    Integer prevValue = this.settingsClientTimeoutInMillis.get();
    this.settingsClientTimeoutInMillis.update(settingsClientTimeoutInMillis);
    propertyChangeSupport.firePropertyChange(
        Property.SETTINGS_CLIENT_TIMEOUT_IN_MILLIS,
        prevValue,
        settingsClientTimeoutInMillis);
  }

  public void addDefaultTags(String... tags) {
    defaultTags.addAll(Arrays.asList(tags));
  }

  public void addDataExclusions(String... exclusions) {
    dataExclusions.addAll(Arrays.asList(exclusions));
  }

  public void addUserAgentBotPatterns(String... userAgentBotPatterns) {
    this.userAgentBotPatterns.addAll(Arrays.asList(userAgentBotPatterns));
  }

  public Set<String> getDataExclusions() {
    Set<String> combinedExclusions = settingsManager.getSavedServerSettings().getDataExclusions();
    combinedExclusions.addAll(dataExclusions);
    return combinedExclusions;
  }

  public Set<String> getUserAgentBotPatterns() {
    Set<String> combinedPatterns =
        settingsManager.getSavedServerSettings().getUserAgentBotPatterns();
    combinedPatterns.addAll(userAgentBotPatterns);
    return combinedPatterns;
  }

  public void submitSessionHeartbeat(String sessionOrUserId) {
    log.info(String.format("Submitting session heartbeat: %s", sessionOrUserId));
    submissionClient.sendHeartBeat(sessionOrUserId, false);
  }

  public void addPlugin(EventPluginIF eventPlugin) {
    pluginManager.addPlugin(eventPlugin);
  }

  public void addPlugin(BiConsumer<EventPluginContext, ConfigurationManager> pluginAction) {
    pluginManager.addPlugin(pluginAction);
  }

  public void addPlugin(
      String name,
      int priority,
      BiConsumer<EventPluginContext, ConfigurationManager> pluginAction) {
    pluginManager.addPlugin(name, priority, pluginAction);
  }

  public void removePlugin(String name) {
    pluginManager.removePlugin(name);
  }

  public List<EventPluginIF> getPlugins() {
    return pluginManager.getPlugins();
  }

  public void setVersion(String version) {
    this.defaultData.put(EventPropertyKey.VERSION.value(), version);
  }

  public void removeUserIdentity() {
    this.defaultData.remove(EventPropertyKey.USER.value());
  }

  public void setUserIdentity(String name, String identity) {
    setUserIdentity(UserInfo.builder().name(name).identity(identity).build());
  }

  public void setUserIdentity(UserInfo userInfo) {
    this.defaultData.put(EventPropertyKey.USER.value(), userInfo);
  }

  public void useSessions() {
    useSessions(DEFAULT_HEART_BEAT_INTERVAL_IN_SECS);
  }

  public void useSessions(int heartbeatIntervalInSecs) {
    addPlugin(HeartbeatPlugin.builder().heartbeatIntervalInSecs(heartbeatIntervalInSecs).build());
  }

  public void onChanged(Consumer<ConfigurationManager> onChangedHandler) {
    onChangedHandlers.add(onChangedHandler);
  }

  private void changed() {
    for (Consumer<ConfigurationManager> onChangedHandler : onChangedHandlers) {
      try {
        onChangedHandler.accept(this);
      } catch (Exception e) {
        log.error(String.format("Error calling on changed handler: %s", e.getMessage()), e);
      }
    }
  }
}
