package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** The user an event happened to ({@code @user}). */
public record UserInfo(
    @Nullable String identity, @Nullable String name, @Nullable Map<String, Object> data)
    implements JsonSerializable {

  /** Copies {@code data} into an unmodifiable map that keeps its order. */
  public UserInfo {
    data = data == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(data));
  }

  public UserInfo(@Nullable String identity, @Nullable String name) {
    this(identity, name, null);
  }

  public UserInfo(@Nullable String identity) {
    this(identity, null, null);
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
        .fieldIfNotEmpty("identity", identity)
        .fieldIfNotEmpty("name", name)
        .fieldIfNotEmpty("data", data)
        .endObject();
  }
}
