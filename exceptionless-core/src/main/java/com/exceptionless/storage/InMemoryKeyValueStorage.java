package com.exceptionless.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Keeps client state in memory for the lifetime of the process. */
public final class InMemoryKeyValueStorage implements KeyValueStorage {
  private final Map<String, String> values = new ConcurrentHashMap<>();

  @Override
  public @Nullable String read(String key) {
    return values.get(key);
  }

  @Override
  public void write(String key, String value) {
    values.put(key, value);
  }
}
