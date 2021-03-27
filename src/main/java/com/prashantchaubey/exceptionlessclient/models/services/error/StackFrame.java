package com.prashantchaubey.exceptionlessclient.models.services.error;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class StackFrame extends Method {
  private String filename;
  private int lineNumber;
  private int column;
}
