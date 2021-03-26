package com.prashantchaubey.exceptionlessclient.models.services.error;

import com.prashantchaubey.exceptionlessclient.models.services.Module;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@SuperBuilder
@Getter
@Setter
public class Error extends InnerError {
  @Builder.Default
  private List<Module> modules = new ArrayList<>();
}
