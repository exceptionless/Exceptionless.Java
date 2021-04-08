package com.exceptionless.exceptionlessclient.queue;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.exceptions.ClientException;
import com.exceptionless.exceptionlessclient.logging.LogIF;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.storage.StorageItem;
import com.exceptionless.exceptionlessclient.models.submission.SubmissionResponse;
import com.exceptionless.exceptionlessclient.storage.StorageProviderIF;
import com.exceptionless.exceptionlessclient.submission.SubmissionClientIF;
import lombok.Builder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DefaultEventQueue implements EventQueueIF {
  private static final String QUEUE_TIMER_NAME = "queue-timer";

  private final LogIF log;
  private final StorageProviderIF storageProvider;
  private final Configuration configuration;
  private final SubmissionClientIF submissionClient;
  private LocalDateTime discardQueueItemsUntil;
  private LocalDateTime suspendProcessingUntil;
  private boolean processingQueue;
  private final Timer queueTimer;
  private int currentSubmissionBatchSize;
  private final List<BiConsumer<List<Event>, SubmissionResponse>> handlers;

  @Builder
  public DefaultEventQueue(
      LogIF log,
      StorageProviderIF storageProvider,
      Configuration configuration,
      SubmissionClientIF submissionClient,
      Integer processingIntervalInSecs) {
    this.log = log;
    this.storageProvider = storageProvider;
    this.configuration = configuration;
    this.submissionClient = submissionClient;
    this.queueTimer = new Timer(QUEUE_TIMER_NAME);
    this.handlers = new ArrayList<>();
    this.currentSubmissionBatchSize = configuration.getSubmissionBatchSize();
    init(processingIntervalInSecs == null ? 10 : processingIntervalInSecs);
  }

  private void init(Integer processingIntervalInSecs) {
    queueTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            onProcessQueue();
          }
        },
        processingIntervalInSecs * 1000);
  }

  private void onProcessQueue() {
    if (processingQueue || shouldSuspendProcessing()) {
      return;
    }

    process();
  }

  private boolean shouldSuspendProcessing() {
    if (suspendProcessingUntil == null) {
      return false;
    }

    return LocalDateTime.now().isBefore(suspendProcessingUntil);
  }

  private void process() {
    process(false);
  }

  @Override
  public void enqueue(Event event) {
    if (shouldDiscard()) {
      log.info("Queue items are currently being discarded. This event will not be enqueued");
    }

    long timestamp = storageProvider.getQueue().save(event);
    String logText =
        String.format("type: %s", event.getType())
            + (event.getReferenceId() != null
                ? String.format("refId: %s", event.getReferenceId())
                : "");
    log.info(String.format("Enqueueing event: %s %s", timestamp, logText));
  }

  private boolean shouldDiscard() {
    if (discardQueueItemsUntil == null) {
      return false;
    }

    return LocalDateTime.now().isBefore(discardQueueItemsUntil);
  }

  @Override
  public void process(boolean isAppExiting) {
    if (processingQueue) {
      return;
    }

    processingQueue = true;
    try {
      List<StorageItem<Event>> storedEvents =
          storageProvider.getQueue().get(currentSubmissionBatchSize);
      List<Event> events =
          storedEvents.stream().map(StorageItem::getValue).collect(Collectors.toList());

      log.info(
          String.format("Sending %s events to %s", events.size(), configuration.getServerUrl()));
      SubmissionResponse response = submissionClient.postEvents(events, isAppExiting);
      processSubmissionResponse(response, storedEvents);
      eventPosted(response, events);
    } catch (ClientException e) {
      log.error("Error processing queue", e);
      suspendProcessing();
    } finally {
      processingQueue = false;
    }
  }

  private void processSubmissionResponse(
      SubmissionResponse response, List<StorageItem<Event>> storedEvents) {
    if (response.isSuccess()) {
      log.info(String.format("Sent %s events", storedEvents.size()));
      setBatchSizeToConfigured();
      removeEvents(storedEvents);
      return;
    }

    if (response.isServiceUnavailable()) {
      log.error("Service returns service unavailable");
      suspendProcessing();
      return;
    }

    if (response.isPaymentRequired()) {
      log.info("Too many events have been submitted, please upgrade your plan");
      suspendProcessingForNoPayment();
      return;
    }

    if (response.unableToAuthenticate()) {
      log.info(
          "Unable to authenticate, please check your configuration. Events will not be submitted");
      suspendProcessing(Duration.ofMinutes(15));
      removeEvents(storedEvents);
      return;
    }

    if (response.isNotFound() || response.isBadRequest()) {
      log.error(String.format("Error while trying to submit data: %s", response.getMessage()));
      suspendProcessing(Duration.ofMinutes(4));
      removeEvents(storedEvents);
      return;
    }

    if (response.isRequestEntityTooLarge()) {
      if (currentSubmissionBatchSize > 1) {
        log.error(
            "Event submission discarded for being too large. Retrying with smaller batch size");
        currentSubmissionBatchSize =
            Math.max(1, (int) Math.round(currentSubmissionBatchSize / 1.5));
      } else {
        log.error("Event submission discarded for being too large. Events will not be submitted");
        removeEvents(storedEvents);
      }
      return;
    }

    log.error(String.format("Error submitting events: %s", response.getMessage()));
    suspendProcessing();
  }

  private void setBatchSizeToConfigured() {
    currentSubmissionBatchSize = configuration.getSubmissionBatchSize();
  }

  private void removeEvents(List<StorageItem<Event>> storedEvents) {
    for (StorageItem<Event> storedEvent : storedEvents) {
      storageProvider.getQueue().remove(storedEvent.getTimestamp());
    }
  }

  private void eventPosted(SubmissionResponse response, List<Event> events) {
    for (BiConsumer<List<Event>, SubmissionResponse> handler : handlers) {
      try {
        handler.accept(events, response);
      } catch (Exception e) {
        log.error("Error while processing an event submission handler", e);
      }
    }
  }

  private void suspendProcessing() {
    suspendProcessing(null, false, false);
  }

  private void suspendProcessingForNoPayment() {
    suspendProcessing(null, true, true);
  }

  private void suspendProcessing(Duration duration) {
    suspendProcessing(duration, false, false);
  }

  @Override
  public void suspendProcessing(
      Duration duration, boolean discardFutureQueueItems, boolean clearQueue) {
    if (duration == null) {
      duration = Duration.ofMinutes(5);
    }

    log.info(String.format("Suspending processing for %s", duration));
    suspendProcessingUntil = LocalDateTime.now().plus(duration);
    if (discardFutureQueueItems) {
      discardQueueItemsUntil = suspendProcessingUntil;
    }

    if (clearQueue) {
      storageProvider.getQueue().clear();
    }
  }

  @Override
  public void onEventsPosted(BiConsumer<List<Event>, SubmissionResponse> handler) {
    handlers.add(handler);
  }
}
