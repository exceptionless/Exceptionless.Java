package com.exceptionless.internal;

import java.util.Collection;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Case-insensitive wildcard matching with the same semantics as the other Exceptionless clients:
 * {@code *} matches everything, {@code *x} ends with, {@code x*} starts with, {@code *x*} contains,
 * otherwise an exact match.
 */
public final class Wildcard {
  private Wildcard() {}

  public static boolean matches(@Nullable String value, @Nullable String pattern) {
    if (value == null || pattern == null) {
      return false;
    }
    String p = pattern.trim().toLowerCase(Locale.ROOT);
    if (p.isEmpty()) {
      return false;
    }
    String v = value.trim().toLowerCase(Locale.ROOT);
    if (p.equals("*")) {
      return true;
    }
    boolean startsWithWildcard = p.charAt(0) == '*';
    boolean endsWithWildcard = p.length() > 1 && p.charAt(p.length() - 1) == '*';
    if (startsWithWildcard && endsWithWildcard) {
      return v.contains(p.substring(1, p.length() - 1));
    }
    if (startsWithWildcard) {
      return v.endsWith(p.substring(1));
    }
    if (endsWithWildcard) {
      return v.startsWith(p.substring(0, p.length() - 1));
    }
    return v.equals(p);
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
