package com.exceptionless.exceptionlessclient;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.enums.EventType;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.PluginContext;
import com.exceptionless.exceptionlessclient.models.UserDescription;
import com.exceptionless.exceptionlessclient.plugins.EventPluginRunner;
import com.exceptionless.exceptionlessclient.submission.SubmissionResponse;
import com.exceptionless.exceptionlessclient.utils.VisibleForTesting;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ExceptionlessClient {
  private static final String UPDATE_SETTINGS_TIMER_NAME = "update-settings-timer";
  private static final int UPDATE_SETTINGS_TIMER_INITIAL_DELAY = 5000;
  private static final Integer DEFAULT_NTHREADS = 10;

  @Getter private final Configuration configuration;
  private final EventPluginRunner eventPluginRunner;
  private final Timer updateSettingsTimer;
  private final ExecutorService executorService;

  @Builder
  public ExceptionlessClient(Configuration configuration, Integer nThreads) {
    this(
        configuration,
        UPDATE_SETTINGS_TIMER_INITIAL_DELAY,
        nThreads == null ? DEFAULT_NTHREADS : nThreads);
  }

  @VisibleForTesting
  ExceptionlessClient(
      Configuration configuration, long updateSettingsTimerInitialDelay, Integer nThreads) {
    this.configuration = configuration;
    this.eventPluginRunner = EventPluginRunner.builder().configuration(this.configuration).build();
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
              configuration.getSettingsManager().updateSettings();
            } catch (Exception e) {
              log.error("Error in updating settings", e);
            }
          }
        },
        delay,
        configuration.getUpdateSettingsWhenIdleInterval().get());

    configuration.onChanged(ignored -> configuration.getSettingsManager().updateSettings());
    configuration
        .getQueue()
        .onEventsPosted(
            (ignored1, ignored2) -> configuration.getSettingsManager().updateSettings());
  }

  public static ExceptionlessClient from(String apiKey, String serverUrl) {
    return ExceptionlessClient.builder()
        .configuration(Configuration.builder().apiKey(apiKey).serverUrl(serverUrl).build())
        .build();
  }

  public void submitException(Exception exception) {
    submitException(null, exception);
  }

  public void submitException(String message, Exception exception) {
    Event event;
    if (message == null) {
      event = createError().build();
    } else {
      event = createError().message(message).build();
    }
    PluginContext pluginContext = PluginContext.builder().exception(exception).build();
    submitEventWithContext(
        EventPluginContext.builder().event(event).context(pluginContext).build());
  }

  public Event.EventBuilder createError() {
    return createEvent().type(EventType.ERROR.value());
  }

  public void submitUnhandledException(Exception exception, String submissionMethod) {
    Event event = createError().build();
    PluginContext pluginContext =
        PluginContext.builder()
            .exception(exception)
            .unhandledError(true)
            .submissionMethod(submissionMethod)
            .build();
    submitEventWithContext(
        EventPluginContext.builder().event(event).context(pluginContext).build());
  }

  public void submitFeatureUsage(String feature) {
    submitEvent(createFeatureUsage(feature).build());
  }

  public Event.EventBuilder createFeatureUsage(String feature) {
    return createEvent().type(EventType.USAGE.value()).source(feature);
  }

  public void submitLog(String message) {
    submitLog(message, null, null);
  }

  public void submitLog(String message, String source) {
    submitLog(message, source, null);
  }

  public void submitLog(String message, String source, String level) {
    submitEvent(createLog(message, source, level).build());
  }

  public Event.EventBuilder createLog(String message) {
    return createLog(message, null, null);
  }

  public Event.EventBuilder createLog(String message, String source) {
    return createLog(message, source, null);
  }

  public Event.EventBuilder createLog(String message, String source, String level) {
    if (source == null) {
      source = getCallingMethod();
    }

    Event.EventBuilder builder =
        createEvent().type(EventType.LOG.value()).source(source).message(message);
    if (level == null) {
      return builder;
    }

    return builder.property(EventPropertyKey.LOG_LEVEL.value(), level);
  }

  private String getCallingMethod() {
    StackTraceElement[] traceElements = Thread.currentThread().getStackTrace();
    String source = traceElements[3].getMethodName();
    boolean cameFromOverridenMethod = source.equals("createLog");

    return cameFromOverridenMethod ? traceElements[4].getMethodName() : source;
  }

  public void submitNotFound(String resource) {
    submitEvent(createNotFound(resource).build());
  }

  public Event.EventBuilder createNotFound(String resource) {
    return createEvent().type(EventType.NOT_FOUND.value()).source(resource);
  }

  public void submitSessionStart() {
    submitEvent(createSessionStart().build());
  }

  public Event.EventBuilder createSessionStart() {
    return createEvent().type(EventType.SESSION.value());
  }

  public Event.EventBuilder createEvent() {
    return Event.builder().dataExclusions(configuration.getDataExclusions()).date(OffsetDateTime.now());
  }

  public void submitEvent(Event event) {
    eventPluginRunner.run(EventPluginContext.from(event));
  }

  public void submitEventWithContext(EventPluginContext eventPluginContext) {
    eventPluginRunner.run(eventPluginContext);
  }

  public void submitSessionEnd(String sessionOrUserId) {
    log.info(String.format("Submitting session end: %s", sessionOrUserId));
    configuration.getSubmissionClient().sendHeartBeat(sessionOrUserId, true);
  }

  public SubmissionResponse updateEmailAndDescription(
      String referenceId, String email, String description) {
    SubmissionResponse response =
        configuration
            .getSubmissionClient()
            .postUserDescription(
                referenceId,
                UserDescription.builder().description(description).emailAddress(email).build());
    if (response.hasException()) {
      log.error(
          String.format("Failed to submit user email and description for event: %s", referenceId),
          response.getException());
    }
    if (!response.isSuccess()) {
      log.error(
          String.format(
              "Failed to submit user email and description for event: %s, code: %s",
              referenceId, response.getCode()));
    }

    return response;
  }

  public String getLastReferenceId() {
    return configuration.getLastReferenceIdManager().getLast();
  }
}
