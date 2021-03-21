package com.prashantchaubey.exceptionlessclient.configuration;

import com.prashantchaubey.exceptionlessclient.lastreferenceidmanager.DefaultLastReferenceIdManager;
import com.prashantchaubey.exceptionlessclient.lastreferenceidmanager.LastReferenceIdManagerIF;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.logging.NullLog;
import com.prashantchaubey.exceptionlessclient.queue.EventQueueIF;
import com.prashantchaubey.exceptionlessclient.services.EnvironmentInfoCollectorIF;
import com.prashantchaubey.exceptionlessclient.services.ErrorParserIF;
import com.prashantchaubey.exceptionlessclient.services.ModuleCollectorIF;
import com.prashantchaubey.exceptionlessclient.services.RequestInfoCollectorIF;
import com.prashantchaubey.exceptionlessclient.settings.DefaultSettingsClient;
import com.prashantchaubey.exceptionlessclient.settings.SettingsClientIF;
import com.prashantchaubey.exceptionlessclient.settings.SettingsManager;
import com.prashantchaubey.exceptionlessclient.storage.StorageProviderIF;
import com.prashantchaubey.exceptionlessclient.submission.DefaultSubmissionClient;
import com.prashantchaubey.exceptionlessclient.submission.SubmissionClientIF;
import lombok.Builder;
import lombok.Getter;

@Builder(builderClassName = "ConfigurationInternalBuilder")
@Getter
public class Configuration {
  private EnvironmentInfoCollectorIF environmentInfoCollector;
  private ErrorParserIF errorParser;

  @Builder.Default
  private LastReferenceIdManagerIF lastReferenceIdManager = new DefaultLastReferenceIdManager();

  @Builder.Default private LogIF log = new NullLog();
  private ModuleCollectorIF moduleCollector;
  private RequestInfoCollectorIF requestInfoCollector;
  // By default `DefaultSubmissionClient` will be used; See `init()`
  private SubmissionClientIF submissionClient;
  // By default `DefaultSettingsClient` will be used; See `init()`
  private SettingsClientIF settingsClient;
  private StorageProviderIF storageProvider;
  private EventQueueIF queue;
  private ConfigurationSettings settings;

  // lombok ignored fields
  private SettingsManager $settingsManager;

  public static Configuration from(String apiKey, String serverUrl) {
    return Configuration.builder()
        .settings(ConfigurationSettings.builder().apiKey(apiKey).serverUrl(serverUrl).build())
        .build();
  }

  public SettingsManager getSettingsManager() {
    return $settingsManager;
  }

  public static class ConfigurationBuilder extends ConfigurationInternalBuilder {
    ConfigurationBuilder() {
      super();
    }

    @Override
    public Configuration build() {
      Configuration config = super.build();
      config.init();

      return config;
    }
  }

  public static ConfigurationBuilder builder() {
    return new ConfigurationBuilder();
  }

  // Order of field initialization is very important
  private void init() {
    if (settingsClient == null) {
      settingsClient = DefaultSettingsClient.builder().settings(settings).build();
    }

    $settingsManager =
        SettingsManager.builder()
            .settings(settings)
            .settingsClient(settingsClient)
            .log(log)
            .build();

    if (submissionClient == null) {
      submissionClient =
          DefaultSubmissionClient.builder()
              .settings(settings)
              .settingsManager($settingsManager)
              .build();
    }
  }
}
