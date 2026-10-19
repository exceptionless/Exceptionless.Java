package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.models.Event;

/** The event being processed, plus its context data and the client processing it. */
public final class EventPluginContext {
  private final ExceptionlessClient client;
  private final Event event;
  private final ContextData contextData;
  private boolean cancelled;

  public EventPluginContext(ExceptionlessClient client, Event event, ContextData contextData) {
    this.client = client;
    this.event = event;
    this.contextData = contextData;
  }

  /** {@return the client processing the event} */
  public ExceptionlessClient getClient() {
    return client;
  }

  /** {@return the client's configuration} */
  public ExceptionlessConfiguration getConfiguration() {
    return client.getConfiguration();
  }

  /** The event being processed. Plugins may change it. */
  public Event getEvent() {
    return event;
  }

  /** Data available to plugins but not sent to the server, such as the exception. */
  public ContextData getContextData() {
    return contextData;
  }

  /** Whether a plugin {@link #cancel() cancelled} the event. */
  public boolean isCancelled() {
    return cancelled;
  }

  /** Discards the event; no further plugins run. */
  public void cancel() {
    this.cancelled = true;
  }
}
