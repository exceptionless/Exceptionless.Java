package com.exceptionless.exceptionlessclient.models.services.error;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class StackFrame extends Method {
  private String filename;
  private int lineNumber;
  private int column;
}
