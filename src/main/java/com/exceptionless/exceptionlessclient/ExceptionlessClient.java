package com.exceptionless.exceptionlessclient;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.PluginContext;
import com.exceptionless.exceptionlessclient.models.UserDescription;
import com.exceptionless.exceptionlessclient.models.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.models.enums.EventType;
import com.exceptionless.exceptionlessclient.models.submission.SubmissionResponse;
import com.exceptionless.exceptionlessclient.plugins.EventPluginRunner;
import com.exceptionless.exceptionlessclient.utils.VisibleForTesting;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ExceptionlessClient {
  private static final String UPDATE_SETTINGS_TIMER_NAME = "update-settings-timer";
  private static final int UPDATE_SETTINGS_TIMER_INITIAL_DELAY = 5000;
  private static final Integer DEFAULT_NTHREADS = 10;

  @Getter private final ConfigurationManager configurationManager;
  private final EventPluginRunner eventPluginRunner;
  private final Timer updateSettingsTimer;
  private final ExecutorService executorService;

  @Builder
  public ExceptionlessClient(ConfigurationManager configurationManager, Integer nThreads) {
    this(
        configurationManager,
        UPDATE_SETTINGS_TIMER_INITIAL_DELAY,
        nThreads == null ? DEFAULT_NTHREADS : nThreads);
  }

  @VisibleForTesting
  ExceptionlessClient(
      ConfigurationManager configurationManager,
      long updateSettingsTimerInitialDelay,
      Integer nThreads) {
    this.configurationManager = configurationManager;
    this.eventPluginRunner =
        EventPluginRunner.builder().configurationManager(this.configurationManager).build();
    this.updateSettingsTimer = new Timer(UPDATE_SETTINGS_TIMER_NAME);
    this.executorService = Executors.newFixedThreadPool(nThreads);
    init(updateSettingsTimerInitialDelay);
  }

  private void init(long delay) {
    updateSettingsTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            try {
              configurationManager.getSettingsManager().updateSettings();
            } catch (Exception e) {
              log.error("Error in updating settings", e);
            }
          }
        },
        delay,
        configurationManager.getConfiguration().getUpdateSettingsWhenIdleInterval());

    configurationManager.onChanged(
        ignored -> configurationManager.getSettingsManager().updateSettings());
    configurationManager
        .getQueue()
        .onEventsPosted(
            (ignored1, ignored2) -> configurationManager.getSettingsManager().updateSettings());
  }

  public static ExceptionlessClient from(String apiKey, String serverUrl) {
    return ExceptionlessClient.builder()
        .configurationManager(
            ConfigurationManager.builder()
                .configuration(Configuration.builder().apiKey(apiKey).serverUrl(serverUrl).build())
                .build())
        .build();
  }

  public CompletableFuture<Void> submitExceptionAsync(Exception exception) {
    return CompletableFuture.runAsync(() -> submitException(exception), executorService);
  }

  public void submitException(Exception exception) {
    Event event = createException().build();
    PluginContext pluginContext = PluginContext.builder().exception(exception).build();
    submitEvent(EventPluginContext.builder().event(event).context(pluginContext).build());
  }

  public Event.EventBuilder createException() {
    return createEvent().type(EventType.ERROR.value());
  }

  public CompletableFuture<Void> submitUnhandledExceptionAsync(
      Exception exception, String submissionMethod) {
    return CompletableFuture.runAsync(
        () -> submitUnhandledException(exception, submissionMethod), executorService);
  }

  public void submitUnhandledException(Exception exception, String submissionMethod) {
    Event event = createException().build();
    PluginContext pluginContext =
        PluginContext.builder()
            .exception(exception)
            .unhandledError(true)
            .submissionMethod(submissionMethod)
            .build();
    submitEvent(EventPluginContext.builder().event(event).context(pluginContext).build());
  }

  public CompletableFuture<Void> submitFeatureUsageAsync(String feature) {
    return CompletableFuture.runAsync(() -> submitFeatureUsage(feature), executorService);
  }

  public void submitFeatureUsage(String feature) {
    Event event = createFeatureUsage(feature).build();
    submitEvent(EventPluginContext.from(event));
  }

  public Event.EventBuilder createFeatureUsage(String feature) {
    return createEvent().type(EventType.USAGE.value()).source(feature);
  }

  public CompletableFuture<Void> submitLogAsync(String message) {
    return CompletableFuture.runAsync(() -> submitLog(message), executorService);
  }

  public void submitLog(String message) {
    submitLog(message, null, null);
  }

  public CompletableFuture<Void> submitLogAsync(String message, String source) {
    return CompletableFuture.runAsync(() -> submitLog(message, source), executorService);
  }

  public void submitLog(String message, String source) {
    submitLog(message, source, null);
  }

  public CompletableFuture<Void> submitLogAsync(String message, String source, String level) {
    return CompletableFuture.runAsync(() -> submitLog(message, source, level), executorService);
  }

  public void submitLog(String message, String source, String level) {
    Event event = createLog(message, source, level).build();
    submitEvent(EventPluginContext.from(event));
  }

  public Event.EventBuilder createLog(String message) {
    return createLog(message, null, null);
  }

  public Event.EventBuilder createLog(String message, String source) {
    return createLog(message, source, null);
  }

  public Event.EventBuilder createLog(String message, String source, String level) {
    if (source == null) {
      // Calling method
      StackTraceElement[] traceElements = Thread.currentThread().getStackTrace();
      source = traceElements[2].getMethodName();
      // Came from the overrided method
      if (source.equals("createLog")) {
        source = traceElements[3].getMethodName();
      }
    }

    Event.EventBuilder builder =
        createEvent().type(EventType.LOG.value()).source(source).message(message);
    if (level == null) {
      return builder;
    }

    return builder.property(EventPropertyKey.LOG_LEVEL.value(), level);
  }

  public CompletableFuture<Void> submitNotFoundAsync(String resource) {
    return CompletableFuture.runAsync(() -> submitNotFound(resource), executorService);
  }

  public void submitNotFound(String resource) {
    Event event = createNotFound(resource).build();
    submitEvent(EventPluginContext.from(event));
  }

  public Event.EventBuilder createNotFound(String resource) {
    return createEvent().type(EventType.NOT_FOUND.value()).source(resource);
  }

  public CompletableFuture<Void> submitSessionStartAsync() {
    return CompletableFuture.runAsync(this::submitSessionStart, executorService);
  }

  public void submitSessionStart() {
    Event event = createSessionStart().build();
    submitEvent(EventPluginContext.from(event));
  }

  public Event.EventBuilder createSessionStart() {
    return createEvent().type(EventType.SESSION.value());
  }

  public Event.EventBuilder createEvent() {
    return Event.builder()
        .dataExclusions(configurationManager.getDataExclusions())
        .date(LocalDate.now());
  }

  public CompletableFuture<Void> submitEventAsync(EventPluginContext eventPluginContext) {
    return CompletableFuture.runAsync(() -> submitEvent(eventPluginContext), executorService);
  }

  public void submitEvent(EventPluginContext eventPluginContext) {
    eventPluginRunner.run(eventPluginContext);
  }

  public CompletableFuture<Void> submitSessionEndAsync(String sessionOrUserId) {
    return CompletableFuture.runAsync(() -> submitSessionEnd(sessionOrUserId), executorService);
  }

  public void submitSessionEnd(String sessionOrUserId) {
    log.info(String.format("Submitting session end: %s", sessionOrUserId));
    configurationManager.getSubmissionClient().sendHeartBeat(sessionOrUserId, true);
  }

  public CompletableFuture<SubmissionResponse> updateEmailAndDescriptionAsync(
      String referenceId, String email, String description) {
    return CompletableFuture.supplyAsync(
        () -> updateEmailAndDescription(referenceId, email, description), executorService);
  }

  public SubmissionResponse updateEmailAndDescription(
      String referenceId, String email, String description) {
    SubmissionResponse response =
        configurationManager
            .getSubmissionClient()
            .postUserDescription(
                referenceId,
                UserDescription.builder().description(description).emailAddress(email).build());
    if (!response.isSuccess()) {
      log.error(
          String.format("Failed to submit user email and description for event: %s", referenceId));
    }

    return response;
  }

  public String getLastReferenceId() {
    return configurationManager.getLastReferenceIdManager().getLast();
  }
}
