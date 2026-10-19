package com.exceptionless.json;

import com.exceptionless.internal.DataNormalizer;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * A small streaming JSON writer. It never throws for unexpected value types: anything it does not
 * understand is written using {@link Object#toString()}, so serializing an event can't fail.
 *
 * <p>Simple values (dates, enums, UUIDs, paths and so on) are written as strings exactly like
 * {@link DataNormalizer} converts them, so a value looks the same whether or not it was normalized
 * first.
 */
public final class JsonWriter {
  private static final int MAX_DEPTH = 64;
  private static final HexFormat HEX = HexFormat.of();

  private final StringBuilder out;
  // One entry per open container: true when the next element needs a leading comma.
  private boolean[] needsComma = new boolean[16];
  private int depth;
  private boolean afterName;

  /** Creates a writer with its own buffer; read the result with {@link #toString()}. */
  public JsonWriter() {
    this(new StringBuilder(256));
  }

  /** Creates a writer that appends to {@code out}. */
  public JsonWriter(StringBuilder out) {
    this.out = out;
  }

  /** Serializes {@code value} to a JSON string. */
  public static String toJson(@Nullable Object value) {
    JsonWriter writer = new JsonWriter();
    writer.value(value);
    return writer.toString();
  }

  /** Starts an object: write {@link #name(String) name}/value pairs, then {@link #endObject()}. */
  public JsonWriter beginObject() {
    beforeValue();
    out.append('{');
    push();
    return this;
  }

  /** Ends the current object. */
  public JsonWriter endObject() {
    pop();
    out.append('}');
    return this;
  }

  /** Starts an array: write values, then {@link #endArray()}. */
  public JsonWriter beginArray() {
    beforeValue();
    out.append('[');
    push();
    return this;
  }

  /** Ends the current array. */
  public JsonWriter endArray() {
    pop();
    out.append(']');
    return this;
  }

  /** Writes a property name. Must be followed by exactly one value. */
  public JsonWriter name(String name) {
    if (depth > 0 && needsComma[depth - 1]) {
      out.append(',');
    }
    if (depth > 0) {
      needsComma[depth - 1] = true;
    }
    writeString(name);
    out.append(':');
    afterName = true;
    return this;
  }

  /** Writes {@code "name": value} unless the value is {@code null}. */
  public JsonWriter field(String name, @Nullable Object value) {
    if (value == null) {
      return this;
    }
    name(name);
    return value(value);
  }

  /** Writes {@code "name": value} unless the value is {@code null}, empty or blank. */
  public JsonWriter fieldIfNotEmpty(String name, @Nullable Object value) {
    if (value == null) {
      return this;
    }
    if (value instanceof CharSequence && value.toString().isBlank()) {
      return this;
    }
    if (value instanceof Map<?, ?> map && map.isEmpty()) {
      return this;
    }
    if (value instanceof Collection<?> collection && collection.isEmpty()) {
      return this;
    }
    name(name);
    return value(value);
  }

  /** Writes {@code null}. */
  public JsonWriter nullValue() {
    beforeValue();
    out.append("null");
    return this;
  }

  /** Writes any supported value. Unknown types are written as their {@code toString()}. */
  public JsonWriter value(@Nullable Object value) {
    if (value == null) {
      return nullValue();
    }
    if (depth >= MAX_DEPTH) {
      return string("...");
    }
    if (value instanceof JsonSerializable serializable) {
      int before = depth;
      serializable.toJson(this);
      if (depth != before) {
        throw new IllegalStateException(
            value.getClass().getName() + ".toJson left the writer unbalanced");
      }
      return this;
    }
    if (value instanceof CharSequence || value instanceof Character) {
      return string(value.toString());
    }
    if (value instanceof Boolean bool) {
      beforeValue();
      out.append(bool.booleanValue() ? "true" : "false");
      return this;
    }
    if (value instanceof Number number) {
      return number(number);
    }
    if (DataNormalizer.isSimpleValue(value)) {
      // Before Iterable: a Path is an Iterable of Paths.
      return string(DataNormalizer.simpleValueToString(value));
    }
    if (value instanceof Map<?, ?> map) {
      beginObject();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        name(String.valueOf(entry.getKey()));
        value(entry.getValue());
      }
      return endObject();
    }
    if (value instanceof Iterable<?> iterable) {
      beginArray();
      for (Object item : iterable) {
        value(item);
      }
      return endArray();
    }
    if (value.getClass().isArray()) {
      beginArray();
      int length = Array.getLength(value);
      for (int i = 0; i < length; i++) {
        value(Array.get(value, i));
      }
      return endArray();
    }
    if (value instanceof Optional<?> optional) {
      return value(optional.orElse(null));
    }
    if (value instanceof OptionalInt optional) {
      return optional.isPresent() ? number(optional.getAsInt()) : nullValue();
    }
    if (value instanceof OptionalLong optional) {
      return optional.isPresent() ? number(optional.getAsLong()) : nullValue();
    }
    if (value instanceof OptionalDouble optional) {
      return optional.isPresent() ? number(optional.getAsDouble()) : nullValue();
    }
    return string(String.valueOf(value));
  }

  /** Writes a string value, escaping it as needed. */
  public JsonWriter string(String value) {
    beforeValue();
    writeString(value);
    return this;
  }

  /**
   * Writes a number. Whole floating point values are written without a fraction; NaN and infinity,
   * which JSON can't represent, are written as {@code null}.
   */
  public JsonWriter number(Number value) {
    if (value instanceof Double || value instanceof Float) {
      double d = value.doubleValue();
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        return nullValue();
      }
      beforeValue();
      if (d == Math.rint(d) && Math.abs(d) < 1e15) {
        out.append((long) d);
      } else {
        out.append(value);
      }
      return this;
    }
    beforeValue();
    if (value instanceof BigDecimal decimal) {
      out.append(decimal.toPlainString());
    } else if (value instanceof Integer
        || value instanceof Long
        || value instanceof Short
        || value instanceof Byte
        || value instanceof BigInteger
        || value instanceof AtomicInteger
        || value instanceof AtomicLong) {
      out.append(value);
    } else {
      // Unknown Number subclass: fall back to its double value.
      double d = value.doubleValue();
      if (Double.isNaN(d) || Double.isInfinite(d)) {
        out.append("null");
      } else {
        out.append(BigDecimal.valueOf(d).toPlainString());
      }
    }
    return this;
  }

  /** {@return the JSON written so far} */
  @Override
  public String toString() {
    return out.toString();
  }

  private void beforeValue() {
    if (afterName) {
      afterName = false;
      return;
    }
    if (depth > 0) {
      if (needsComma[depth - 1]) {
        out.append(',');
      }
      needsComma[depth - 1] = true;
    }
  }

  private void push() {
    if (depth == needsComma.length) {
      needsComma = Arrays.copyOf(needsComma, needsComma.length * 2);
    }
    needsComma[depth++] = false;
  }

  private void pop() {
    if (depth == 0) {
      throw new IllegalStateException("Unbalanced JSON container");
    }
    depth--;
  }

  private void writeString(String value) {
    out.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        default -> {
          if (c < 0x20 || c == ' ' || c == ' ') {
            out.append("\\u").append(HEX.toHexDigits(c));
          } else if (Character.isSurrogate(c)) {
            // Keep valid surrogate pairs; replace lone surrogates so the output is valid UTF-8.
            if (Character.isHighSurrogate(c)
                && i + 1 < value.length()
                && Character.isLowSurrogate(value.charAt(i + 1))) {
              out.append(c).append(value.charAt(++i));
            } else {
              out.append('�');
            }
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }
}
