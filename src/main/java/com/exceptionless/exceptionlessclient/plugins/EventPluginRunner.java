package com.exceptionless.exceptionlessclient.plugins;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventPluginRunner {
  private final Configuration configuration;

  @Builder
  public EventPluginRunner(Configuration configuration) {
    this.configuration = configuration;
  }

  public void run(EventPluginContext eventPluginContext) {
    configuration
        .getPlugins()
        .forEach(
            plugin -> {
              if (eventPluginContext.getContext().isEventCancelled()) {
                return;
              }

              try {
                plugin.run(eventPluginContext, configuration);

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

    configuration.getQueue().enqueue(eventPluginContext.getEvent());
    configuration
        .getLastReferenceIdManager()
        .setLast(eventPluginContext.getEvent().getReferenceId());
  }
}
