package com.prashantchaubey.exceptionlessclient;

import com.prashantchaubey.exceptionlessclient.configuration.Configuration;
import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.PluginContext;
import com.prashantchaubey.exceptionlessclient.models.UserDescription;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.models.enums.EventType;
import com.prashantchaubey.exceptionlessclient.models.submission.SubmissionResponse;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginRunner;
import lombok.Builder;

import java.time.LocalDate;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

public class ExceptionlessClient {
  private static final int UPDATE_SETTINGS_TIMER_INITIAL_DELAY = 5000;

  private ConfigurationManager configurationManager;
  private EventPluginRunner eventPluginRunner;
  private Timer updateSettingsTimer;

  @Builder
  public ExceptionlessClient(ConfigurationManager configurationManager) {
    this.configurationManager = configurationManager;
    this.eventPluginRunner =
        EventPluginRunner.builder().configurationManager(this.configurationManager).build();
    this.updateSettingsTimer = new Timer();
    init();
  }

  private void init() {
    updateSettingsTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            configurationManager.getSettingsManager().updateSettingsThreadSafe();
          }
        },
        UPDATE_SETTINGS_TIMER_INITIAL_DELAY,
        configurationManager.getConfiguration().getUpdateSettingsWhenIdleInterval());
  }

  public static ExceptionlessClient from(String apiKey, String serverUrl) {
    return ExceptionlessClient.builder()
        .configurationManager(
            ConfigurationManager.builder()
                .configuration(Configuration.builder().apiKey(apiKey).serverUrl(serverUrl).build())
                .build())
        .build();
  }

  public void submitException(Exception exception, Consumer<EventPluginContext> handler) {
    Event event = createException().build();
    PluginContext pluginContext = PluginContext.builder().exception(exception).build();
    submitEvent(EventPluginContext.builder().event(event).context(pluginContext).build(), handler);
  }

  private Event.EventBuilder createException() {
    return createEvent().type(EventType.ERROR.value());
  }

  public void submitUnhandledException(
      Exception exception, String submissionMethod, Consumer<EventPluginContext> handler) {
    Event event = createException().build();
    PluginContext pluginContext =
        PluginContext.builder()
            .exception(exception)
            .unhandledError(true)
            .submissionMethod(submissionMethod)
            .build();
    submitEvent(EventPluginContext.builder().event(event).context(pluginContext).build(), handler);
  }

  public void submitFeatureUsage(String feature, Consumer<EventPluginContext> handler) {
    Event event = createFeatureUsage(feature).build();
    submitEvent(EventPluginContext.from(event), handler);
  }

  private Event.EventBuilder createFeatureUsage(String feature) {
    return createEvent().type(EventType.USAGE.value()).source(feature);
  }

  public void submitLog(String message, Consumer<EventPluginContext> handler) {
    submitLog(message, null, null, handler);
  }

  public void submitLog(String message, String source, Consumer<EventPluginContext> handler) {
    submitLog(message, source, null, handler);
  }

  public void submitLog(
      String message, String source, String level, Consumer<EventPluginContext> handler) {
    Event event = createLog(message, source, level).build();
    submitEvent(EventPluginContext.from(event), handler);
  }

  private Event.EventBuilder createLog(String message, String source, String level) {
    if (source == null) {
      // Calling method
      source = Thread.currentThread().getStackTrace()[2].getMethodName();
    }

    Event.EventBuilder builder =
        createEvent().type(EventType.LOG.value()).source(source).message(message);
    if (level == null) {
      return builder;
    }

    return builder.property(EventPropertyKey.LOG_LEVEL.value(), level);
  }

  public void submitNotFound(String resource, Consumer<EventPluginContext> handler) {
    Event event = createNotFound(resource).build();
    submitEvent(EventPluginContext.from(event), handler);
  }

  private Event.EventBuilder createNotFound(String resource) {
    return createEvent().type(EventType.NOT_FOUND.value()).source(resource);
  }

  public void submitSessionStart(Consumer<EventPluginContext> handler) {
    Event event = createSessionStart().build();
    submitEvent(EventPluginContext.from(event), handler);
  }

  private Event.EventBuilder createSessionStart() {
    return createEvent().type(EventType.SESSION.value());
  }

  private Event.EventBuilder createEvent() {
    return Event.builder()
        .dataExclusions(configurationManager.getDataExclusions())
        .date(LocalDate.now());
  }

  private void submitEvent(
      EventPluginContext eventPluginContext, Consumer<EventPluginContext> handler) {
    eventPluginRunner.run(
        eventPluginContext,
        evc -> {
          if (evc.getContext().isEventCancelled()) {
            return;
          }
          configurationManager.getQueue().enqueue(evc.getEvent());
          if (evc.getEvent().getReferenceId() != null) {
            configurationManager
                .getLastReferenceIdManager()
                .setLast(evc.getEvent().getReferenceId());
          }
          handler.accept(evc);
        });
  }

  public void submitSessionEnd(String sessionOrUserId) {
    configurationManager
        .getLog()
        .info(String.format("Submitting session end: %s", sessionOrUserId));
    configurationManager.getSubmissionClient().sendHeartBeat(sessionOrUserId, true);
  }

  public void updateEmailAndDescription(
      String referenceId, String email, String description, Consumer<SubmissionResponse> handler) {
    SubmissionResponse response =
        configurationManager
            .getSubmissionClient()
            .postUserDescription(
                referenceId,
                UserDescription.builder().description(description).emailAddress(email).build());
    if (!response.isSuccess()) {
      configurationManager
          .getLog()
          .error(
              String.format(
                  "Failed to submit user email and description for event: %s", referenceId));
    }
    handler.accept(response);
  }

  public String getLastReferenceId() {
    return configurationManager.getLastReferenceIdManager().getLast();
  }
}
