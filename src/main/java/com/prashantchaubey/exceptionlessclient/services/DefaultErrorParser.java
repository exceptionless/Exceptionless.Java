package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.PluginContext;
import com.prashantchaubey.exceptionlessclient.models.services.error.Error;
import com.prashantchaubey.exceptionlessclient.models.services.error.StackFrame;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultErrorParser implements ErrorParserIF {
  @Override
  public Error parse(PluginContext context, Exception exception) {
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
