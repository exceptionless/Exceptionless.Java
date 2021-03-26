package com.prashantchaubey.exceptionlessclient.utils;

public final class EventUtils {
  private EventUtils() {}

  public static <T> T safeGetAs(Object value, Class<T> cls) {
    if (value == null) {
      return null;
    }

    try {
      return cls.cast(value);
    } catch (ClassCastException ignored) {
      return null;
    }
  }
}
