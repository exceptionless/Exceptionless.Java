package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Overrides the server's automatic stacking ({@code @stack}). All events with the same signature
 * data are grouped into one stack.
 */
public record ManualStackingInfo(@Nullable String title, Map<String, String> signatureData)
    implements JsonSerializable {

  /** The signature key used by {@code setManualStackingKey}, shared with the other clients. */
  public static final String MANUAL_STACKING_KEY = "ManualStackingKey";

  public static ManualStackingInfo ofKey(String key, @Nullable String title) {
    return new ManualStackingInfo(title, Map.of(MANUAL_STACKING_KEY, key));
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
        .fieldIfNotEmpty("title", title)
        .field("signature_data", signatureData)
        .endObject();
  }
}
