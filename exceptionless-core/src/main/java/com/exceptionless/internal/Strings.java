package com.exceptionless.internal;

import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** String helpers. */
public final class Strings {
  private static final Pattern LIST_SEPARATORS = Pattern.compile("[,;\\r\\n]");

  private Strings() {}

  public static boolean isBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  /** {@return {@code value} without leading and trailing white space, or null when blank} */
  public static @Nullable String stripToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String stripped = value.strip();
    return stripped.isEmpty() ? null : stripped;
  }

  /** Splits a comma, semicolon or newline separated list, stripping and dropping blanks. */
  public static List<String> splitList(@Nullable String value) {
    if (isBlank(value)) {
      return List.of();
    }
    return LIST_SEPARATORS
        .splitAsStream(value)
        .map(String::strip)
        .filter(part -> !part.isEmpty())
        .toList();
  }

  /** {@return {@code value}, cut to at most {@code maxLength} characters} */
  public static String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }
}
