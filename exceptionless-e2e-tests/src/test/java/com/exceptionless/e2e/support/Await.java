package com.exceptionless.e2e.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.awaitility.pollinterval.IterativePollInterval;

/**
 * Waits until the server has processed events (it does so asynchronously through queues), polling
 * with Awaitility from 250 ms, doubling up to 2 s between attempts.
 */
public final class Await {
  /** Default wait for event processing. Usually takes 1-3 seconds against the all-in-one image. */
  public static final Duration EVENTS = Duration.ofSeconds(90);

  private static final Duration FIRST_POLL = Duration.ofMillis(250);
  private static final Duration MAX_POLL = Duration.ofSeconds(2);

  private Await() {}

  public static void until(String description, Duration timeout, BooleanSupplier condition) {
    value(description, timeout, condition::getAsBoolean, Boolean::booleanValue);
  }

  /**
   * Returns the first non-null value matching {@code accept}, failing after {@code timeout}.
   * Exceptions thrown while polling (e.g. the server isn't ready yet) count as no match.
   */
  public static <T> T value(
      String description, Duration timeout, Supplier<T> supplier, Predicate<T> accept) {
    return await(description)
        .atMost(timeout)
        .pollDelay(Duration.ZERO)
        .pollInterval(
            new IterativePollInterval(
                previous -> {
                  Duration doubled = previous.multipliedBy(2);
                  return doubled.compareTo(MAX_POLL) > 0 ? MAX_POLL : doubled;
                },
                FIRST_POLL))
        .ignoreExceptions()
        .until(supplier::get, value -> value != null && accept.test(value));
  }
}
