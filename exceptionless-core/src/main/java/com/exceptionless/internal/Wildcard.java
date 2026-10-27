package com.exceptionless.internal;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Case-insensitive wildcard matching with the same semantics as the other Exceptionless clients:
 * {@code *} matches everything, {@code *x} ends with, {@code x*} starts with, {@code *x*} contains,
 * otherwise an exact match.
 *
 * <p>Use {@link #compile} when the same pattern is matched repeatedly; it normalizes the pattern
 * once instead of on every call.
 */
public final class Wildcard implements Predicate<@Nullable String> {
  private enum Kind {
    ANY,
    EXACT,
    PREFIX,
    SUFFIX,
    CONTAINS,
    NONE
  }

  private static final Wildcard NEVER = new Wildcard(Kind.NONE, "");

  private final Kind kind;
  private final String text;

  private Wildcard(Kind kind, String text) {
    this.kind = kind;
    this.text = text;
  }

  /** Compiles {@code pattern}. A null or blank pattern never matches. */
  public static Wildcard compile(@Nullable String pattern) {
    if (pattern == null) {
      return NEVER;
    }
    String p = pattern.strip().toLowerCase(Locale.ROOT);
    if (p.isEmpty()) {
      return NEVER;
    }
    if (p.equals("*")) {
      return new Wildcard(Kind.ANY, "");
    }
    boolean leading = p.charAt(0) == '*';
    boolean trailing = p.length() > 1 && p.charAt(p.length() - 1) == '*';
    if (leading && trailing) {
      return new Wildcard(Kind.CONTAINS, p.substring(1, p.length() - 1));
    }
    if (leading) {
      return new Wildcard(Kind.SUFFIX, p.substring(1));
    }
    if (trailing) {
      return new Wildcard(Kind.PREFIX, p.substring(0, p.length() - 1));
    }
    return new Wildcard(Kind.EXACT, p);
  }

  /** {@return whether {@code value} matches this pattern} A null value never matches. */
  @Override
  public boolean test(@Nullable String value) {
    if (value == null || kind == Kind.NONE) {
      return false;
    }
    String v = value.strip().toLowerCase(Locale.ROOT);
    return switch (kind) {
      case ANY -> true;
      case EXACT -> v.equals(text);
      case PREFIX -> v.startsWith(text);
      case SUFFIX -> v.endsWith(text);
      case CONTAINS -> v.contains(text);
      case NONE -> false;
    };
  }

  public static boolean matches(@Nullable String value, @Nullable String pattern) {
    return value != null && compile(pattern).test(value);
  }

  public static boolean matchesAny(@Nullable String value, @Nullable Collection<String> patterns) {
    if (value == null || patterns == null) {
      return false;
    }
    for (String pattern : patterns) {
      if (matches(value, pattern)) {
        return true;
      }
    }
    return false;
  }
}
