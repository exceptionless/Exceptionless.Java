package com.exceptionless.exceptionlessclient.plugins;

import com.exceptionless.exceptionlessclient.models.Event;
import com.exceptionless.exceptionlessclient.queue.DefaultEventQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class MergedEventTest {
  @Mock private DefaultEventQueue eventQueue;
  private MergedEvent mergedEvent;

  @BeforeEach
  public void setup() {
    mergedEvent =
        MergedEvent.builder()
            .event(Event.builder().date(LocalDate.now()).build())
            .eventQueue(eventQueue)
            .build();
  }

  @Test
  public void itCanIncrementCount() {
    mergedEvent.incrementCount(100);
    mergedEvent.resubmit();

    verify(eventQueue, times(1)).enqueue(argThat(event -> event.getCount().equals(101L)));
  }

  @Test
  public void itCanUpdateDate() {
    LocalDate tomorrow = LocalDate.now().plusDays(1);
    mergedEvent.updateDate(tomorrow);
    mergedEvent.resubmit();

    verify(eventQueue, times(1)).enqueue(argThat(event -> event.getDate().equals(tomorrow)));
  }

  @Test
  public void itShouldNotUpdateDateIfSameAsTheEvent() {
    mergedEvent.updateDate(LocalDate.now());
    mergedEvent.resubmit();

    verify(eventQueue, times(1)).enqueue(argThat(event -> event.getDate().equals(LocalDate.now())));
  }

  @Test
  public void itShouldNotUpdateDateIfBeforeAsTheEvent() {
    LocalDate yesterday = LocalDate.now().minusDays(1);
    mergedEvent.updateDate(yesterday);
    mergedEvent.resubmit();

    verify(eventQueue, times(1)).enqueue(argThat(event -> event.getDate().equals(LocalDate.now())));
  }
}
