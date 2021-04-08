package com.exceptionless.exceptionlessclient.configuration;

import com.exceptionless.exceptionlessclient.exceptions.ClientException;
import com.exceptionless.exceptionlessclient.lastreferenceidmanager.DefaultLastReferenceIdManager;
import com.exceptionless.exceptionlessclient.lastreferenceidmanager.LastReferenceIdManagerIF;
import com.exceptionless.exceptionlessclient.logging.LogIF;
import com.exceptionless.exceptionlessclient.logging.ConsoleLog;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.UserInfo;
import com.exceptionless.exceptionlessclient.models.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.plugins.preconfigured.HeartbeatPlugin;
import com.exceptionless.exceptionlessclient.queue.DefaultEventQueue;
import com.exceptionless.exceptionlessclient.queue.EventQueueIF;
import com.exceptionless.exceptionlessclient.services.*;
import com.exceptionless.exceptionlessclient.settings.DefaultSettingsClient;
import com.exceptionless.exceptionlessclient.settings.SettingsClientIF;
import com.exceptionless.exceptionlessclient.settings.SettingsManager;
import com.exceptionless.exceptionlessclient.storage.InMemoryStorageProvider;
import com.exceptionless.exceptionlessclient.storage.StorageProviderIF;
import com.exceptionless.exceptionlessclient.submission.DefaultSubmissionClient;
import com.exceptionless.exceptionlessclient.submission.SubmissionClientIF;
import lombok.Builder;
import lombok.Getter;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ConfigurationManager {
  @Getter private final EnvironmentInfoCollectorIF environmentInfoCollector;
  @Getter private final ErrorParserIF errorParser;
  @Getter private final LastReferenceIdManagerIF lastReferenceIdManager;
  @Getter private final LogIF log;
  @Getter private final ModuleCollectorIF moduleCollector;
  @Getter private final RequestInfoCollectorIF requestInfoCollector;
  @Getter private final SubmissionClientIF submissionClient;
  @Getter private final EventQueueIF queue;
  @Getter private final Configuration configuration;
  @Getter private final Set<String> defaultTags;
  @Getter private final Map<String, Object> defaultData;
  private final List<Consumer<ConfigurationManager>> onChangedHandlers;
  @Getter private final SettingsManager settingsManager;
  private final Set<String> userAgentBotPatterns;
  @Getter private final PrivateInformationInclusions privateInformationInclusions;
  private final Set<String> dataExclusions;
  private PluginManager pluginManager;
  @Getter private final StorageProviderIF storageProvider;

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
    this.log = log == null ? ConsoleLog.builder().build() : log;
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
    this.storageProvider =
        storageProvider == null
            ? InMemoryStorageProvider.builder().maxQueueItems(maxQueueItems).build()
            : storageProvider;
    this.configuration =
            configuration == null ? Configuration.defaultConfiguration() : configuration;
    this.settingsManager =
        SettingsManager.builder()
            .settingsClient(
                settingsClient == null
                    ? DefaultSettingsClient.builder().configuration(this.configuration).build()
                    : settingsClient)
            .log(this.log)
            .storageProvider(this.storageProvider)
            .build();
    this.userAgentBotPatterns = new HashSet<>();
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
                .storageProvider(this.storageProvider)
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
    addPropertyChangeListeners();
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
