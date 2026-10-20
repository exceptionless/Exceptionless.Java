package com.exceptionless.queue;

import com.exceptionless.models.Event;
import java.time.Duration;

/** Queues events and sends them to the server in batches. */
public interface EventQueue {
  /** Serializes and queues an event. Never blocks on the network. */
  void enqueue(Event event);

  /** Sends queued events now. Blocks the calling thread until done or suspended. */
  void process();

  /** Stops sending for {@code duration}, keeping queued and new events. */
  default void suspendProcessing(Duration duration) {
    suspendProcessing(duration, false, false);
  }

  /**
   * Stops sending for {@code duration}.
   *
   * @param discardFutureQueuedItems drop events enqueued while suspended
   * @param clearQueue drop the events already queued
   */
  void suspendProcessing(Duration duration, boolean discardFutureQueuedItems, boolean clearQueue);

  /** Resumes sending immediately after a suspension. */
  void resume();

  /**
   * {@return whether sending is paused} The queue pauses after errors and rate limiting, or when
   * {@link #suspendProcessing} is called; events are still queued while it is paused.
   */
  boolean isSuspended();

  /** {@return the number of queued events} */
  int size();

  /** Adds a listener called after each batch is posted. */
  void onEventsPosted(EventsPostedListener listener);
}
