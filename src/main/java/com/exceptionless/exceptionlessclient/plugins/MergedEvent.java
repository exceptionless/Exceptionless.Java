package com.exceptionless.exceptionlessclient.plugins;

import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.queue.EventQueueIF;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MergedEvent {
  private Long hash;
  private Event event;
  private EventQueueIF eventQueue;

  @Builder
  public MergedEvent(Long hash, Event event, EventQueueIF eventQueue) {
    this.hash = hash;
    this.event = event;
    this.eventQueue = eventQueue;
  }

  public void incrementCount(long count) {
    event.setCount(event.getCount() + count);
  }

  public void updateDate(LocalDate date) {
    if (date.isAfter(event.getDate())) {
      event.setDate(date);
    }
  }

  public void resubmit() {
    eventQueue.enqueue(event);
  }
}
