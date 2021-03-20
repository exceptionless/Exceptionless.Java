package com.prashantchaubey.exceptionlessclient.queue;

import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.submission.SubmissionResponse;

import java.util.List;
import java.util.function.BiConsumer;

public class DefaultEventQueue implements EventQueueIF {
  @Override
  public void enqueue(Event event) {}

  @Override
  public void process(boolean isAppExiting) {}

  @Override
  public void suspendProcessing(
      int durationInMinutes, boolean discardFutureQueueItems, boolean clearQueue) {}

  @Override
  public void onEventsPosted(BiConsumer<List<Event>, SubmissionResponse> handler) {}
}
