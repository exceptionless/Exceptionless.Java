package com.exceptionless;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A clock tests can move. Usable wherever production code takes a {@link Clock} or {@link
 * java.time.InstantSource}. Thread-safe.
 */
public final class TestClock extends Clock {
  /** The default start time: a fixed instant, so tests don't depend on when they run. */
  public static final Instant START = Instant.parse("2026-05-01T10:07:00Z");

  private final AtomicReference<Instant> now;
  private final ZoneId zone;

  public TestClock() {
    this(START);
  }

  public TestClock(Instant start) {
    this(new AtomicReference<>(start), ZoneOffset.UTC);
  }

  private TestClock(AtomicReference<Instant> now, ZoneId zone) {
    this.now = now;
    this.zone = zone;
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  /** A view of this clock in another zone; moving either moves both. */
  @Override
  public TestClock withZone(ZoneId zone) {
    return new TestClock(now, zone);
  }

  @Override
  public Instant instant() {
    return now.get();
  }

  /** Moves the clock forward by {@code duration}. */
  public TestClock advance(Duration duration) {
    now.updateAndGet(current -> current.plus(duration));
    return this;
  }

  /** Sets the current time. */
  public TestClock set(Instant instant) {
    now.set(instant);
    return this;
  }
}
