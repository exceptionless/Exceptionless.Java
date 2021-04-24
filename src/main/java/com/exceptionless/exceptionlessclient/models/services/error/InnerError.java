package com.exceptionless.exceptionlessclient.models.services.error;

import com.exceptionless.exceptionlessclient.models.base.Model;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Value
@EqualsAndHashCode(callSuper = true)
public class InnerError extends Model {
  String message;
  String type;
  String code;
  InnerError inner;
  List<StackFrame> stackTrace;
  Method targetMethod;
}
