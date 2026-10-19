package com.exceptionless.storage;

import com.exceptionless.internal.Log;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Keeps queued events in memory. When full, the oldest events are dropped. */
public final class InMemoryQueueStorage implements QueueStorage {
  public static final int DEFAULT_MAX_ITEMS = 1000;
  private static final Log LOG = Log.get(InMemoryQueueStorage.class);

  private final int maxItems;
  private final Map<String, QueueEntry> entries = new LinkedHashMap<>();
  private final AtomicLong sequence = new AtomicLong();
  private long dropped;

  public InMemoryQueueStorage() {
    this(DEFAULT_MAX_ITEMS);
  }

  public InMemoryQueueStorage(int maxItems) {
    if (maxItems < 1) {
      throw new IllegalArgumentException("maxItems must be at least 1");
    }
    this.maxItems = maxItems;
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
    String id = Long.toString(sequence.incrementAndGet());
    entries.put(id, new QueueEntry(id, json, 0, Instant.now()));
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
    Set<String> ids = new HashSet<>();
    for (QueueEntry entry : toUpdate) {
      ids.add(entry.id());
    }
    for (Map.Entry<String, QueueEntry> item : entries.entrySet()) {
      if (ids.contains(item.getKey())) {
        QueueEntry e = item.getValue();
        item.setValue(new QueueEntry(e.id(), e.json(), e.attempts() + 1, e.enqueuedAt()));
      }
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
