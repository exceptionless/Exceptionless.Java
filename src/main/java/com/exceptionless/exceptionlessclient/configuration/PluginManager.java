package com.exceptionless.exceptionlessclient.configuration;

import com.exceptionless.exceptionlessclient.logging.LogIF;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.plugins.preconfigured.*;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class PluginManager {
  private final LogIF log;
  @Getter private List<EventPluginIF> plugins;

  @Builder
  public PluginManager(LogIF log) {
    this.log = log;
    this.plugins = new ArrayList<>();
    configureDefaultPlugins();
    sortPlugins();
  }

  private void configureDefaultPlugins() {
    addPlugin(ConfigurationDefaultsPlugin.builder().build());
    addPlugin(ErrorPlugin.builder().build());
    addPlugin(DuplicateErrorCheckerPlugin.builder().log(this.log).build());
    addPlugin(EventExclusionPlugin.builder().log(this.log).build());
    addPlugin(ModuleInfoPlugin.builder().build());
    addPlugin(RequestInfoPlugin.builder().log(this.log).build());
    addPlugin(EnvironmentInfoPlugin.builder().build());
    addPlugin(SubmissionMethodPlugin.builder().build());
  }

  public void addPlugin(EventPluginIF eventPlugin) {
    if (plugins.stream().anyMatch(plugin -> plugin.getName().equals(eventPlugin.getName()))) {
      log.info(
          String.format(
              "Can't add plugin, name: %s, priority: %s as a plugin with this name already configured",
              eventPlugin.getName(), eventPlugin.getPriority()));
      return;
    }
    plugins.add(eventPlugin);
    sortPlugins();
  }

  private void sortPlugins() {
    plugins.sort((o1, o2) -> o2.getPriority() - o1.getPriority());
  }

  public void addPlugin(BiConsumer<EventPluginContext, ConfigurationManager> pluginAction) {
    addPlugin(UUID.randomUUID().toString(), 0, pluginAction);
  }

  public void addPlugin(
      String name,
      int priority,
      BiConsumer<EventPluginContext, ConfigurationManager> pluginAction) {
    addPlugin(
        new EventPluginIF() {
          @Override
          public int getPriority() {
            return priority;
          }

          @Override
          public String getName() {
            return name;
          }

          @Override
          public void run(
              EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
            pluginAction.accept(eventPluginContext, configurationManager);
          }
        });
  }

  public void removePlugin(String name) {
    plugins =
        plugins.stream()
            .filter(eventPlugin -> !eventPlugin.getName().equals(name))
            .collect(Collectors.toList());
  }
}
