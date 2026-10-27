package com.exceptionless.internal;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Identifier helpers. */
public final class Ids {
  private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern REFERENCE_ID = Pattern.compile("^[a-zA-Z\\d-]{8,100}$");

  private Ids() {}

  /** A new random reference id (16 base-36 characters, ~82 bits of entropy). */
  public static String newReferenceId() {
    char[] chars = new char[16];
    for (int i = 0; i < chars.length; i++) {
      chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
    }
    return new String(chars);
  }

  /** A new random 32 character hex id, used for session ids and install ids. */
  public static String newGuid() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  /** True when the id satisfies the server's reference id rules: 8-100 of [a-zA-Z0-9-]. */
  public static boolean isValidReferenceId(@Nullable String id) {
    return id != null && REFERENCE_ID.matcher(id).matches();
  }
}
