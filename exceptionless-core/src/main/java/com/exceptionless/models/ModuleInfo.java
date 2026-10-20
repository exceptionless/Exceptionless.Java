package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import org.jspecify.annotations.Nullable;

/**
 * A loaded library (jar) and its version.
 *
 * @param moduleId the module's index, referenced by {@link StackFrame#getModuleId()}
 * @param name the library name
 * @param version the library version, if known
 * @param entry whether the application was started from this library
 */
public record ModuleInfo(int moduleId, String name, @Nullable String version, boolean entry)
    implements JsonSerializable {

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
        .field("module_id", moduleId)
        .fieldIfNotEmpty("name", name)
        .fieldIfNotEmpty("version", version)
        .field("is_entry", entry)
        .endObject();
  }

  @Override
  public String toString() {
    return version != null ? name + " " + version : name;
  }
}
