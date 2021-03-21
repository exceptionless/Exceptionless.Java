package com.prashantchaubey.exceptionlessclient;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.PluginContext;
import com.prashantchaubey.exceptionlessclient.models.UserDescription;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.models.enums.EventType;
import com.prashantchaubey.exceptionlessclient.models.submission.SubmissionResponse;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginManager;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;

@Builder(builderClassName = "ExceptionlessClientInternalBuilder")
@Getter
public class ExceptionlessClient {
  private static final int UPDATE_SETTINGS_TIMER_INITIAL_DELAY = 5000;

  private ConfigurationManager configurationManager;

  // lombok ignored fields
  private Timer $updateSettingsTimer;

  public static ExceptionlessClient from(String apiKey, String serverUrl) {
    return ExceptionlessClient.builder()
        .configurationManager(ConfigurationManager.from(apiKey, serverUrl))
        .build();
  }

  public void submitException(Exception exception, Consumer<Event> handler) {
    Event event = createException(exception).build();
    submitEvent(event, handler);
  }

  private Event.EventBuilderImpl createException(Exception exception) {
    return createEvent(PluginContext.builder().exception(exception).build())
        .type(EventType.ERROR.value());
  }

  public void submitUnhandledException(
      Exception exception, String submissionMethod, Consumer<Event> handler) {
    Event event = createUnhandledException(exception, submissionMethod).build();
    submitEvent(event, handler);
  }

  private Event.EventBuilderImpl createUnhandledException(
      Exception exception, String submissionMethod) {
    return createEvent(
            PluginContext.builder()
                .exception(exception)
                .markAsUnhandledError()
                .submissionMethod(submissionMethod)
                .build())
        .type(EventType.ERROR.value());
  }

  public void submitFeatureUsage(String feature, Consumer<Event> handler) {
    Event event = createFeatureUsage(feature).build();
    submitEvent(event, handler);
  }

  private Event.EventBuilderImpl createFeatureUsage(String feature) {
    return createEvent().type(EventType.USAGE.value()).source(feature);
  }

  public void submitLog(String message, Consumer<Event> handler) {
    submitLog(message, null, null, handler);
  }

  public void submitLog(String message, String source, Consumer<Event> handler) {
    submitLog(message, source, null, handler);
  }

  public void submitLog(String message, String source, String level, Consumer<Event> handler) {
    Event event = createLog(message, source, level).build();
    submitEvent(event, handler);
  }

  private Event.EventBuilderImpl createLog(String message, String source, String level) {
    if (source == null) {
      // Calling method
      source = Thread.currentThread().getStackTrace()[2].getMethodName();
    }

    Event.EventBuilderImpl builder =
        createEvent().type(EventType.LOG.value()).source(source).message(message);
    if (level == null) {
      return builder;
    }

    return builder.property(EventPropertyKey.LOG_LEVEL.value(), level);
  }

  public void submitNotFound(String resource, Consumer<Event> handler) {
    Event event = createNotFound(resource).build();
    submitEvent(event, handler);
  }

  private Event.EventBuilderImpl createNotFound(String resource) {
    return createEvent().type(EventType.NOT_FOUND.value()).source(resource);
  }

  public void submitSessionStart(Consumer<Event> handler) {
    Event event = createSessionStart().build();
    submitEvent(event, handler);
  }

  private Event.EventBuilderImpl createSessionStart() {
    return createEvent().type(EventType.SESSION.value());
  }

  private Event.EventBuilderImpl createEvent() {
    return createEvent(PluginContext.builder().build());
  }

  private Event.EventBuilderImpl createEvent(PluginContext pluginContext) {
    return Event.builder(configurationManager.getDataExclusions())
        .pluginContext(pluginContext)
        .date(LocalDate.now());
  }

  private void submitEvent(Event event, Consumer<Event> handler) {
    EventPluginManager.run(
        event,
        ev -> {
          if (ev.getPluginContext().isEventCancelled()) {
            return;
          }
          configurationManager.getQueue().enqueue(ev);
          if (ev.getReferenceId() != null) {
            configurationManager.getLastReferenceIdManager().setLast(ev.getReferenceId());
          }
          handler.accept(ev);
        });
  }

  public void submitSessionEnd(String sessionOrUserId) {
    configurationManager
        .getLog()
        .info(String.format("Submitting session end: %s", sessionOrUserId));
    configurationManager.getSubmissionClient().sendHeartBeat(sessionOrUserId, true);
  }

  public void submitSessionHeartbeat(String sessionOrUserId) {
    configurationManager
        .getLog()
        .info(String.format("Submitting session heartbeat: %s", sessionOrUserId));
    configurationManager.getSubmissionClient().sendHeartBeat(sessionOrUserId, false);
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

  public static class ExceptionlessClientBuilder extends ExceptionlessClientInternalBuilder {
    ExceptionlessClientBuilder() {
      super();
    }

    @Override
    public ExceptionlessClient build() {
      ExceptionlessClient client = super.build();
      client.init();

      return client;
    }
  }

  public static ExceptionlessClientBuilder builder() {
    return new ExceptionlessClientBuilder();
  }

  private void init() {
    $updateSettingsTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            configurationManager.getSettingsManager().updateSettingsThreadSafe();
          }
        },
        UPDATE_SETTINGS_TIMER_INITIAL_DELAY,
        configurationManager.getConfiguration().getUpdateSettingsWhenIdleInterval());
  }

  public static void main(String[] args) {
    System.out.println("Hello World!");
  }
}
