package com.exceptionless.internal;

import java.util.List;
import java.util.function.Predicate;

/** Builds name predicates from data exclusion patterns. */
public final class Exclusions {
  private static final Predicate<String> NONE = name -> false;

  private Exclusions() {}

  public static Predicate<String> of(List<String> patterns) {
    if (patterns.isEmpty()) {
      return NONE;
    }
    List<String> copy = List.copyOf(patterns);
    return name -> Wildcard.matchesAny(name, copy);
  }
}
