package com.exceptionless.exceptionlessclient.queue;

import com.exceptionless.exceptionlessclient.configuration.Configuration;
import com.exceptionless.exceptionlessclient.exceptions.SubmissionClientException;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.storage.StorageItem;
import com.exceptionless.exceptionlessclient.models.submission.SubmissionResponse;
import com.exceptionless.exceptionlessclient.storage.StorageProviderIF;
import com.exceptionless.exceptionlessclient.submission.SubmissionClientIF;
import com.exceptionless.exceptionlessclient.utils.VisibleForTesting;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class DefaultEventQueue implements EventQueueIF {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultEventQueue.class);
  private static final String QUEUE_TIMER_NAME = "queue-timer";
  private static final Integer DEFAULT_PROCESSING_INTERVAL_IN_SECS = 10;
  private static final Double BATCH_SIZE_DIVISOR = 1.5;
  private static final Integer DEFAULT_SUSPENSION_DURATION_IN_MINS = 5;

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
      StorageProviderIF storageProvider,
      Configuration configuration,
      SubmissionClientIF submissionClient,
      Integer processingIntervalInSecs) {
    this.storageProvider = storageProvider;
    this.configuration = configuration;
    this.submissionClient = submissionClient;
    this.queueTimer = new Timer(QUEUE_TIMER_NAME);
    this.handlers = new ArrayList<>();
    this.currentSubmissionBatchSize = configuration.getSubmissionBatchSize();
    init(
        processingIntervalInSecs == null
            ? DEFAULT_PROCESSING_INTERVAL_IN_SECS
            : processingIntervalInSecs);
  }

  private void init(Integer processingIntervalInSecs) {
    queueTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            try {
              onProcessQueue();
            } catch (Exception e) {
              LOG.error("Error in processing queue", e);
            }
          }
        },
        processingIntervalInSecs * 1000,
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

  @VisibleForTesting
  Boolean isProcessingCurrentlySuspended() {
    return shouldSuspendProcessing();
  }

  @Override
  public void enqueue(Event event) {
    if (shouldDiscard()) {
      LOG.info("Queue items are currently being discarded. This event will not be enqueued");
      return;
    }

    long timestamp = storageProvider.getQueue().save(event);
    LOG.info(
        String.format(
            "Enqueueing event: %s type: %s, refId: %s",
            timestamp, event.getType(), event.getReferenceId()));
  }

  private boolean shouldDiscard() {
    if (discardQueueItemsUntil == null) {
      return false;
    }

    return LocalDateTime.now().isBefore(discardQueueItemsUntil);
  }

  @Override
  public void process() {
    synchronized (this) {
      if (processingQueue) {
        LOG.trace("Currently processing queue; Returning...");
        return;
      }
      processingQueue = true;
    }

    try {
      List<StorageItem<Event>> storedEvents =
          storageProvider.getQueue().get(currentSubmissionBatchSize);
      if (storedEvents.isEmpty()) {
        LOG.trace("No events found to submit; Returning...");
        return;
      }

      List<Event> events =
          storedEvents.stream().map(StorageItem::getValue).collect(Collectors.toList());
      LOG.info(
          String.format("Sending %s events to %s", events.size(), configuration.getServerUrl()));
      SubmissionResponse response = submissionClient.postEvents(events);
      processSubmissionResponse(response, storedEvents);
      eventPosted(response, events);
    } catch (SubmissionClientException e) {
      LOG.error("Error submitting events from queue", e);
      suspendProcessing();
    } finally {
      synchronized (this) {
        processingQueue = false;
      }
    }
  }

  private void processSubmissionResponse(
      SubmissionResponse response, List<StorageItem<Event>> storedEvents) {
    if (response.isSuccess()) {
      LOG.info(String.format("Sent %s events", storedEvents.size()));
      setBatchSizeToConfigured();
      removeEvents(storedEvents);
      return;
    }

    if (response.isServiceUnavailable()) {
      LOG.error("Service returns service unavailable");
      suspendProcessing();
      return;
    }

    if (response.isPaymentRequired()) {
      LOG.info("Too many events have been submitted, please upgrade your plan");
      suspendProcessingForNoPayment();
      return;
    }

    if (response.unableToAuthenticate()) {
      LOG.info(
          "Unable to authenticate, please check your configuration. Events will not be submitted");
      suspendProcessing(Duration.ofMinutes(15));
      removeEvents(storedEvents);
      return;
    }

    if (response.isNotFound() || response.isBadRequest()) {
      LOG.error(
          String.format(
              "Error while trying to submit data, Code:%s, Body:%s",
              response.getCode(), response.getBody()));
      suspendProcessing(Duration.ofMinutes(4));
      removeEvents(storedEvents);
      return;
    }

    if (response.isRequestEntityTooLarge()) {
      if (currentSubmissionBatchSize > 1) {
        LOG.error(
            "Event submission discarded for being too large. Retrying with smaller batch size");
        currentSubmissionBatchSize =
            Math.max(1, (int) Math.round(currentSubmissionBatchSize / BATCH_SIZE_DIVISOR));
      } else {
        LOG.error("Event submission discarded for being too large. Events will not be submitted");
        removeEvents(storedEvents);
      }
      return;
    }

    LOG.error(
        String.format(
            "Error submitting events, Code: %s, Body: %s", response.getCode(), response.getBody()));
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
        LOG.error("Error while processing an event submission handler", e);
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
      duration = Duration.ofMinutes(DEFAULT_SUSPENSION_DURATION_IN_MINS);
    }

    LOG.info(String.format("Suspending processing for %s", duration));
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
