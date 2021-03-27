package com.prashantchaubey.exceptionlessclient.services;

import com.prashantchaubey.exceptionlessclient.models.services.Module;
import lombok.Builder;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultModuleCollector implements ModuleCollectorIF {
  @Builder
  public DefaultModuleCollector() {}

  @Override
  public List<Module> getModules() {
    return ModuleLayer.boot().modules().stream()
        .map(module -> Module.builder().name(module.getName()).build())
        .collect(Collectors.toList());
  }
}
