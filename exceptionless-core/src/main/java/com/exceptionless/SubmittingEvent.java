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

  /** The client submitting the event. */
  public ExceptionlessClient getClient() {
    return client;
  }

  /** The event about to be queued. Handlers may change it. */
  public Event getEvent() {
    return event;
  }

  /** Data available to handlers but not sent to the server, such as the exception. */
  public ContextData getContextData() {
    return contextData;
  }

  /** Whether a handler {@link #cancel() cancelled} the event. */
  public boolean isCancelled() {
    return cancelled;
  }

  /** Discards the event; it is not queued. */
  public void cancel() {
    this.cancelled = true;
  }
}
