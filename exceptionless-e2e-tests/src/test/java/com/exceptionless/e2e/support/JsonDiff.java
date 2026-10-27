package com.exceptionless.e2e.support;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compares the JSON the client sent with what the server stored and lists every value the client
 * sent that the server dropped or changed. Extra fields added by the server are ignored.
 *
 * <p>This is the core wire-format check: the server deserializes known data keys ({@code
 * @error}, {@code @request}, {@code @environment}, ...) into its typed models and writes them back,
 * so a misnamed or wrongly typed field shows up here as missing.
 */
public final class JsonDiff {
  private JsonDiff() {}

  public static List<String> diff(Object sent, Object stored) {
    List<String> differences = new ArrayList<>();
    compare("", sent, stored, differences);
    return differences;
  }

  private static void compare(String path, Object sent, Object stored, List<String> out) {
    if (sent == null) {
      return;
    }
    if (stored == null) {
      out.add(path + ": sent " + abbreviate(sent) + " but the server did not store it");
      return;
    }
    if (sent instanceof Map<?, ?> sentMap) {
      if (!(stored instanceof Map<?, ?> storedMap)) {
        out.add(path + ": sent an object but stored " + abbreviate(stored));
        return;
      }
      for (Map.Entry<?, ?> entry : sentMap.entrySet()) {
        String key = String.valueOf(entry.getKey());
        compare(path.isEmpty() ? key : path + "." + key, entry.getValue(), storedMap.get(key), out);
      }
      return;
    }
    if (sent instanceof List<?> sentList) {
      if (!(stored instanceof List<?> storedList)) {
        out.add(path + ": sent an array but stored " + abbreviate(stored));
        return;
      }
      if (sentList.size() != storedList.size()) {
        out.add(path + ": sent " + sentList.size() + " items but stored " + storedList.size());
      }
      for (int i = 0; i < Math.min(sentList.size(), storedList.size()); i++) {
        compare(path + "[" + i + "]", sentList.get(i), storedList.get(i), out);
      }
      return;
    }
    if (!scalarEquals(sent, stored)) {
      out.add(path + ": sent " + abbreviate(sent) + " but stored " + abbreviate(stored));
    }
  }

  private static boolean scalarEquals(Object sent, Object stored) {
    if (sent instanceof Number a && stored instanceof Number b) {
      return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
    }
    if (sent instanceof String a && stored instanceof String b && !a.equals(b)) {
      // Dates are stored with .NET's 100ns precision (Java sends nanoseconds).
      try {
        Duration delta =
            Duration.between(
                OffsetDateTime.parse(a).toInstant(), OffsetDateTime.parse(b).toInstant());
        return delta.abs().compareTo(Duration.ofNanos(100)) < 0;
      } catch (DateTimeParseException e) {
        return false;
      }
    }
    return Objects.equals(sent, stored);
  }

  private static String abbreviate(Object value) {
    String text = value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
    return text.length() > 120 ? text.substring(0, 117) + "..." : text;
  }
}
