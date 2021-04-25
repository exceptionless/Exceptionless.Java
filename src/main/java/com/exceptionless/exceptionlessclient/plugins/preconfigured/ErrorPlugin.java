package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.enums.EventType;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ErrorPlugin implements EventPluginIF {
  private static final Integer DEFAULT_PRIORITY = 30;

  @Builder
  public ErrorPlugin() {}

  @Override
  public int getPriority() {
    return DEFAULT_PRIORITY;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Exception exception = eventPluginContext.getContext().getException();
    if (exception == null) {
      return;
    }

    Event event = eventPluginContext.getEvent();
    event.setType(EventType.ERROR.value());
    if (event.getError().isPresent()) {
      return;
    }

    event.addError(configurationManager.getErrorParser().parse(exception));

    Set<String> dataExclusions = new HashSet<>(configurationManager.getDataExclusions());
    event.addData(Map.of(EventPropertyKey.EXTRA.value(), exception), dataExclusions);
  }
}
