package com.exceptionless;

import com.exceptionless.models.Event;
import com.exceptionless.plugins.ContextData;

/**
 * Passed to {@link ExceptionlessClient#addSubmittingEventHandler} handlers after the plugins run
 * and before the event is queued. Call {@link #cancel()} to discard the event.
 */
public final class SubmittingEvent {
  private final ExceptionlessClient client;
  private final Event event;
  private final ContextData contextData;
  private boolean cancelled;

  SubmittingEvent(ExceptionlessClient client, Event event, ContextData contextData) {
    this.client = client;
    this.event = event;
    this.contextData = contextData;
  }

  public ExceptionlessClient getClient() {
    return client;
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

  public void cancel() {
    this.cancelled = true;
  }
}
