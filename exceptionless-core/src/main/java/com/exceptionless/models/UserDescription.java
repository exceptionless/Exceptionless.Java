package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A user supplied description of what they were doing ({@code @user_description}). */
public record UserDescription(
    @Nullable String emailAddress, @Nullable String description, @Nullable Map<String, Object> data)
    implements JsonSerializable {

  /** Copies {@code data} into an unmodifiable map that keeps its order. */
  public UserDescription {
    data = data == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(data));
  }

  public UserDescription(@Nullable String emailAddress, @Nullable String description) {
    this(emailAddress, description, null);
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
        .fieldIfNotEmpty("email_address", emailAddress)
        .fieldIfNotEmpty("description", description)
        .fieldIfNotEmpty("data", data)
        .endObject();
  }
}
