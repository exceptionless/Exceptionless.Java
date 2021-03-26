package com.prashantchaubey.exceptionlessclient.models.plugins;

import com.prashantchaubey.exceptionlessclient.models.Event;
import com.prashantchaubey.exceptionlessclient.queue.EventQueueIF;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Builder
@Getter
public class MergedEvent {
  private long hash;
  private Event event;
  private long count;
  private EventQueueIF eventQueue;

  public void incrementCount(long count) {
    this.count += count;
  }

  public void updateDate(LocalDate date) {
    if (date.isAfter(event.getDate())) {
      event.setDate(date);
    }
  }

  public void resubmit() {
    event.setCount(count);
    eventQueue.enqueue(event);
  }
}
