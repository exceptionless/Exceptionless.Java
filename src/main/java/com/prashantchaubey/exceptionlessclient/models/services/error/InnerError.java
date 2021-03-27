package com.prashantchaubey.exceptionlessclient.models.services.error;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Value
@NonFinal
@EqualsAndHashCode(callSuper = true)
public class InnerError extends Model {
  private String message;
  private String type;
  private String code;
  private InnerError inner;
  private List<StackFrame> stackTrace;
  private Method targetMethod;
}
