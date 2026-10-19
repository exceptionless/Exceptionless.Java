package com.exceptionless.storage;

import java.util.Collection;
import java.util.List;

/** Stores serialized events until they are sent. Implementations must be thread-safe. */
public interface QueueStorage {
  /** Adds an event. Implementations may drop the oldest entries to stay within a size limit. */
  void enqueue(String json);

  /** Returns up to {@code maxItems} of the oldest entries without removing them. */
  List<QueueEntry> peek(int maxItems);

  /** Removes entries (after they were sent or discarded). */
  void remove(Collection<QueueEntry> entries);

  /** Records a failed attempt for each entry. */
  void incrementAttempts(Collection<QueueEntry> entries);

  /** Removes all entries. */
  void clear();

  /** {@return the number of stored entries} */
  int size();
}
