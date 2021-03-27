package com.prashantchaubey.exceptionlessclient.configuration;

import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import com.prashantchaubey.exceptionlessclient.plugins.preconfigured.*;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class PluginManager {
    private LogIF log;
    @Getter
    private List<EventPluginIF> plugins;

    @Builder
    public PluginManager(LogIF log){
        configureDefaultPlugins();
    }

    private void configureDefaultPlugins() {
        addPlugin(ConfigurationDefaultsPlugin.builder().build());
        addPlugin(ErrorPlugin.builder().build());
        addPlugin(DuplicateCheckerPlugin.builder().build());
        addPlugin(EventExclusionPlugin.builder().build());
        addPlugin(ModuleInfoPlugin.builder().build());
        addPlugin(EnvironmentInfoPlugin.builder().build());
        addPlugin(SubmissionMethodPlugin.builder().build());
    }

    public void addPlugin(EventPluginIF eventPlugin) {
        if (plugins.stream().anyMatch(plugin -> plugin.getName().equals(eventPlugin.getName()))) {
            log.info(
                    String.format(
                            "Can't add plugin, name: %s, priority: %s as a plugin with this name already configured",
                            eventPlugin.getName(), eventPlugin.getPriority()));
        }
        plugins.add(eventPlugin);
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
