package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.models.enums.EventType;
import com.prashantchaubey.exceptionlessclient.models.services.error.InnerError;
import com.prashantchaubey.exceptionlessclient.models.settings.ServerSettings;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.Optional;
import java.util.OptionalInt;

public class EventExclusionPlugin implements EventPluginIF {
  private LogIF log;

  @Builder
  public EventExclusionPlugin(LogIF log) {
    this.log = log;
  }

  @Override
  public int getPriority() {
    return 0;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    ServerSettings serverSettings =
        configurationManager.getSettingsManager().getSavedServerSettings();

    if (event.getType().equals(EventType.LOG.value())) {
      handleLogEvent(eventPluginContext, serverSettings);
    } else if (event.getType().equals(EventType.ERROR.value())) {
      handleErrorEvent(eventPluginContext, serverSettings);
    } else {
      Optional<String> maybeSetting =
          serverSettings.getTypeAndSourceSetting(event.getType(), event.getSource());
      if (!maybeSetting.isPresent() || !ServerSettings.getAsBoolean(maybeSetting.get())) {
        return;
      }
      log.info(
          String.format(
              "Cancelling event from excluded type: %s and source :%s",
              event.getType(), event.getSource()));
    }
  }

  private void handleLogEvent(
      EventPluginContext eventPluginContext, ServerSettings serverSettings) {
    Event event = eventPluginContext.getEvent();
    Optional<String> maybeSetting =
        serverSettings.getTypeAndSourceSetting(event.getType(), event.getSource());
    OptionalInt maybeMinLogLevel =
        maybeSetting.isPresent() ? getLogLevel(maybeSetting.get()) : OptionalInt.empty();
    OptionalInt maybeLevel =
        getLogLevel((String) event.getData().getOrDefault(EventPropertyKey.LOG_LEVEL.value(), ""));
    if (!maybeLevel.isPresent() || !maybeMinLogLevel.isPresent()) {
      return;
    }
    if (maybeLevel.getAsInt() >= maybeMinLogLevel.getAsInt()) {
      return;
    }
    log.info("Cancelling log event due to minimum log level");
    eventPluginContext.getContext().markAsCancelled();
  }

  private OptionalInt getLogLevel(String level) {
    switch (level) {
      case "trace":
      case "true":
      case "1":
      case "yes":
        return OptionalInt.of(0);
      case "debug":
        return OptionalInt.of(1);
      case "info":
        return OptionalInt.of(2);
      case "warn":
        return OptionalInt.of(3);
      case "error":
        return OptionalInt.of(4);
      case "fatal":
        return OptionalInt.of(5);
      case "off":
      case "false":
      case "0":
      case "no":
        return OptionalInt.of(6);
      default:
        return OptionalInt.empty();
    }
  }

  private void handleErrorEvent(
      EventPluginContext eventPluginContext, ServerSettings serverSettings) {
    Event event = eventPluginContext.getEvent();
    InnerError error = (InnerError) event.getData().get(EventPropertyKey.ERROR.value());
    while (error != null) {
      Optional<String> maybeSetting =
          serverSettings.getTypeAndSourceSetting(EventType.ERROR.value(), error.getType());
      if (maybeSetting.isPresent() && ServerSettings.getAsBoolean(maybeSetting.get())) {
        log.info(
            String.format("Cancelling error from excluded exception type: %s", error.getType()));
        eventPluginContext.getContext().markAsCancelled();
        break;
      }
      error = error.getInner();
    }
  }
}
