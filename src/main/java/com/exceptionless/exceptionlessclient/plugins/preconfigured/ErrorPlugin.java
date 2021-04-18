package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.models.enums.EventType;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ErrorPlugin implements EventPluginIF {
  private static final Integer DEFAULT_PRIORITY = 30;

  private final Set<String> dataExclusions;

  @Builder
  public ErrorPlugin() {
    this.dataExclusions =
        new HashSet<>(
            Arrays.asList(
                "arguments",
                "column",
                "columnNumber",
                "description",
                "fileName",
                "message",
                "name",
                "number",
                "line",
                "lineNumber",
                "opera#sourceloc",
                "sourceId",
                "sourceURL",
                "stack",
                "stackArray",
                "stacktrace"));
  }

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
    dataExclusions.addAll(this.dataExclusions);
    event.addData(Map.of(EventPropertyKey.EXTRA.value(), exception), dataExclusions);
  }
}
