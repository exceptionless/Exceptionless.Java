package com.exceptionless.exceptionlessclient.queue;

import com.exceptionless.exceptionlessclient.models.submission.SubmissionResponse;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.submission.SubmissionResponse;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;

public interface EventQueueIF {
  void enqueue(Event event);

  void process(boolean isAppExiting);

  void suspendProcessing(Duration duration, boolean discardFutureQueueItems, boolean clearQueue);

  void onEventsPosted(BiConsumer<List<Event>, SubmissionResponse> handler);
}
