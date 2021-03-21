package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.models.Event;

import java.util.function.Consumer;

public final class EventPluginManager {
  private EventPluginManager() {}

  public static void run(Event event, Consumer<Event> handler) {
    // todo implement
  }
}
