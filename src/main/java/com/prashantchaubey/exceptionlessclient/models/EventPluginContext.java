package com.prashantchaubey.exceptionlessclient.models;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class EventPluginContext {
  private Event event;
  private PluginContext context;

  public static EventPluginContext from(Event event) {
    return EventPluginContext.builder()
        .context(PluginContext.builder().build())
        .event(event)
        .build();
  }
}
