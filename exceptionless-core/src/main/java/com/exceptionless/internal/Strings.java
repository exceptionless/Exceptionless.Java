package com.exceptionless.internal;

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

  public static @Nullable String truncate(@Nullable String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
