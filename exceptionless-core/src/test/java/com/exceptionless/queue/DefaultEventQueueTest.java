package com.exceptionless.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.exceptionless.ExceptionlessConfiguration;
import com.exceptionless.TestClients;
import com.exceptionless.TestClients.CapturingSubmissionClient;
import com.exceptionless.models.Event;
import com.exceptionless.storage.InMemoryQueueStorage;
import com.exceptionless.submission.SubmissionResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultEventQueueTest {
  /** A clock tests can move. */
  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-05-01T10:07:00Z");

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }
  }

  private final ExceptionlessConfiguration config = TestClients.config().submissionBatchSize(10);
  private final CapturingSubmissionClient submission = new CapturingSubmissionClient();
  private final InMemoryQueueStorage storage = new InMemoryQueueStorage(1000);
  private final MutableClock clock = new MutableClock();
  private final DefaultEventQueue queue = new DefaultEventQueue(config, submission, storage, clock);

  private void enqueue(int count) {
    for (int i = 0; i < count; i++) {
      queue.enqueue(log("message " + i));
    }
  }

  private static Event log(String message) {
    Event event = new Event();
    event.setType("log");
    event.setMessage(message);
    return event;
  }

  private void respond(int status) {
    submission.eventsResponse = json -> new SubmissionResponse(status, null, null);
  }

  @Test
  void sendsAllEventsInBatches() {
    enqueue(25);
    queue.process();
    assertThat(submission.posts).hasSize(3);
    assertThat(submission.events()).hasSize(25);
    assertThat(queue.size()).isZero();
  }

  @Test
  void batchesAreJsonArraysOfEvents() {
    enqueue(2);
    queue.process();
    assertThat(submission.posts.get(0)).startsWith("[{\"type\":\"log\"").endsWith("}]");
  }

  @Test
  void rateLimitKeepsEventsAndSuspendsUntilNext15MinuteWindow() {
    enqueue(3);
    respond(429);
    queue.process();
    assertThat(queue.size()).isEqualTo(3);
    assertThat(queue.isSuspended()).isTrue();
    clock.advance(Duration.ofMinutes(7)); // 10:14
    assertThat(queue.isSuspended()).isTrue();
    clock.advance(Duration.ofMinutes(1)); // 10:15
    assertThat(queue.isSuspended()).isFalse();
    respond(202);
    queue.process();
    assertThat(queue.size()).isZero();
  }

  @Test
  void serviceUnavailableKeepsEvents() {
    enqueue(3);
    respond(503);
    queue.process();
    assertThat(queue.size()).isEqualTo(3);
    clock.advance(Duration.ofMinutes(4));
    assertThat(queue.isSuspended()).isTrue();
    clock.advance(Duration.ofMinutes(1));
    assertThat(queue.isSuspended()).isFalse();
  }

  @Test
  void paymentRequiredClearsQueueAndDiscardsNewEventsForAnHour() {
    enqueue(3);
    respond(402);
    queue.process();
    assertThat(queue.size()).isZero();
    enqueue(2);
    assertThat(queue.size()).isZero();
    clock.advance(Duration.ofHours(1));
    enqueue(2);
    assertThat(queue.size()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void unauthorizedDropsBatchAndSuspends15Minutes(int status) {
    enqueue(15);
    respond(status);
    queue.process();
    assertThat(queue.size()).isEqualTo(5);
    assertThat(submission.posts).hasSize(1);
    clock.advance(Duration.ofMinutes(15));
    assertThat(queue.isSuspended()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 404})
  void badRequestDropsBatchAndSuspends4Hours(int status) {
    enqueue(3);
    respond(status);
    queue.process();
    assertThat(queue.size()).isZero();
    clock.advance(Duration.ofHours(3));
    assertThat(queue.isSuspended()).isTrue();
    clock.advance(Duration.ofHours(1));
    assertThat(queue.isSuspended()).isFalse();
  }

  @Test
  void payloadTooLargeShrinksBatchesUntilTheySucceed() {
    enqueue(10);
    submission.eventsResponse =
        json -> {
          int count = json.split("\"type\"").length - 1;
          return new SubmissionResponse(count > 3 ? 413 : 202, null, null);
        };
    queue.process();
    assertThat(queue.size()).isZero();
    assertThat(submission.events().size()).isGreaterThan(10); // retried batches were re-sent
  }

  @Test
  void smallerBatchSizeLastsForTheRestOfTheRun() {
    enqueue(20);
    List<Integer> statuses = new ArrayList<>();
    submission.eventsResponse =
        json -> {
          int count = json.split("\"type\"").length - 1;
          int status = count > 3 ? 413 : 202;
          statuses.add(status);
          return new SubmissionResponse(status, null, null);
        };
    queue.process();
    assertThat(queue.size()).isZero();
    // 10 -> 413, 5 -> 413, then batches of 2 succeed without further rejections.
    assertThat(statuses.stream().filter(s -> s == 413).count()).isEqualTo(2);
  }

  @Test
  void payloadTooLargeDropsSingleOversizedEvent() {
    enqueue(1);
    respond(413);
    queue.process();
    assertThat(queue.size()).isZero();
    assertThat(queue.isSuspended()).isFalse();
  }

  @Test
  void networkErrorsRetryWithBackoffAndEventuallyDrop() {
    config.queueMaxAttempts(3);
    enqueue(1);
    submission.eventsResponse =
        json -> SubmissionResponse.failed(new java.io.IOException("connection refused"));
    List<Duration> suspensions = new ArrayList<>();
    for (int attempt = 0; attempt < 3; attempt++) {
      queue.process();
      Duration waited = Duration.ZERO;
      while (queue.isSuspended()) {
        clock.advance(Duration.ofSeconds(1));
        waited = waited.plusSeconds(1);
      }
      suspensions.add(waited);
    }
    assertThat(suspensions)
        .containsExactly(Duration.ofSeconds(15), Duration.ofSeconds(30), Duration.ofSeconds(60));
    assertThat(submission.posts).hasSize(3);
    queue.process(); // third failure reached the attempt limit
    assertThat(queue.size()).isZero();
    assertThat(submission.posts).hasSize(3);
  }

  @Test
  void serverErrorsAreRetried() {
    enqueue(1);
    respond(500);
    queue.process();
    assertThat(queue.size()).isEqualTo(1);
    assertThat(storage.peek(1).get(0).attempts()).isEqualTo(1);
  }

  @Test
  void dropsEventsOlderThanMaxAge() {
    config.queueMaxAge(Duration.ofHours(1));
    enqueue(2);
    DefaultEventQueue later =
        new DefaultEventQueue(
            config, submission, storage, Clock.offset(Clock.systemUTC(), Duration.ofHours(2)));
    later.process();
    assertThat(submission.posts).isEmpty();
    assertThat(storage.size()).isZero();
  }

  @Test
  void doesNotSendWhileSuspendedOrDisabled() {
    enqueue(1);
    queue.suspendProcessing(Duration.ofMinutes(1));
    queue.process();
    assertThat(submission.posts).isEmpty();
    queue.resume();
    config.enabled(false);
    queue.process();
    assertThat(submission.posts).isEmpty();
    config.enabled(true);
    queue.process();
    assertThat(submission.posts).hasSize(1);
  }

  @Test
  void notifiesHandlersWithCountAndResponse() {
    List<String> seen = new ArrayList<>();
    queue.onEventsPosted((count, response) -> seen.add(count + ":" + response.statusCode()));
    queue.onEventsPosted(
        (count, response) -> {
          throw new IllegalStateException("handler failures are isolated");
        });
    enqueue(2);
    queue.process();
    assertThat(seen).containsExactly("2:202");
  }

  @Test
  void submissionClientExceptionsAreTreatedAsFailures() {
    enqueue(1);
    submission.eventsResponse =
        json -> {
          throw new IllegalStateException("boom");
        };
    queue.process();
    assertThat(queue.size()).isEqualTo(1);
    assertThat(queue.isSuspended()).isTrue();
  }

  @Test
  void limitsBatchBytes() {
    String big = "x".repeat(300_000);
    for (int i = 0; i < 5; i++) {
      queue.enqueue(log(big));
    }
    queue.process();
    assertThat(submission.posts.size()).isGreaterThanOrEqualTo(2);
    for (String post : submission.posts) {
      assertThat(post.length()).isLessThanOrEqualTo(DefaultEventQueue.MAX_BATCH_BYTES + 400_000);
    }
    assertThat(queue.size()).isZero();
  }
}
