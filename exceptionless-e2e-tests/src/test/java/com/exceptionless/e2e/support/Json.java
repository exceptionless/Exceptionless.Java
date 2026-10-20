package com.exceptionless.e2e.support;

import com.exceptionless.json.JsonParser;
import java.util.List;
import java.util.Map;

/** Navigation helpers for JSON parsed with the client's {@link JsonParser}. */
public final class Json {
  private Json() {}

  public static Object parse(String json) {
    return JsonParser.parse(json);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> object(Object value) {
    if (!(value instanceof Map<?, ?>)) {
      throw new AssertionError("Expected a JSON object but got: " + value);
    }
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  public static List<Object> array(Object value) {
    if (!(value instanceof List<?>)) {
      throw new AssertionError("Expected a JSON array but got: " + value);
    }
    return (List<Object>) value;
  }

  /**
   * Follows a path of object keys and array indexes, e.g. {@code at(event, "data", "@error",
   * "stack_trace", 0)}. Returns null when any step is missing.
   */
  public static Object at(Object root, Object... path) {
    Object current = root;
    for (Object step : path) {
      if (current == null) {
        return null;
      }
      if (step instanceof Integer index) {
        if (!(current instanceof List<?> list) || index >= list.size()) {
          return null;
        }
        current = list.get(index);
      } else {
        if (!(current instanceof Map<?, ?> map)) {
          return null;
        }
        current = map.get(String.valueOf(step));
      }
    }
    return current;
  }

  public static Map<String, Object> objectAt(Object root, Object... path) {
    Object value = at(root, path);
    if (value == null) {
      throw new AssertionError("Missing " + describe(path) + " in " + root);
    }
    return object(value);
  }

  public static List<Object> arrayAt(Object root, Object... path) {
    Object value = at(root, path);
    if (value == null) {
      throw new AssertionError("Missing " + describe(path) + " in " + root);
    }
    return array(value);
  }

  public static String describe(Object... path) {
    StringBuilder sb = new StringBuilder();
    for (Object step : path) {
      if (step instanceof Integer) {
        sb.append('[').append(step).append(']');
      } else {
        if (!sb.isEmpty()) {
          sb.append('.');
        }
        sb.append(step);
      }
    }
    return sb.toString();
  }
}
