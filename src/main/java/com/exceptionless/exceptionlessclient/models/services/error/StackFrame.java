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
  String filename;
  int lineNumber;
  int column;
}
