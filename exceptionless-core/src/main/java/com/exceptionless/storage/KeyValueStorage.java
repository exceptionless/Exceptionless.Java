package com.exceptionless.storage;

import org.jspecify.annotations.Nullable;

/** Persists small pieces of client state, such as the server settings and the install id. */
public interface KeyValueStorage {
  /**
   * Reads a value.
   *
   * @param key the value's name; implementations may restrict which characters are allowed
   * @return the stored value, or {@code null} when nothing is stored under {@code key} or it can't
   *     be read
   */
  @Nullable String read(String key);

  /**
   * Stores a value, replacing any existing value for {@code key}. Failures are logged rather than
   * thrown: losing client state must never break the application.
   *
   * @param key the value's name; implementations may restrict which characters are allowed
   * @param value the value to store
   */
  void write(String key, String value);
}
