package com.exceptionless.plugins;

import com.exceptionless.ExceptionlessClient;
import com.exceptionless.models.ErrorInfo;
import com.exceptionless.models.Event;
import com.exceptionless.models.StackFrame;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.Nullable;

/**
 * Collapses repeated errors. When the same error (type, message and stack) happens again within the
 * window, it is counted instead of sent, and one event with the total count is sent at the end of
 * the window.
 */
public final class DuplicateCheckerPlugin implements EventPlugin {
  public static final int PRIORITY = 1010;
  static final int MAX_HASHES = 50;

  private final Duration window;
  private final InstantSource clock;
  private final Deque<Processed> processed = new ArrayDeque<>();
  private final Map<Integer, Merged> merged = new LinkedHashMap<>();
  private @Nullable ScheduledFuture<?> timer;
  private @Nullable ExceptionlessClient client;

  /** An error hash and when it was first seen in the current window. */
  private record Processed(int hash, Instant seenAt) {}

  public DuplicateCheckerPlugin() {
    this(Duration.ofSeconds(30), InstantSource.system());
  }

  DuplicateCheckerPlugin(Duration window, InstantSource clock) {
    this.window = window;
    this.clock = clock;
  }

  @Override
  public int getPriority() {
    return PRIORITY;
  }

  @Override
  public synchronized void startup(ExceptionlessClient client) {
    this.client = client;
    if (timer == null) {
      timer = client.scheduleAtFixedRate(this::flush, window, window);
    }
  }

  @Override
  public void run(EventPluginContext context) {
    Event event = context.getEvent();
    if (!event.isError() || event.getError() == null) {
      return;
    }
    int hash = hash(event);
    Instant now = clock.instant();
    Instant cutoff = now.minus(window);
    synchronized (this) {
      Merged existing = merged.get(hash);
      if (existing != null) {
        existing.count += countOf(event);
        context.cancel();
        return;
      }
      for (Iterator<Processed> it = processed.iterator(); it.hasNext(); ) {
        Processed entry = it.next();
        if (!entry.seenAt().isAfter(cutoff)) {
          it.remove();
        } else if (entry.hash() == hash) {
          merged.put(hash, new Merged(event, countOf(event)));
          context.cancel();
          return;
        }
      }
      processed.addLast(new Processed(hash, now));
      while (processed.size() > MAX_HASHES) {
        processed.removeFirst();
      }
    }
  }

  /** Stops the flush timer and sends the counted duplicates. */
  @Override
  public void suspend(ExceptionlessClient client) {
    synchronized (this) {
      if (timer != null) {
        timer.cancel(false);
        timer = null;
      }
    }
    flush();
  }

  /** Sends the counted duplicates. */
  public void flush() {
    List<Merged> toSend;
    ExceptionlessClient target;
    synchronized (this) {
      if (merged.isEmpty()) {
        return;
      }
      toSend = new ArrayList<>(merged.values());
      merged.clear();
      target = client;
    }
    if (target == null) {
      return;
    }
    for (Merged item : toSend) {
      item.event.setCount(item.count);
      target.getQueue().enqueue(item.event);
    }
  }

  private static int countOf(Event event) {
    return event.getCount() != null && event.getCount() > 0 ? event.getCount() : 1;
  }

  static int hash(Event event) {
    int hash = Objects.hash(event.getType(), event.getSource());
    ErrorInfo error = event.getError();
    while (error != null) {
      hash = 31 * hash + Objects.hashCode(error.getType());
      hash = 31 * hash + Objects.hashCode(error.getMessage());
      for (StackFrame frame : error.getStackTrace()) {
        hash = 31 * hash + frame.getFullName().hashCode();
        hash = 31 * hash + Objects.hashCode(frame.getLineNumber());
      }
      error = error.getInner();
    }
    return hash;
  }

  private static final class Merged {
    final Event event;
    int count;

    Merged(Event event, int count) {
      this.event = event;
      this.count = count;
    }
  }
}
