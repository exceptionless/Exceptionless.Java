package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.models.Event;

/** The event being processed, plus its context data and the client processing it. */
public final class EventPluginContext {
  private final ExceptionlessClient client;
  private final Event event;
  private final ContextData contextData;
  private volatile boolean cancelled;

  public EventPluginContext(ExceptionlessClient client, Event event, ContextData contextData) {
    this.client = client;
    this.event = event;
    this.contextData = contextData;
  }

  public ExceptionlessClient getClient() {
    return client;
  }

  public ExceptionlessConfiguration getConfiguration() {
    return client.getConfiguration();
  }

  public Event getEvent() {
    return event;
  }

  public ContextData getContextData() {
    return contextData;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  /** Discards the event; no further plugins run. */
  public void cancel() {
    this.cancelled = true;
  }

  public void setCancelled(boolean cancelled) {
    this.cancelled = cancelled;
  }
}
