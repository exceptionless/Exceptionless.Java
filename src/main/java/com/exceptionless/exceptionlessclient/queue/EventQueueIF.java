package com.exceptionless.exceptionlessclient.queue;

import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.submission.SubmissionResponse;

import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;

public interface EventQueueIF {
  void enqueue(Event event);

  void process();

  void suspendProcessing(Duration duration, boolean discardFutureQueueItems, boolean clearQueue);

  void onEventsPosted(BiConsumer<List<Event>, SubmissionResponse> handler);
}
