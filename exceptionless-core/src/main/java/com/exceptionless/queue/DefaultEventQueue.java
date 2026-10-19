package com.exceptionless.queue;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.internal.Log;
import com.exceptionless.json.JsonWriter;
import com.exceptionless.models.Event;
import com.exceptionless.storage.QueueEntry;
import com.exceptionless.storage.QueueStorage;
import com.exceptionless.submission.SubmissionClient;
import com.exceptionless.submission.SubmissionResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The default queue. Events are serialized when queued, so a bad event can never block the queue,
 * and sent in batches by the client's background thread.
 *
 * <p>Server responses are handled like the other Exceptionless clients:
 *
 * <ul>
 *   <li>429 (rate limited) and 503: keep the events and pause until the server should accept them
 *       again
 *   <li>402 (plan limit reached): drop queued events and new events for an hour
 *   <li>401/403 (bad API key): drop the batch and pause for 15 minutes
 *   <li>400/404: drop the batch and pause for 4 hours
 *   <li>413 (too large): retry with smaller batches; drop single events that are still too large
 *   <li>anything else (network errors, 5xx): keep the events, retry with backoff, and drop an event
 *       after {@link ExceptionlessConfiguration#getQueueMaxAttempts() max attempts}
 * </ul>
 */
public final class DefaultEventQueue implements EventQueue {
  /**
   * Uncompressed size limit for one batch. The server rejects request bodies over 200 KB
   * (compressed) with a 413; JSON usually compresses 5-10x, and batches that still don't fit are
   * split on the 413.
   */
  static final int MAX_BATCH_BYTES = 1_000_000;

  private static final int MAX_BATCHES_PER_RUN = 100;
  private static final Log LOG = Log.get(DefaultEventQueue.class);

  private final ExceptionlessConfiguration config;
  private final SubmissionClient submissionClient;
  private final QueueStorage storage;
  private final Clock clock;
  private final ReentrantLock processLock = new ReentrantLock();
  private final List<EventsPostedListener> listeners = new CopyOnWriteArrayList<>();

  private volatile Instant suspendedUntil = Instant.MIN;
  private volatile Instant discardUntil = Instant.MIN;
  private volatile int currentBatchSize;
  private volatile int consecutiveFailures;

  public DefaultEventQueue(
      ExceptionlessConfiguration config, SubmissionClient submissionClient, QueueStorage storage) {
    this(config, submissionClient, storage, Clock.systemUTC());
  }

  DefaultEventQueue(
      ExceptionlessConfiguration config,
      SubmissionClient submissionClient,
      QueueStorage storage,
      Clock clock) {
    this.config = config;
    this.submissionClient = submissionClient;
    this.storage = storage;
    this.clock = clock;
    this.currentBatchSize = config.getSubmissionBatchSize();
  }

  @Override
  public void enqueue(Event event) {
    if (clock.instant().isBefore(discardUntil)) {
      LOG.debug("Discarding event: the plan limit was reached");
      return;
    }
    String json;
    try {
      json = JsonWriter.toJson(event);
    } catch (RuntimeException | StackOverflowError e) {
      LOG.error("Discarding event that could not be serialized: " + event, e);
      return;
    }
    storage.enqueue(json);
  }

  @Override
  public void process() {
    if (!processLock.tryLock()) {
      return; // already processing
    }
    try {
      for (int run = 0; run < MAX_BATCHES_PER_RUN; run++) {
        if (isSuspended() || !config.isEnabled()) {
          return;
        }
        if (run == 0) {
          // A smaller batch size after a 413 lasts for the rest of the run, like the .NET client.
          currentBatchSize = config.getSubmissionBatchSize();
        }
        List<QueueEntry> batch = nextBatch();
        if (batch.isEmpty()) {
          return;
        }
        if (!send(batch)) {
          return;
        }
      }
    } catch (RuntimeException e) {
      LOG.error("Error processing the event queue", e);
      suspendProcessing(Duration.ofMinutes(1));
    } finally {
      processLock.unlock();
    }
  }

  private List<QueueEntry> nextBatch() {
    int limit = Math.max(1, Math.min(currentBatchSize, config.getSubmissionBatchSize()));
    while (true) {
      List<QueueEntry> entries = storage.peek(limit);
      List<QueueEntry> expired = new ArrayList<>();
      List<QueueEntry> batch = new ArrayList<>();
      Instant oldest = clock.instant().minus(config.getQueueMaxAge());
      int bytes = 2;
      for (QueueEntry entry : entries) {
        if (entry.enqueuedAt().isBefore(oldest)
            || entry.attempts() >= config.getQueueMaxAttempts()) {
          expired.add(entry);
          continue;
        }
        int size = entry.json().getBytes(StandardCharsets.UTF_8).length + 1;
        if (!batch.isEmpty() && bytes + size > MAX_BATCH_BYTES) {
          break;
        }
        batch.add(entry);
        bytes += size;
      }
      if (!expired.isEmpty()) {
        LOG.info(
            "Discarding "
                + expired.size()
                + " queued events that are too old or failed too many times");
        storage.remove(expired);
      }
      if (!batch.isEmpty() || expired.isEmpty()) {
        return batch;
      }
    }
  }

  /** Sends one batch. Returns true to keep processing. */
  private boolean send(List<QueueEntry> batch) {
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < batch.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append(batch.get(i).json());
    }
    json.append(']');

    SubmissionResponse response;
    try {
      response = submissionClient.postEvents(json.toString());
    } catch (RuntimeException e) {
      response = SubmissionResponse.failed(e);
    }
    boolean keepGoing = handleResponse(batch, response);
    for (EventsPostedListener listener : listeners) {
      try {
        listener.eventsPosted(batch.size(), response);
      } catch (RuntimeException e) {
        LOG.warn("EventsPosted listener failed", e);
      }
    }
    return keepGoing;
  }

  private boolean handleResponse(List<QueueEntry> batch, SubmissionResponse response) {
    int status = response.statusCode();
    if (response.isSuccess()) {
      LOG.debug(() -> "Sent " + batch.size() + " events");
      storage.remove(batch);
      consecutiveFailures = 0;
      return true;
    }
    if (response.isTooManyRequests()) {
      // The server throttles per organization in 15 minute windows and sends no Retry-After.
      Instant now = clock.instant();
      Instant window = now.truncatedTo(ChronoUnit.HOURS);
      while (!window.isAfter(now)) {
        window = window.plus(15, ChronoUnit.MINUTES);
      }
      LOG.warn("Rate limit reached; pausing event submission until " + window);
      suspendProcessing(Duration.between(now, window));
      return false;
    }
    if (response.isServiceUnavailable()) {
      LOG.warn("Server unavailable (503); pausing event submission for 5 minutes");
      suspendProcessing(Duration.ofMinutes(5));
      return false;
    }
    if (response.isPaymentRequired()) {
      LOG.warn("Event limit reached for this billing period; discarding events for an hour");
      suspendProcessing(Duration.ofHours(1), true, true);
      return false;
    }
    if (response.isUnauthorized() || response.isForbidden()) {
      LOG.error(
          "Unable to send events: the API key is invalid or not authorized (" + status + ")", null);
      storage.remove(batch);
      suspendProcessing(Duration.ofMinutes(15));
      return false;
    }
    if (response.isBadRequest() || response.isNotFound()) {
      LOG.error("Unable to send events (HTTP " + status + "): " + response.message(), null);
      storage.remove(batch);
      suspendProcessing(Duration.ofHours(4));
      return false;
    }
    if (response.isRequestEntityTooLarge()) {
      if (batch.size() > 1) {
        // Shrink from what was actually sent, which may be fewer events than the batch size
        // when the byte limit applied.
        currentBatchSize = Math.max(1, batch.size() / 2);
        LOG.info("Batch too large; retrying with " + currentBatchSize + " events per batch");
        return true;
      }
      LOG.error("Discarding an event that is too large to send", null);
      storage.remove(batch);
      return true;
    }

    // Network errors, timeouts and 5xx: retry with backoff.
    storage.incrementAttempts(batch);
    int failures = ++consecutiveFailures;
    long seconds = Math.min(300, 15L << Math.min(failures - 1, 5));
    LOG.warn(
        "Unable to send events ("
            + (status < 0 ? response.message() : "HTTP " + status)
            + "); retrying in "
            + seconds
            + " seconds");
    suspendProcessing(Duration.ofSeconds(seconds));
    return false;
  }

  @Override
  public void suspendProcessing(
      Duration duration, boolean discardFutureQueuedItems, boolean clearQueue) {
    Instant until = clock.instant().plus(duration);
    suspendedUntil = until;
    if (discardFutureQueuedItems) {
      discardUntil = until;
    }
    if (clearQueue) {
      storage.clear();
    }
  }

  @Override
  public void resume() {
    suspendedUntil = Instant.MIN;
    discardUntil = Instant.MIN;
  }

  @Override
  public boolean isSuspended() {
    return clock.instant().isBefore(suspendedUntil);
  }

  @Override
  public int size() {
    return storage.size();
  }

  @Override
  public void onEventsPosted(EventsPostedListener listener) {
    listeners.add(listener);
  }
}
