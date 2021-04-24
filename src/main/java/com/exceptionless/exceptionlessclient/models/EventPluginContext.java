package com.exceptionless.exceptionlessclient.models;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class EventPluginContext {
  Event event;
  PluginContext context;

  public static EventPluginContext from(Event event) {
    return EventPluginContext.builder()
        .context(PluginContext.builder().build())
        .event(event)
        .build();
  }
}
