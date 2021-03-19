package com.prashantchaubey.exceptionlessclient.configuration;

import com.prashantchaubey.exceptionlessclient.lastreferenceidmanager.LastReferenceIdManagerIF;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.queue.EventQueueIF;
import com.prashantchaubey.exceptionlessclient.services.ErrorParserIF;
import com.prashantchaubey.exceptionlessclient.services.ModuleCollectorIF;
import com.prashantchaubey.exceptionlessclient.services.RequestInfoCollectorIF;
import com.prashantchaubey.exceptionlessclient.storage.StorageProviderIF;
import com.prashantchaubey.exceptionlessclient.submission.SubmissionAdapterIF;
import com.prashantchaubey.exceptionlessclient.submission.SubmissionClientIF;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class Configuration {
  private String apiKey;
  private String serverUrl;
  private String configServerUrl;
  private String heartbeatServerUrl;
  private long updateSettingsWhenIdleInterval;
  private boolean includePrivateInformation;
  private ErrorParserIF errorParser;
  private LastReferenceIdManagerIF lastReferenceIdManager;
  private LogIF log;
  private ModuleCollectorIF moduleCollector;
  private RequestInfoCollectorIF requestInfoCollector;
  private int submissionBatchSize;
  private SubmissionClientIF submissionClient;
  private SubmissionAdapterIF submissionAdapter;
  private StorageProviderIF storageProvider;
  private EventQueueIF queue;
}
