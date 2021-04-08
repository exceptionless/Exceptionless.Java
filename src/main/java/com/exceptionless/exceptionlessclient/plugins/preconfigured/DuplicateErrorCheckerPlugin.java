package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.logging.LogIF;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.plugins.MergedEvent;
import com.exceptionless.exceptionlessclient.models.services.error.Error;
import com.exceptionless.exceptionlessclient.models.services.error.InnerError;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;
import lombok.Getter;

import java.util.*;

public class DuplicateErrorCheckerPlugin implements EventPluginIF {
  private static final String MERGED_EVENTS_RESUBMISSION_TIMER_NAME = "merged-events-resubmission-timer";

  private final LogIF log;
  private final int maxHashesCount;
  private final Queue<MergedEvent> mergedEvents;
  private final Timer mergedEventsResubmissionTimer;
  private final List<TimeStampedHash> hashes;
  private final Integer mergedEventsResubmissionInSecs;

  @Builder
  public DuplicateErrorCheckerPlugin(
      LogIF log, Integer mergedEventsResubmissionInSecs, Integer maxHashesCount) {
    this.log = log;
    this.maxHashesCount = maxHashesCount == null ? 50 : maxHashesCount;
    this.mergedEvents = new ArrayDeque<>();
    this.mergedEventsResubmissionTimer = new Timer(MERGED_EVENTS_RESUBMISSION_TIMER_NAME);
    this.hashes = new ArrayList<>();
    this.mergedEventsResubmissionInSecs =
        mergedEventsResubmissionInSecs == null ? 30 : mergedEventsResubmissionInSecs;
    init();
  }

  private void init() {
    mergedEventsResubmissionTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            MergedEvent event = mergedEvents.poll();
            if (event != null) {
              event.resubmit();
            }
          }
        },
        mergedEventsResubmissionInSecs * 1000);
  }

  @Override
  public int getPriority() {
    return 1010;
  }

  @Override
  public void run(
          EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    Optional<Error> maybeError = event.getError();
    if (maybeError.isEmpty()) {
      return;
    }
    Error error = maybeError.get();

    long hash = getHashCode(error);
    Optional<MergedEvent> maybeMergedEvent =
        mergedEvents.stream().filter(mergedEvent -> mergedEvent.getHash() == hash).findFirst();
    if (maybeMergedEvent.isPresent()) {
      MergedEvent mergedEvent = maybeMergedEvent.get();
      mergedEvent.incrementCount(event.getCount());
      mergedEvent.updateDate(event.getDate());
      log.info(String.format("Ignoring duplicate event with hash: %s", hash));
      eventPluginContext.getContext().setEventCancelled(true);
      return;
    }

    long now = System.currentTimeMillis();
    // All the merged events of one hash are supposed to be processed by the timer every
    // `mergedEventsResubmissionInSecs` seconds
    if (hashes.stream()
        .anyMatch(
            timeStampedHash ->
                timeStampedHash.getHash() == hash
                    && timeStampedHash.getTimestamp()
                        >= (now - mergedEventsResubmissionInSecs * 1000))) {
      log.trace(String.format("Adding event with hash :%s", hash));
      mergedEvents.add(
          MergedEvent.builder()
              .count(event.getCount())
              .event(event)
              .eventQueue(configurationManager.getQueue())
              .build());
      eventPluginContext.getContext().setEventCancelled(true);
      return;
    }

    addNewHashIfPossible(hash, now);
  }

  private void addNewHashIfPossible(long hash, long now) {
    if (hashes.size() == maxHashesCount) {
      return;
    }

    hashes.add(TimeStampedHash.builder().hash(hash).timestamp(now).build());
  }

  private long getHashCode(InnerError error) {
    long hash = 0L;
    while (error != null) {
      hash += Objects.hash(error.getMessage(), error.getStackTrace());
      error = error.getInner();
    }
    return hash;
  }

  @Builder
  @Getter
  private static class TimeStampedHash {
    private final long hash;
    private final long timestamp;
  }
}
