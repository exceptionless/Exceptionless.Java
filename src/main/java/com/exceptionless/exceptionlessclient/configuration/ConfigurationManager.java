package com.exceptionless.exceptionlessclient.configuration;

import com.exceptionless.exceptionlessclient.exceptions.ClientException;
import com.exceptionless.exceptionlessclient.lastreferenceidmanager.DefaultLastReferenceIdManager;
import com.exceptionless.exceptionlessclient.lastreferenceidmanager.LastReferenceIdManagerIF;
import com.exceptionless.exceptionlessclient.logging.LogIF;
import com.exceptionless.exceptionlessclient.logging.NullLog;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.UserInfo;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.plugins.preconfigured.HeartbeatPlugin;
import com.exceptionless.exceptionlessclient.queue.DefaultEventQueue;
import com.exceptionless.exceptionlessclient.queue.EventQueueIF;
import com.exceptionless.exceptionlessclient.services.*;
import com.exceptionless.exceptionlessclient.settings.SettingsClientIF;
import com.exceptionless.exceptionlessclient.settings.SettingsManager;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import com.exceptionless.exceptionlessclient.storage.StorageProviderIF;
import com.exceptionless.exceptionlessclient.submission.DefaultSubmissionClient;
import com.exceptionless.exceptionlessclient.submission.SubmissionClientIF;
import com.prashantchaubey.exceptionlessclient.exceptions.ClientException;
import com.prashantchaubey.exceptionlessclient.lastreferenceidmanager.DefaultLastReferenceIdManager;
import com.prashantchaubey.exceptionlessclient.lastreferenceidmanager.LastReferenceIdManagerIF;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.logging.NullLog;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.UserInfo;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import com.prashantchaubey.exceptionlessclient.plugins.preconfigured.HeartbeatPlugin;
import com.prashantchaubey.exceptionlessclient.queue.DefaultEventQueue;
import com.prashantchaubey.exceptionlessclient.queue.EventQueueIF;
import com.prashantchaubey.exceptionlessclient.services.*;
import com.prashantchaubey.exceptionlessclient.settings.DefaultSettingsClient;
import com.prashantchaubey.exceptionlessclient.settings.SettingsClientIF;
import com.prashantchaubey.exceptionlessclient.settings.SettingsManager;
import com.prashantchaubey.exceptionlessclient.storage.InMemoryStorageProvider;
import com.prashantchaubey.exceptionlessclient.storage.StorageProviderIF;
import com.prashantchaubey.exceptionlessclient.submission.DefaultSubmissionClient;
import com.prashantchaubey.exceptionlessclient.submission.SubmissionClientIF;
import lombok.Builder;
import lombok.Getter;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ConfigurationManager {
  @Getter private EnvironmentInfoCollectorIF environmentInfoCollector;
  @Getter private ErrorParserIF errorParser;
  @Getter private LastReferenceIdManagerIF lastReferenceIdManager;
  @Getter private LogIF log;
  @Getter private ModuleCollectorIF moduleCollector;
  @Getter private RequestInfoCollectorIF requestInfoCollector;
  @Getter private SubmissionClientIF submissionClient;
  @Getter private EventQueueIF queue;
  @Getter private Configuration configuration;
  @Getter private Set<String> defaultTags;
  @Getter private Map<String, Object> defaultData;
  private List<Consumer<ConfigurationManager>> onChangedHandlers;
  @Getter private SettingsManager settingsManager;
  @Getter private Set<String> userAgentBotPatterns;
  @Getter private PrivateInformationInclusions privateInformationInclusions;
  private Set<String> dataExclusions;
  private PluginManager pluginManager;

  @Builder
  public ConfigurationManager(
      EnvironmentInfoCollectorIF environmentInfoCollector,
      ErrorParserIF errorParser,
      LastReferenceIdManagerIF lastReferenceIdManager,
      LogIF log,
      ModuleCollectorIF moduleCollector,
      RequestInfoCollectorIF requestInfoCollector,
      SubmissionClientIF submissionClient,
      SettingsClientIF settingsClient,
      StorageProviderIF storageProvider,
      EventQueueIF queue,
      Configuration configuration,
      Integer maxQueueItems,
      Integer processingIntervalInSecs) {
    this.log = log == null ? NullLog.builder().build() : log;
    this.environmentInfoCollector =
        environmentInfoCollector == null
            ? DefaultEnvironmentInfoCollector.builder().log(this.log).build()
            : environmentInfoCollector;
    this.errorParser = errorParser == null ? DefaultErrorParser.builder().build() : errorParser;
    this.lastReferenceIdManager =
        lastReferenceIdManager == null
            ? DefaultLastReferenceIdManager.builder().build()
            : lastReferenceIdManager;
    this.moduleCollector =
        moduleCollector == null ? DefaultModuleCollector.builder().build() : moduleCollector;
    this.requestInfoCollector =
        requestInfoCollector == null
            ? DefaultRequestInfoCollector.builder().log(this.log).build()
            : requestInfoCollector;
    this.settingsManager =
        SettingsManager.builder()
            .settingsClient(
                settingsClient == null
                    ? DefaultSettingsClient.builder().configuration(this.configuration).build()
                    : settingsClient)
            .log(log)
            .build();
    this.userAgentBotPatterns = new HashSet<>();
    this.configuration =
        configuration == null ? Configuration.defaultConfiguration() : configuration;
    this.submissionClient =
        submissionClient == null
            ? DefaultSubmissionClient.builder()
                .settingsManager(this.settingsManager)
                .configuration(this.configuration)
                .log(this.log)
                .build()
            : submissionClient;
    this.queue =
        queue == null
            ? DefaultEventQueue.builder()
                .configuration(this.configuration)
                .log(this.log)
                .processingIntervalInSecs(processingIntervalInSecs)
                .storageProvider(
                    storageProvider == null
                        ? InMemoryStorageProvider.builder().maxQueueItems(maxQueueItems).build()
                        : storageProvider)
                .submissionClient(this.submissionClient)
                .build()
            : queue;
    this.pluginManager = PluginManager.builder().log(this.log).build();
    this.defaultData = new HashMap<>();
    this.defaultTags = new HashSet<>();
    this.onChangedHandlers = new ArrayList<>();
    this.dataExclusions = new HashSet<>();
    this.privateInformationInclusions = PrivateInformationInclusions.builder().build();
    this.pluginManager = PluginManager.builder().log(this.log).build();
    checkApiKeyIsValid();
  }

  private void addPropertyChangeListeners() {
    this.privateInformationInclusions.addPropertyChangeListener(ignored -> changed());
    this.configuration.addPropertyChangeListener(ignored -> changed());
    this.settingsManager.addPropertyChangeListener(ignored -> changed());
  }

  private void checkApiKeyIsValid() {
    if (configuration.getApiKey() != null && configuration.getApiKey().length() > 10) {
      return;
    }

    throw new ClientException(
        String.format("Apikey is not valid: [%s]", this.configuration.getApiKey()));
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

  public void submitSessionHeartbeat(String sessionOrUserId) {
    log.info(String.format("Submitting session heartbeat: %s", sessionOrUserId));
    submissionClient.sendHeartBeat(sessionOrUserId, false);
  }

  public void addPlugin(EventPluginIF eventPlugin) {
    pluginManager.addPlugin(eventPlugin);
  }

  public void addPlugin(BiConsumer<EventPluginContext, ConfigurationManager> pluginAction) {
    addPlugin(UUID.randomUUID().toString(), 0, pluginAction);
  }

  public void addPlugin(
      String name,
      int priority,
      BiConsumer<EventPluginContext, ConfigurationManager> pluginAction) {
    addPlugin(
        new EventPluginIF() {
          @Override
          public int getPriority() {
            return priority;
          }

          @Override
          public String getName() {
            return name;
          }

          @Override
          public void run(
              EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
            pluginAction.accept(eventPluginContext, configurationManager);
          }
        });
  }

  public void removePlugin(String name) {
    pluginManager.removePlugin(name);
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

  public void useSession() {
    useSessions(30000);
  }

  public void useSessions(int heartbeatInterval) {
    addPlugin(HeartbeatPlugin.builder().heartbeatInterval(heartbeatInterval).build());
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

  public List<EventPluginIF> getPlugins() {
    return pluginManager.getPlugins();
  }
}
