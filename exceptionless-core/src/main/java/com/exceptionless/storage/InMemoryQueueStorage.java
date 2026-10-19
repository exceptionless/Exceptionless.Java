package com.exceptionless.storage;

import com.exceptionless.internal.Log;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps queued events in memory. When full, the oldest events are dropped. */
public final class InMemoryQueueStorage implements QueueStorage {
  public static final int DEFAULT_MAX_ITEMS = 1000;
  private static final Log LOG = Log.get(InMemoryQueueStorage.class);

  private final int maxItems;
  private final InstantSource clock;
  // Guarded by this. Insertion order is queue order.
  private final Map<String, QueueEntry> entries = new LinkedHashMap<>();
  private long sequence;
  private long dropped;

  public InMemoryQueueStorage() {
    this(DEFAULT_MAX_ITEMS);
  }

  public InMemoryQueueStorage(int maxItems) {
    this(maxItems, InstantSource.system());
  }

  /**
   * Creates a storage that drops the oldest events beyond {@code maxItems}.
   *
   * @param clock records when each event was queued
   */
  public InMemoryQueueStorage(int maxItems, InstantSource clock) {
    if (maxItems < 1) {
      throw new IllegalArgumentException("maxItems must be at least 1");
    }
    this.maxItems = maxItems;
    this.clock = clock;
  }

  @Override
  public synchronized void enqueue(String json) {
    while (entries.size() >= maxItems) {
      Iterator<String> oldest = entries.keySet().iterator();
      oldest.next();
      oldest.remove();
      if (dropped++ % 100 == 0) {
        LOG.warn("Event queue is full (" + maxItems + " items); dropping the oldest events");
      }
    }
    String id = Long.toString(++sequence);
    entries.put(id, new QueueEntry(id, json, 0, clock.instant()));
  }

  @Override
  public synchronized List<QueueEntry> peek(int maxItems) {
    List<QueueEntry> result = new ArrayList<>(Math.min(maxItems, entries.size()));
    for (QueueEntry entry : entries.values()) {
      if (result.size() >= maxItems) {
        break;
      }
      result.add(entry);
    }
    return result;
  }

  @Override
  public synchronized void remove(Collection<QueueEntry> toRemove) {
    for (QueueEntry entry : toRemove) {
      entries.remove(entry.id());
    }
  }

  @Override
  public synchronized void incrementAttempts(Collection<QueueEntry> toUpdate) {
    for (QueueEntry entry : toUpdate) {
      entries.computeIfPresent(
          entry.id(), (id, current) -> current.withAttempts(current.attempts() + 1));
    }
  }

  @Override
  public synchronized void clear() {
    entries.clear();
  }

  @Override
  public synchronized int size() {
    return entries.size();
  }
}
