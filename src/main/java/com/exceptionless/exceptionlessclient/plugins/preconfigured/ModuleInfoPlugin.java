package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.services.error.Error;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.services.error.Error;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.Optional;

public class ModuleInfoPlugin implements EventPluginIF {
  @Builder
  public ModuleInfoPlugin() {}

  @Override
  public int getPriority() {
    return 50;
  }

  @Override
  public void run(
          EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Optional<Error> maybeError = eventPluginContext.getEvent().getError();
    if (!maybeError.isPresent()) {
      return;
    }
    Error error = maybeError.get();

    if (!error.getModules().isEmpty()) {
      return;
    }

    error.setModules(configurationManager.getModuleCollector().getModules());
  }
}
