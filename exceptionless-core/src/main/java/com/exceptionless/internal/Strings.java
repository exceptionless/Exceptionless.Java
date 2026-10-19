package com.exceptionless.internal;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** String helpers. */
public final class Strings {
  private Strings() {}

  public static boolean isBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  public static @Nullable String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Splits a comma, semicolon or newline separated list, trimming and dropping blanks. */
  public static List<String> splitList(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (String part : value.split("[,;\\r\\n]")) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return List.copyOf(result);
  }

  public static @Nullable String truncate(@Nullable String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
