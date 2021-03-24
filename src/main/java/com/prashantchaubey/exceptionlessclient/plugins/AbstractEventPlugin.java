package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public abstract class AbstractEventPlugin {
  private int priority;
  private String name;

  public abstract void run(EventPluginContext eventPluginContext);
}
