package com.exceptionless.storage;

import org.jspecify.annotations.Nullable;

/** Persists small pieces of client state, such as the server settings and the install id. */
public interface KeyValueStorage {
  @Nullable String read(String key);

  void write(String key, String value);
}
