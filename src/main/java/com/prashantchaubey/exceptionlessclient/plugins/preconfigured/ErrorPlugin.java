package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.models.enums.EventType;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ErrorPlugin implements EventPluginIF {

  private Set<String> dataExclusions;

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
    return 30;
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
    event.addData(
        Map.of(
            EventPropertyKey.ERROR.value(),
            configurationManager.getErrorParser().parse(exception)));
    Set<String> dataExclusions = new HashSet<>(configurationManager.getDataExclusions());
    dataExclusions.addAll(this.dataExclusions);
    event.addData(Map.of(EventPropertyKey.EXTRA.value(), exception), dataExclusions);
  }
}
