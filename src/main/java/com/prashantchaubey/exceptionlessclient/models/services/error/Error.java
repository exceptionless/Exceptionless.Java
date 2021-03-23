package com.prashantchaubey.exceptionlessclient.models.services.error;

import com.prashantchaubey.exceptionlessclient.models.services.Module;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@Getter
public class Error extends InnerError {
  private List<Module> modules;
}
