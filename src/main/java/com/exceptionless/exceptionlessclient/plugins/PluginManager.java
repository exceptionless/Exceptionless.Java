package com.exceptionless.exceptionlessclient.plugins;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.plugins.preconfigured.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
public class PluginManager {
  private static final Integer DEFAULT_PLUGIN_PRIORITY = 0;

  @Getter private List<EventPluginIF> plugins;

  @Builder
  public PluginManager() {
    this.plugins = new ArrayList<>();
    configureDefaultPlugins();
    sortPlugins();
  }

  private void configureDefaultPlugins() {
    addPlugin(ConfigurationDefaultsPlugin.builder().build());
    addPlugin(ErrorPlugin.builder().build());
    addPlugin(DuplicateCheckerPlugin.builder().build());
    addPlugin(EventExclusionPlugin.builder().build());
    addPlugin(ModuleInfoPlugin.builder().build());
    addPlugin(RequestInfoPlugin.builder().build());
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

  public void addPlugin(BiConsumer<EventPluginContext, Configuration> pluginAction) {
    addPlugin(UUID.randomUUID().toString(), DEFAULT_PLUGIN_PRIORITY, pluginAction);
  }

  public void addPlugin(
      String name,
      int priority,
      BiConsumer<EventPluginContext, Configuration> pluginAction) {
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
              EventPluginContext eventPluginContext, Configuration configuration) {
            pluginAction.accept(eventPluginContext, configuration);
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
