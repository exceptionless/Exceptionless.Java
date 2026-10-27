package com.exceptionless.storage;

import java.time.Instant;

/**
 * A serialized event waiting to be sent.
 *
 * @param id storage specific identifier
 * @param json the serialized event
 * @param attempts how many times sending it has failed
 * @param enqueuedAt when it was queued
 */
public record QueueEntry(String id, String json, int attempts, Instant enqueuedAt) {
  /** {@return a copy of this entry with {@code attempts} replaced} */
  public QueueEntry withAttempts(int attempts) {
    return new QueueEntry(id, json, attempts, enqueuedAt);
  }
}
