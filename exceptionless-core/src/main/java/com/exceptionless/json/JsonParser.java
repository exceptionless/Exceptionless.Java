package com.exceptionless.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A small, strict JSON parser. Objects become {@link LinkedHashMap}, arrays become {@link
 * ArrayList}, numbers become {@link Long} or {@link BigDecimal}.
 */
public final class JsonParser {
  private static final int MAX_DEPTH = 256;

  private final String input;
  private int pos;
  private int depth;

  private JsonParser(String input) {
    this.input = input;
  }

  /**
   * Parses a JSON document.
   *
   * @throws IllegalArgumentException if the input is not valid JSON
   */
  public static @Nullable Object parse(String json) {
    JsonParser parser = new JsonParser(json);
    parser.skipWhitespace();
    Object value = parser.readValue();
    parser.skipWhitespace();
    if (parser.pos != parser.input.length()) {
      throw parser.error("Unexpected trailing content");
    }
    return value;
  }

  /** Parses a JSON object, failing if the document is not an object. */
  @SuppressWarnings("unchecked")
  public static Map<String, @Nullable Object> parseObject(String json) {
    Object value = parse(json);
    if (value instanceof Map<?, ?> map) {
      return (Map<String, @Nullable Object>) map;
    }
    throw new IllegalArgumentException("Expected a JSON object");
  }

  private @Nullable Object readValue() {
    if (pos >= input.length()) {
      throw error("Unexpected end of input");
    }
    char c = input.charAt(pos);
    return switch (c) {
      case '{' -> readObject();
      case '[' -> readArray();
      case '"' -> readString();
      case 't' -> readLiteral("true", Boolean.TRUE);
      case 'f' -> readLiteral("false", Boolean.FALSE);
      case 'n' -> readLiteral("null", null);
      default -> {
        if (c == '-' || (c >= '0' && c <= '9')) {
          yield readNumber();
        }
        throw error("Unexpected character '" + c + "'");
      }
    };
  }

  private Map<String, @Nullable Object> readObject() {
    enter();
    pos++; // {
    Map<String, @Nullable Object> map = new LinkedHashMap<>();
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      depth--;
      return map;
    }
    while (true) {
      skipWhitespace();
      if (peek() != '"') {
        throw error("Expected property name");
      }
      String name = readString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      map.put(name, readValue());
      skipWhitespace();
      char c = next();
      if (c == '}') {
        break;
      }
      if (c != ',') {
        throw error("Expected ',' or '}'");
      }
    }
    depth--;
    return map;
  }

  private List<@Nullable Object> readArray() {
    enter();
    pos++; // [
    List<@Nullable Object> list = new ArrayList<>();
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      depth--;
      return list;
    }
    while (true) {
      skipWhitespace();
      list.add(readValue());
      skipWhitespace();
      char c = next();
      if (c == ']') {
        break;
      }
      if (c != ',') {
        throw error("Expected ',' or ']'");
      }
    }
    depth--;
    return list;
  }

  private String readString() {
    expect('"');
    StringBuilder sb = null;
    int start = pos;
    while (true) {
      if (pos >= input.length()) {
        throw error("Unterminated string");
      }
      char c = input.charAt(pos++);
      if (c == '"') {
        if (sb == null) {
          return input.substring(start, pos - 1);
        }
        return sb.toString();
      }
      if (c == '\\') {
        if (sb == null) {
          sb = new StringBuilder(input.substring(start, pos - 1));
        }
        char escaped = next();
        switch (escaped) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (pos + 4 > input.length()) {
              throw error("Invalid unicode escape");
            }
            try {
              sb.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
            } catch (NumberFormatException e) {
              throw error("Invalid unicode escape");
            }
            pos += 4;
          }
          default -> throw error("Invalid escape '\\" + escaped + "'");
        }
      } else if (c < 0x20) {
        throw error("Unescaped control character in string");
      } else if (sb != null) {
        sb.append(c);
      }
    }
  }

  private Number readNumber() {
    int start = pos;
    if (peek() == '-') {
      pos++;
    }
    boolean decimal = false;
    while (pos < input.length()) {
      char c = input.charAt(pos);
      if (c >= '0' && c <= '9') {
        pos++;
      } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
        decimal = true;
        pos++;
      } else {
        break;
      }
    }
    String text = input.substring(start, pos);
    try {
      if (!decimal) {
        try {
          return Long.parseLong(text);
        } catch (NumberFormatException overflow) {
          return new BigDecimal(text);
        }
      }
      return new BigDecimal(text);
    } catch (NumberFormatException e) {
      throw error("Invalid number '" + text + "'");
    }
  }

  private @Nullable Object readLiteral(String literal, @Nullable Object value) {
    if (!input.startsWith(literal, pos)) {
      throw error("Invalid literal");
    }
    pos += literal.length();
    return value;
  }

  private void enter() {
    if (++depth > MAX_DEPTH) {
      throw error("JSON nested too deeply");
    }
  }

  private void skipWhitespace() {
    while (pos < input.length()) {
      char c = input.charAt(pos);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '﻿') {
        pos++;
      } else {
        break;
      }
    }
  }

  private char peek() {
    if (pos >= input.length()) {
      throw error("Unexpected end of input");
    }
    return input.charAt(pos);
  }

  private char next() {
    char c = peek();
    pos++;
    return c;
  }

  private void expect(char expected) {
    if (next() != expected) {
      pos--;
      throw error("Expected '" + expected + "'");
    }
  }

  private IllegalArgumentException error(String message) {
    return new IllegalArgumentException(message + " at position " + pos);
  }
}
