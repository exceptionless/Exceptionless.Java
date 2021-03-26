package com.prashantchaubey.exceptionlessclient.plugins.preconfigured;

import com.prashantchaubey.exceptionlessclient.configuration.ConfigurationManager;
import com.prashantchaubey.exceptionlessclient.logging.LogIF;
import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.models.EventPluginContext;
import com.prashantchaubey.exceptionlessclient.models.enums.EventPropertyKey;
import com.prashantchaubey.exceptionlessclient.models.plugins.MergedEvent;
import com.prashantchaubey.exceptionlessclient.models.services.error.InnerError;
import com.prashantchaubey.exceptionlessclient.plugins.EventPluginIF;
import lombok.Builder;
import lombok.Getter;

import java.util.*;

@Builder
@Getter
public class DuplicateCheckerPlugin implements EventPluginIF {
  private LogIF log;
  @Builder.Default private int mergedEventsResubmissionTimerInSecs = 30;
  @Builder.Default private int maxHashesCount = 50;

  // lombok ignored fields;
  @Builder.Default private Queue<MergedEvent> mergedEvents = new LinkedList<>();
  @Builder.Default private Timer mergedEventsResubmissionTimer = new Timer();
  @Builder.Default private List<TimeStampedHash> hashes = new ArrayList<>();

  {
    mergedEventsResubmissionTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            MergedEvent event = mergedEvents.poll();
            if (event != null) {
              event.resubmit();
              ;
            }
          }
        },
        mergedEventsResubmissionTimerInSecs * 1000);
  }

  @Override
  public int getPriority() {
    return 1010;
  }

  @Override
  public void run(
      EventPluginContext eventPluginContext, ConfigurationManager configurationManager) {
    Event event = eventPluginContext.getEvent();
    InnerError error = (InnerError) event.getData().get(EventPropertyKey.ERROR.value());
    long hash = getHashCode(error);

    Optional<MergedEvent> maybeMergedEvent =
        mergedEvents.stream().filter(mergedEvent -> mergedEvent.getHash() == hash).findFirst();
    if (maybeMergedEvent.isPresent()) {
      MergedEvent mergedEvent = maybeMergedEvent.get();
      mergedEvent.incrementCount(event.getCount());
      mergedEvent.updateDate(event.getDate());
      log.info(String.format("Ignoring duplicate event with hash: %s", hash));
      eventPluginContext.getContext().markAsCancelled();
      return;
    }
    long now = System.currentTimeMillis();
    if (hashes.stream()
        .anyMatch(
            timeStampedHash ->
                timeStampedHash.getHash() == hash && timeStampedHash.getTimestamp() == now)) {
      log.trace(String.format("Adding event with hash :%s", hash));
      mergedEvents.add(
          MergedEvent.builder()
              .count(event.getCount())
              .event(event)
              .eventQueue(configurationManager.getQueue())
              .build());
      eventPluginContext.getContext().markAsCancelled();
      return;
    }

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
