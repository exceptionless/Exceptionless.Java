package com.exceptionless.e2e.support;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Polls until the server has processed events (it does so asynchronously through queues). */
public final class Await {
  /** Default wait for event processing. Usually takes 1-3 seconds against the all-in-one image. */
  public static final Duration EVENTS = Duration.ofSeconds(90);

  private Await() {}

  public static void until(String description, Duration timeout, BooleanSupplier condition) {
    value(description, timeout, () -> condition.getAsBoolean(), Boolean::booleanValue);
  }

  /** Returns the first value matching {@code accept}, failing after {@code timeout}. */
  public static <T> T value(
      String description, Duration timeout, Supplier<T> supplier, Predicate<T> accept) {
    long deadline = System.nanoTime() + timeout.toNanos();
    T last = null;
    RuntimeException lastError = null;
    long sleep = 250;
    while (true) {
      try {
        last = supplier.get();
        lastError = null;
        if (last != null && accept.test(last)) {
          return last;
        }
      } catch (RuntimeException | AssertionError e) {
        lastError = e instanceof RuntimeException re ? re : new RuntimeException(e);
      }
      if (System.nanoTime() > deadline) {
        AssertionError error =
            new AssertionError(
                "Timed out after "
                    + timeout.toSeconds()
                    + "s waiting for "
                    + description
                    + "; last value: "
                    + last);
        if (lastError != null) {
          error.initCause(lastError);
        }
        throw error;
      }
      try {
        Thread.sleep(sleep);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      sleep = Math.min(2000, sleep * 2);
    }
  }
}
