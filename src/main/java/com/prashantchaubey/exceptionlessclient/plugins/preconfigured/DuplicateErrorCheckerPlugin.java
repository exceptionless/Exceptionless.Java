package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.plugins.MergedEvent;
import com.prashantchaubey.exceptionlessclient.models.services.error.Error;
import com.prashantchaubey.exceptionlessclient.models.services.error.InnerError;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;
import lombok.Getter;

import java.util.*;

public class DuplicateErrorCheckerPlugin implements EventPluginIF {
  private LogIF log;
  private int maxHashesCount;
  private Queue<MergedEvent> mergedEvents;
  private Timer mergedEventsResubmissionTimer;
  private List<TimeStampedHash> hashes;
  private Integer mergedEventsResubmissionInSecs;

  @Builder
  public DuplicateErrorCheckerPlugin(
      LogIF log, Integer mergedEventsResubmissionInSecs, Integer maxHashesCount) {
    this.log = log;
    this.maxHashesCount = maxHashesCount == null ? 50 : maxHashesCount;
    this.mergedEvents = new ArrayDeque<>();
    this.mergedEventsResubmissionTimer = new Timer();
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
    if (!maybeError.isPresent()) {
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
    private long hash;
    private long timestamp;
  }
}
