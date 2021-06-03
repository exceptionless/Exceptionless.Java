package com.exceptionless.exceptionlessclient.models.error;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
@EqualsAndHashCode(callSuper = true)
public class StackFrame extends Method {
  String filename;
  int lineNumber;
  int column;
}
