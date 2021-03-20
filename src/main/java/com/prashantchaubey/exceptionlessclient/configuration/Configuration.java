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
import com.prashantchaubey.exceptionlessclient.storage.StorageProviderIF;
import com.prashantchaubey.exceptionlessclient.submission.SubmissionClientIF;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Configuration {
  private EnvironmentInfoCollectorIF environmentInfoCollector;
  private ErrorParserIF errorParser;

  @Builder.Default
  private LastReferenceIdManagerIF lastReferenceIdManager = new DefaultLastReferenceIdManager();

  @Builder.Default private LogIF log = new NullLog();
  private ModuleCollectorIF moduleCollector;
  private RequestInfoCollectorIF requestInfoCollector;
  private SubmissionClientIF submissionClient;
  private StorageProviderIF storageProvider;
  private EventQueueIF queue;
  private ConfigurationSettings settings;
}
