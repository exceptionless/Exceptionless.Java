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
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Timer;
import java.util.TimerTask;

public class ExceptionlessClient {
  private static final String UPDATE_SETTINGS_TIMER_NAME = "update-settings-timer";
  private static final int UPDATE_SETTINGS_TIMER_INITIAL_DELAY = 5000;

  @Getter private final ConfigurationManager configurationManager;
  private final EventPluginRunner eventPluginRunner;
  private final Timer updateSettingsTimer;

  @Builder
  public ExceptionlessClient(ConfigurationManager configurationManager) {
    this.configurationManager = configurationManager;
    this.eventPluginRunner =
        EventPluginRunner.builder().configurationManager(this.configurationManager).build();
    this.updateSettingsTimer = new Timer(UPDATE_SETTINGS_TIMER_NAME);
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

    configurationManager.onChanged(
        ignored -> configurationManager.getSettingsManager().updateSettingsThreadSafe());
    configurationManager
        .getQueue()
        .onEventsPosted(
            (ignored1, ignored2) ->
                configurationManager.getSettingsManager().updateSettingsThreadSafe());
  }

  public static ExceptionlessClient from(String apiKey, String serverUrl) {
    return ExceptionlessClient.builder()
        .configurationManager(
            ConfigurationManager.builder()
                .configuration(Configuration.builder().apiKey(apiKey).serverUrl(serverUrl).build())
                .build())
        .build();
  }

  public void submitException(Exception exception) {
    Event event = createException().build();
    PluginContext pluginContext = PluginContext.builder().exception(exception).build();
    submitEvent(EventPluginContext.builder().event(event).context(pluginContext).build());
  }

  private Event.EventBuilder createException() {
    return createEvent().type(EventType.ERROR.value());
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

  public void submitFeatureUsage(String feature) {
    Event event = createFeatureUsage(feature).build();
    submitEvent(EventPluginContext.from(event));
  }

  private Event.EventBuilder createFeatureUsage(String feature) {
    return createEvent().type(EventType.USAGE.value()).source(feature);
  }

  public void submitLog(String message) {
    submitLog(message, null, null);
  }

  public void submitLog(String message, String source) {
    submitLog(message, source, null);
  }

  public void submitLog(String message, String source, String level) {
    Event event = createLog(message, source, level).build();
    submitEvent(EventPluginContext.from(event));
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

  public void submitNotFound(String resource) {
    Event event = createNotFound(resource).build();
    submitEvent(EventPluginContext.from(event));
  }

  private Event.EventBuilder createNotFound(String resource) {
    return createEvent().type(EventType.NOT_FOUND.value()).source(resource);
  }

  public void submitSessionStart() {
    Event event = createSessionStart().build();
    submitEvent(EventPluginContext.from(event));
  }

  private Event.EventBuilder createSessionStart() {
    return createEvent().type(EventType.SESSION.value());
  }

  private Event.EventBuilder createEvent() {
    return Event.builder()
        .dataExclusions(configurationManager.getDataExclusions())
        .date(LocalDate.now());
  }

  // todo this should be async
  public void submitEvent(EventPluginContext eventPluginContext) {
    eventPluginRunner.run(eventPluginContext);
  }

  public void submitSessionEnd(String sessionOrUserId) {
    configurationManager
        .getLog()
        .info(String.format("Submitting session end: %s", sessionOrUserId));
    configurationManager.getSubmissionClient().sendHeartBeat(sessionOrUserId, true);
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
      configurationManager
          .getLog()
          .error(
              String.format(
                  "Failed to submit user email and description for event: %s", referenceId));
    }

    return response;
  }

  public String getLastReferenceId() {
    return configurationManager.getLastReferenceIdManager().getLast();
  }
}
