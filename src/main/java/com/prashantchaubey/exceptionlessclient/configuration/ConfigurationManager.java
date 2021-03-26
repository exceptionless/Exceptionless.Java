package com.prashantchaubey.exceptionlessclient.configuration;

import com.prashantchaubey.exceptionlessclient.exceptions.ClientException;
import com.prashantchaubey.exceptionlessclient.lastreferenceidmanager.DefaultLastReferenceIdManager;
import com.prashantchaubey.exceptionlessclient.lastreferenceidmanager.LastReferenceIdManagerIF;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.logging.NullLog;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import com.prashantchaubey.exceptionlessclient.queue.EventQueueIF;
import com.prashantchaubey.exceptionlessclient.services.EnvironmentInfoCollectorIF;
import com.prashantchaubey.exceptionlessclient.services.ErrorParserIF;
import com.prashantchaubey.exceptionlessclient.services.ModuleCollectorIF;
import com.prashantchaubey.exceptionlessclient.settings.DefaultSettingsClient;
import com.prashantchaubey.exceptionlessclient.settings.SettingsClientIF;
import com.prashantchaubey.exceptionlessclient.settings.SettingsManager;
import com.prashantchaubey.exceptionlessclient.storage.StorageProviderIF;
import com.prashantchaubey.exceptionlessclient.submission.DefaultSubmissionClient;
import com.prashantchaubey.exceptionlessclient.submission.SubmissionClientIF;
import lombok.Builder;
import lombok.Getter;

import java.util.*;
import java.util.function.BiConsumer;

@Builder(builderClassName = "ConfigurationInternalBuilder")
@Getter
public class ConfigurationManager {
  private EnvironmentInfoCollectorIF environmentInfoCollector;
  private ErrorParserIF errorParser;

  @Builder.Default
  private LastReferenceIdManagerIF lastReferenceIdManager = new DefaultLastReferenceIdManager();

  @Builder.Default private LogIF log = new NullLog();
  private ModuleCollectorIF moduleCollector;
  // By default `DefaultSubmissionClient` will be used; See `init()`
  private SubmissionClientIF submissionClient;
  // By default `DefaultSettingsClient` will be used; See `init()`
  private SettingsClientIF settingsClient;
  private StorageProviderIF storageProvider;
  private EventQueueIF queue;
  @Builder.Default private Configuration configuration = Configuration.defaultConfiguration();
  @Builder.Default private Set<String> defaultTags = new HashSet<>();
  @Builder.Default private Map<String, Object> defaultData = new HashMap<>();
  @Builder.Default private List<EventPluginIF> plugins = new ArrayList<>();

  // lombok ignored fields
  private SettingsManager $settingsManager;
  private Map<String, String> $settings = new HashMap<>();
  private Set<String> $dataExclusions = new HashSet<>();

  public static ConfigurationManager from(String apiKey, String serverUrl) {
    return ConfigurationManager.builder()
        .configuration(Configuration.builder().apiKey(apiKey).serverUrl(serverUrl).build())
        .build();
  }

  public SettingsManager getSettingsManager() {
    return $settingsManager;
  }

  public void addDataExclusions(String... exclusions) {
    $dataExclusions.addAll(Arrays.asList(exclusions));
  }

  public Set<String> getDataExclusions() {
    String serverExclusions = $settings.getOrDefault("@@DataExclusions", "");
    Set<String> combinedExclusions = new HashSet<>(Arrays.asList(serverExclusions.split(",")));
    combinedExclusions.addAll($dataExclusions);
    return combinedExclusions;
  }

  public void submitSessionHeartbeat(String sessionOrUserId) {
    log.info(String.format("Submitting session heartbeat: %s", sessionOrUserId));
    submissionClient.sendHeartBeat(sessionOrUserId, false);
  }

  public void addPlugin(EventPluginIF eventPlugin) {
    if (plugins.stream().anyMatch(plugin -> plugin.getName().equals(eventPlugin.getName()))) {
      log.info(
          String.format(
              "Can't add plugin, name: %s, priority: %s as a plugin with this name already configured",
              eventPlugin.getName(), eventPlugin.getPriority()));
    }
    plugins.add(eventPlugin);
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

  public static ConfigurationBuilder builder() {
    return new ConfigurationBuilder();
  }

  public static class ConfigurationBuilder extends ConfigurationInternalBuilder {
    @Override
    public ConfigurationManager build() {
      ConfigurationManager configurationManager = super.build();
      configurationManager.init();

      return configurationManager;
    }
  }

  // todo try to inject the dependencies using spring
  // Order of field initialization is very important
  private void init() {
    if (!configuration.isApiKeyValid()) {
      throw new ClientException("Api key is not valid");
    }

    if (settingsClient == null) {
      settingsClient = DefaultSettingsClient.builder().configuration(configuration).build();
    }

    $settingsManager =
        SettingsManager.builder()
            .configuration(configuration)
            .settingsClient(settingsClient)
            .log(log)
            .build();

    if (submissionClient == null) {
      submissionClient =
          DefaultSubmissionClient.builder()
              .configuration(configuration)
              .settingsManager($settingsManager)
              .build();
    }
  }
}
