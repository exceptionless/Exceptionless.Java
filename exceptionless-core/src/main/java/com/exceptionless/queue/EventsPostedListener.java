package com.exceptionless.queue;

import com.exceptionless.submission.SubmissionResponse;

/** Notified after the queue posts a batch of events. */
@FunctionalInterface
public interface EventsPostedListener {
  /**
   * Called after a batch is posted.
   *
   * @param count the number of events in the batch
   * @param response the server's response
   */
  void eventsPosted(int count, SubmissionResponse response);
}
