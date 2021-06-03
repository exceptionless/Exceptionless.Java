package com.exceptionless.exceptionlessclient.plugins.preconfigured;

import com.exceptionless.exceptionlessclient.configuration.ConfigurationManager;
import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.models.EventPluginContext;
import com.exceptionless.exceptionlessclient.models.error.Error;
import com.exceptionless.exceptionlessclient.models.error.InnerError;
import com.exceptionless.exceptionlessclient.plugins.EventPluginIF;
import com.exceptionless.exceptionlessclient.plugins.MergedEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class DuplicateErrorCheckerPlugin implements EventPluginIF {
  private static final String MERGED_EVENTS_RESUBMISSION_TIMER_NAME =
      "merged-events-resubmission-timer";
  private static final Integer DEFAULT_PRIORITY = 1010;
  private static final Integer DEFAULT_MAX_HASHES_COUNT = 50;
  private static final Integer DEFAULT_MERGED_EVENTS_RESUBMISSION_IN_SECS = 30;

  private final int maxHashesCount;
  private final Queue<MergedEvent> mergedEvents;
  private final Timer mergedEventsResubmissionTimer;
  private final List<TimeStampedHash> hashes;
  private final Integer mergedEventsResubmissionInSecs;

  @Builder
  public DuplicateErrorCheckerPlugin(
      Integer mergedEventsResubmissionInSecs, Integer maxHashesCount) {
    this.maxHashesCount = maxHashesCount == null ? DEFAULT_MAX_HASHES_COUNT : maxHashesCount;
    this.mergedEvents = new ArrayDeque<>();
    this.mergedEventsResubmissionTimer = new Timer(MERGED_EVENTS_RESUBMISSION_TIMER_NAME);
    this.hashes = new ArrayList<>();
    this.mergedEventsResubmissionInSecs =
        mergedEventsResubmissionInSecs == null
            ? DEFAULT_MERGED_EVENTS_RESUBMISSION_IN_SECS
            : mergedEventsResubmissionInSecs;
    init();
  }

  private void init() {
    mergedEventsResubmissionTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            try {
              MergedEvent event = mergedEvents.poll();
              if (event != null) {
                event.resubmit();
              }
            } catch (Exception e) {
              log.error("Error in resubmitting merged events", e);
            }
          }
        },
        mergedEventsResubmissionInSecs * 1000,
        mergedEventsResubmissionInSecs * 1000);
  }

  @Override
  public int getPriority() {
    return DEFAULT_PRIORITY;
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
              .event(event)
              .eventQueue(configurationManager.getQueue())
              .hash(hash)
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
