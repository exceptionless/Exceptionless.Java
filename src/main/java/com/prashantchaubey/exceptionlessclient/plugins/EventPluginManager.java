package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.plugins.preconfigured.*;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.function.Consumer;

@Builder(builderClassName = "EventPluginInternalBuilder")
@Getter
public class EventPluginManager {
  private ConfigurationManager configurationManager;

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

    plugins.sort((o1, o2) -> o2.getPriority() - o1.getPriority());
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

  public static EventPluginBuilder builder() {
    return new EventPluginBuilder();
  }

  public static class EventPluginBuilder extends EventPluginInternalBuilder {
    @Override
    public EventPluginManager build() {
      EventPluginManager manager = super.build();
      manager.init();
      return manager;
    }
  }

  private void init() {
    configurationManager.addPlugin(ConfigurationDefaultsPlugin.builder().build());
    configurationManager.addPlugin(ErrorPlugin.builder().build());
    configurationManager.addPlugin(DuplicateCheckerPlugin.builder().build());
    configurationManager.addPlugin(EventExclusionPlugin.builder().build());
    configurationManager.addPlugin(ModuleInfoPlugin.builder().build());
    configurationManager.addPlugin(EnvironmentInfoPlugin.builder().build());
    configurationManager.addPlugin(SubmissionMethodPlugin.builder().build());
  }
}
