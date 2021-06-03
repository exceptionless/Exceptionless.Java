package com.exceptionless.exceptionlessclient.plugins;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventPluginRunner {
  private final ConfigurationManager configurationManager;

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
                    log.error(
                        String.format(
                            "Error running plugin: %s: %s. Discarding event",
                            plugin.getName(), e.getMessage()),
                        e);
                eventPluginContext.getContext().setEventCancelled(true);
              }
            });

    if (eventPluginContext.getContext().isEventCancelled()) {
          log.info(
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
