package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import lombok.Builder;

public class EventPluginRunner {
  private ConfigurationManager configurationManager;

  @Builder
  public EventPluginRunner(ConfigurationManager configurationManager) {
    this.configurationManager = configurationManager;
  }

  public void run(EventPluginContext eventPluginContext) {
    configurationManager
        .getPlugins()
        .forEach(
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
                eventPluginContext.getContext().setEventCancelled(true);
              }
            });

    if (eventPluginContext.getContext().isEventCancelled()) {
      configurationManager
          .getLog()
          .info(
              String.format(
                  "Event cancelled during plugin runs; Not submitting: %s",
                  eventPluginContext.getEvent().getReferenceId()));
      return;
    }

    configurationManager.getQueue().enqueue(eventPluginContext.getEvent());
    configurationManager
        .getLastReferenceIdManager()
        .setLast(eventPluginContext.getEvent().getReferenceId());
  }
}
