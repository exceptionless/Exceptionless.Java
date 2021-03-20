package com.prashantchaubey.exceptionlessclient.queue;

import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.submission.SubmissionResponse;

import java.util.List;
import java.util.function.BiConsumer;

public interface EventQueueIF {
  void enqueue(Event event);

  void process(boolean isAppExiting);

  void suspendProcessing(
      int durationInMinutes, boolean discardFutureQueueItems, boolean clearQueue);

  void onEventsPosted(BiConsumer<List<Event>, SubmissionResponse> handler);
}
