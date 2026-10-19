package com.exceptionless.internal;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A compiled set of data exclusion patterns: tests whether a property name matches any of them.
 * Patterns are normalized once, so an instance can be cached and shared between threads.
 */
public final class Exclusions implements Predicate<String> {
  private static final Exclusions NONE = new Exclusions(List.of());

  private final List<Wildcard> patterns;

  private Exclusions(List<Wildcard> patterns) {
    this.patterns = patterns;
  }

  /** {@return exclusions matching nothing} */
  public static Exclusions none() {
    return NONE;
  }

  /** Compiles {@code patterns} (wildcards supported, e.g. {@code *password*}). */
  public static Exclusions of(List<String> patterns) {
    if (patterns.isEmpty()) {
      return NONE;
    }
    return new Exclusions(patterns.stream().map(Wildcard::compile).toList());
  }

  /** {@return exclusions matching names excluded by this or {@code other}} */
  public Exclusions plus(Exclusions other) {
    if (other.patterns.isEmpty()) {
      return this;
    }
    if (patterns.isEmpty()) {
      return other;
    }
    return new Exclusions(Stream.concat(patterns.stream(), other.patterns.stream()).toList());
  }

  /** {@return whether {@code name} matches any of the patterns} */
  @Override
  public boolean test(String name) {
    for (Wildcard pattern : patterns) {
      if (pattern.test(name)) {
        return true;
      }
    }
    return false;
  }
}
