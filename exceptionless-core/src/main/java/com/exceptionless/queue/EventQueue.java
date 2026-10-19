package com.exceptionless.queue;

import com.exceptionless.models.Event;
import com.exceptionless.submission.SubmissionResponse;
import java.time.Duration;
import java.util.function.BiConsumer;

/** Queues events and sends them to the server in batches. */
public interface EventQueue {
  /** Serializes and queues an event. Never blocks on the network. */
  void enqueue(Event event);

  /** Sends queued events now. Blocks the calling thread until done or suspended. */
  void process();

  /**
   * Stops sending for {@code duration}.
   *
   * @param discardFutureQueuedItems drop events enqueued while suspended
   * @param clearQueue drop the events already queued
   */
  void suspendProcessing(Duration duration, boolean discardFutureQueuedItems, boolean clearQueue);

  /** Resumes sending immediately after a suspension. */
  void resume();

  boolean isSuspended();

  /** The number of queued events. */
  int size();

  /** Called after each batch is posted with the number of events and the response. */
  void onEventsPosted(BiConsumer<Integer, SubmissionResponse> handler);
}
