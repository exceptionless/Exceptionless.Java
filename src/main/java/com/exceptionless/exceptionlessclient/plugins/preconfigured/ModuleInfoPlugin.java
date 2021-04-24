package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.services.Module;
import com.exceptionless.exceptionlessclient.models.services.error.Error;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ModuleInfoPlugin implements EventPluginIF {
  private static final Integer DEFAULT_PRIORITY = 50;

  private final List<Module> modules;

  @Builder
  public ModuleInfoPlugin() {
    this.modules =
        ModuleLayer.boot().modules().stream()
            .map(module -> Module.builder().name(module.getName()).build())
            .collect(Collectors.toList());
  }

  @Override
  public int getPriority() {
    return DEFAULT_PRIORITY;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Optional<Error> maybeError = eventPluginContext.getEvent().getError();
    if (maybeError.isEmpty()) {
      return;
    }
    Error error = maybeError.get();

    if (!error.getModules().isEmpty()) {
      return;
    }

    error.setModules(modules);
  }
}
