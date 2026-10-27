package com.exceptionless;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Typed access to parsed JSON in tests, without unchecked casts. */
public final class TestJson {
  private TestJson() {}

  /** Asserts that {@code value} is a JSON object and returns a typed copy of it. */
  public static Map<String, @Nullable Object> object(@Nullable Object value) {
    assertThat(value).isInstanceOf(Map.class);
    Map<String, @Nullable Object> result = new LinkedHashMap<>();
    ((Map<?, ?>) value).forEach((key, item) -> result.put(String.valueOf(key), item));
    return result;
  }

  /** Asserts that {@code value} is an array of JSON objects and returns typed copies of them. */
  public static List<Map<String, @Nullable Object>> objects(@Nullable Object value) {
    assertThat(value).isInstanceOf(List.class);
    List<Map<String, @Nullable Object>> result = new ArrayList<>();
    for (Object item : (List<?>) value) {
      result.add(object(item));
    }
    return result;
  }
}
