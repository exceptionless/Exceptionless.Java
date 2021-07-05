package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.enums.EventPropertyKey;
import com.exceptionless.exceptionlessclient.enums.EventType;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.error.Error;
import com.exceptionless.exceptionlessclient.models.error.StackFrame;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.*;
import java.util.stream.Collectors;

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
      EventPluginContext eventPluginContext, Configuration configuration) {
    Exception exception = eventPluginContext.getContext().getException();
    if (exception == null) {
      return;
    }

    Event event = eventPluginContext.getEvent();
    event.setType(EventType.ERROR.value());
    if (event.getError().isPresent()) {
      return;
    }

    event.addError(parse(exception));

    Set<String> dataExclusions = new HashSet<>(configuration.getDataExclusions());
    event.addData(Map.of(EventPropertyKey.EXTRA.value(), exception), dataExclusions);
  }

  private Error parse(Exception exception) {
    return Error.builder()
        .type(exception.getClass().getCanonicalName())
        .message(exception.getMessage())
        .stackTrace(getStackFrames(exception))
        .build();
  }

  private List<StackFrame> getStackFrames(Exception exception) {
    return Arrays.stream(exception.getStackTrace())
        .map(
            stackTraceElement ->
                StackFrame.builder()
                    .name(stackTraceElement.getMethodName())
                    .filename(stackTraceElement.getFileName())
                    .lineNumber(stackTraceElement.getLineNumber())
                    .declaringType(stackTraceElement.getClassName())
                    .build())
        .collect(Collectors.toList());
  }
}
