package com.exceptionless.exceptionlessclient.services;

import com.exceptionless.exceptionlessclient.models.services.Module;
import lombok.Builder;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultModuleCollector implements ModuleCollectorIF {
  private final List<Module> modules;

  @Builder
  public DefaultModuleCollector() {
    this.modules =
        ModuleLayer.boot().modules().stream()
            .map(module -> Module.builder().name(module.getName()).build())
            .collect(Collectors.toList());
  }

  @Override
  public List<Module> getModules() {
    return modules;
  }
}
