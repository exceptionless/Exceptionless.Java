package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.services.error.Error;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ModuleInfoPlugin implements EventPluginIF {
  @Override
  public int getPriority() {
    return 50;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Error error = eventPluginContext.getEvent().getError();
    if (error == null) {
      return;
    }

    if (error.getModules().isEmpty()) {
      return;
    }
    error.setModules(configurationManager.getModuleCollector().getModules());
  }
}
