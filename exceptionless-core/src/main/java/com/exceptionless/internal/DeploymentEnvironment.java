package com.exceptionless.internal;

import org.jspecify.annotations.Nullable;

/**
 * Normalizes deployment environment names the way the server does, so the client never sends a
 * value the server would discard.
 */
public final class DeploymentEnvironment {
  /** The longest name the server accepts. */
  public static final int MAX_LENGTH = 64;

  private DeploymentEnvironment() {}

  /**
   * Trims the name and returns it with its casing preserved, or null when the server would treat it
   * as unspecified: empty, longer than {@value #MAX_LENGTH} characters or containing a control
   * character.
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
    if (start == end || end - start > MAX_LENGTH) {
      return null;
    }
    for (int i = start; i < end; i++) {
      if (Character.isISOControl(value.charAt(i))) {
        return null;
      }
    }
    return value.substring(start, end);
  }

  /**
   * Whether the server's trim removes the character: white space plus the Unicode space, line and
   * paragraph separators. This is wider than {@link String#strip()}, which keeps non-breaking
   * spaces.
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
