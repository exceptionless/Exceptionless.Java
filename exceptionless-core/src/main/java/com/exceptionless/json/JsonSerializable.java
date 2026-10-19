package com.exceptionless.json;

/**
 * A type that knows how to write itself as JSON using the Exceptionless wire format (snake_case
 * property names, null properties omitted).
 */
public interface JsonSerializable {
  /** Writes this value as a single JSON value (usually an object). */
  void toJson(JsonWriter writer);
}
