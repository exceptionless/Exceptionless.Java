package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A loaded library (jar) and its version. */
public final class ModuleInfo implements JsonSerializable {
  private int moduleId;
  private @Nullable String name;
  private @Nullable String version;
  private boolean entry;
  private final Map<String, Object> data = new LinkedHashMap<>();

  public int getModuleId() {
    return moduleId;
  }

  public ModuleInfo setModuleId(int moduleId) {
    this.moduleId = moduleId;
    return this;
  }

  public @Nullable String getName() {
    return name;
  }

  public ModuleInfo setName(@Nullable String name) {
    this.name = name;
    return this;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public ModuleInfo setVersion(@Nullable String version) {
    this.version = version;
    return this;
  }

  public boolean isEntry() {
    return entry;
  }

  public ModuleInfo setEntry(boolean entry) {
    this.entry = entry;
    return this;
  }

  public Map<String, Object> getData() {
    return data;
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
        .field("module_id", moduleId)
        .fieldIfNotEmpty("name", name)
        .fieldIfNotEmpty("version", version)
        .field("is_entry", entry)
        .fieldIfNotEmpty("data", data)
        .endObject();
  }

  @Override
  public String toString() {
    return name + (version != null ? " " + version : "");
  }
}
