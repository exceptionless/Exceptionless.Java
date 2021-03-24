package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;

import java.util.function.Consumer;

public final class EventPluginManager {
  private EventPluginManager() {}

  public static void run(EventPluginContext eventPluginContext, Consumer<EventPluginContext> handler) {
    // todo implement
  }
}
