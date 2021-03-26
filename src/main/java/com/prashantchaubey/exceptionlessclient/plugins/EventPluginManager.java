package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import lombok.Builder;
import lombok.Getter;

import java.util.function.Consumer;

@Builder
@Getter
public class EventPluginManager {
  private ConfigurationManager configurationManager;

  public void run(EventPluginContext eventPluginContext, Consumer<EventPluginContext> handler) {
    // todo implement
  }
}
