package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import lombok.Builder;

import java.util.List;
import java.util.function.Consumer;

public class EventPluginRunner {
  private ConfigurationManager configurationManager;

  @Builder
  public EventPluginRunner(ConfigurationManager configurationManager) {
    this.configurationManager = configurationManager;
  }

  public void run(EventPluginContext eventPluginContext, Consumer<EventPluginContext> handler) {
    List<EventPluginIF> plugins = configurationManager.getPlugins();
    plugins.add(
        new EventPluginIF() {
          @Override
          public int getPriority() {
            return Integer.MAX_VALUE;
          }

          @Override
          public String getName() {
            return "handler";
          }

          @Override
          public void run(
              EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
            handler.accept(eventPluginContext);
          }
        });

    plugins.sort(
        (o1, o2) -> o2.getPriority() - o1.getPriority()); // todo move this to configuration manager
    plugins.forEach(
        plugin -> {
          if (eventPluginContext.getContext().isEventCancelled()) {
            return;
          }

          try {
            plugin.run(eventPluginContext, configurationManager);

          } catch (Exception e) {
            configurationManager
                .getLog()
                .error(
                    String.format(
                        "Error running plugin: %s: %s. Discarding event",
                        plugin.getName(), e.getMessage()),
                    e);
            eventPluginContext.getContext().markAsCancelled();
          }
        });
  }
}
