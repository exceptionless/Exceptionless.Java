package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.enums.EventType;
import com.exceptionless.exceptionlessclient.models.services.error.Error;
import com.exceptionless.exceptionlessclient.models.settings.ServerSettings;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalInt;

public class EventExclusionPlugin implements EventPluginIF {
  private static final Logger LOG = LoggerFactory.getLogger(EventExclusionPlugin.class);

  @Builder
  public EventExclusionPlugin() {
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
      if (maybeSetting.isEmpty() || ServerSettings.getAsBoolean(maybeSetting.get())) {
        return;
      }
      LOG.info(
          String.format(
              "Cancelling event from excluded type: %s and source :%s",
              event.getType(), event.getSource()));
      eventPluginContext.getContext().setEventCancelled(true);
    }
  }

  private void handleLogEvent(
      EventPluginContext eventPluginContext, ServerSettings serverSettings) {
    Event event = eventPluginContext.getEvent();
    Optional<String> maybeLogSetting =
        serverSettings.getTypeAndSourceSetting(EventType.LOG.value(), event.getSource());
    if (maybeLogSetting.isEmpty()) {
      return;
    }
    Optional<String> maybeLogLevel = event.getLogLevel();
    if (maybeLogLevel.isEmpty()) {
      return;
    }

    OptionalInt maybeMinLogPriority = getLogPriority(maybeLogSetting.get());
    OptionalInt maybeLogPriority = getLogPriority(maybeLogLevel.get());
    if (maybeLogPriority.isEmpty() || maybeMinLogPriority.isEmpty()) {
      return;
    }

    if (maybeLogPriority.getAsInt() >= maybeMinLogPriority.getAsInt()) {
      return;
    }

    eventPluginContext.getContext().setEventCancelled(true);
  }

  private OptionalInt getLogPriority(String level) {
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
    Optional<Error> maybeError = eventPluginContext.getEvent().getError();
    if (maybeError.isEmpty()) {
      return;
    }
    Error error = maybeError.get();

    while (error != null) {
      Optional<String> maybeSetting =
          serverSettings.getTypeAndSourceSetting(EventType.ERROR.value(), error.getType());
      if (maybeSetting.isPresent() && !ServerSettings.getAsBoolean(maybeSetting.get())) {
        LOG.info(
            String.format("Cancelling error from excluded exception type: %s", error.getType()));
        eventPluginContext.getContext().setEventCancelled(true);
        break;
      }
      error = (Error) error.getInner();
    }
  }
}
