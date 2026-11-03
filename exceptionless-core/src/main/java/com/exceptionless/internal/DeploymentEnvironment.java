package com.exceptionless.internal;

import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Validates deployment environment names the way the server does, so the client never sends a value
 * the server would discard.
 */
public final class DeploymentEnvironment {
  /** ASCII letters, digits, {@code .}, {@code _} and {@code -}; starts with a letter or digit. */
  private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

  private DeploymentEnvironment() {}

  /**
   * Trims the name and returns it with its casing preserved, or null when it is not a valid
   * deployment environment name: ASCII letters, digits, {@code .}, {@code _} and {@code -},
   * starting with a letter or digit, at most 64 characters. Names are never rewritten.
   */
  public static @Nullable String normalize(@Nullable String value) {
    if (value == null) {
      return null;
    }
    int start = 0;
    int end = value.length();
    while (start < end && isTrimmable(value.charAt(start))) {
      start++;
    }
    while (end > start && isTrimmable(value.charAt(end - 1))) {
      end--;
    }
    String name = value.substring(start, end);
    return VALID_NAME.matcher(name).matches() ? name : null;
  }

  /**
   * Whether the trim removes the character: white space plus the Unicode space, line and paragraph
   * separators. This is wider than {@link String#strip()}, which keeps non-breaking spaces.
   */
  private static boolean isTrimmable(char c) {
    if ((c >= '\t' && c <= '\r') || c == ' ' || c == '\u0085') {
      return true;
    }
    int type = Character.getType(c);
    return type == Character.SPACE_SEPARATOR
        || type == Character.LINE_SEPARATOR
        || type == Character.PARAGRAPH_SEPARATOR;
  }
}
