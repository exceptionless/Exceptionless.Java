package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** A single stack frame. Java packages map to {@code declaring_namespace}. */
public final class StackFrame implements JsonSerializable {
  private @Nullable String name;
  private @Nullable String declaringNamespace;
  private @Nullable String declaringType;
  private @Nullable String fileName;
  private @Nullable Integer lineNumber;
  private @Nullable Integer column;
  private @Nullable Integer moduleId;
  private final Map<String, Object> data = new LinkedHashMap<>();

  public @Nullable String getName() {
    return name;
  }

  public StackFrame setName(@Nullable String name) {
    this.name = name;
    return this;
  }

  public @Nullable String getDeclaringNamespace() {
    return declaringNamespace;
  }

  public StackFrame setDeclaringNamespace(@Nullable String declaringNamespace) {
    this.declaringNamespace = declaringNamespace;
    return this;
  }

  public @Nullable String getDeclaringType() {
    return declaringType;
  }

  public StackFrame setDeclaringType(@Nullable String declaringType) {
    this.declaringType = declaringType;
    return this;
  }

  public @Nullable String getFileName() {
    return fileName;
  }

  public StackFrame setFileName(@Nullable String fileName) {
    this.fileName = fileName;
    return this;
  }

  public @Nullable Integer getLineNumber() {
    return lineNumber;
  }

  public StackFrame setLineNumber(@Nullable Integer lineNumber) {
    this.lineNumber = lineNumber;
    return this;
  }

  public @Nullable Integer getColumn() {
    return column;
  }

  public StackFrame setColumn(@Nullable Integer column) {
    this.column = column;
    return this;
  }

  public @Nullable Integer getModuleId() {
    return moduleId;
  }

  public StackFrame setModuleId(@Nullable Integer moduleId) {
    this.moduleId = moduleId;
    return this;
  }

  public Map<String, Object> getData() {
    return data;
  }

  /** {@code namespace.Type.method}, the way the frame is displayed. */
  public String getFullName() {
    StringBuilder sb = new StringBuilder();
    if (declaringNamespace != null && !declaringNamespace.isEmpty()) {
      sb.append(declaringNamespace).append('.');
    }
    if (declaringType != null && !declaringType.isEmpty()) {
      sb.append(declaringType).append('.');
    }
    sb.append(Objects.requireNonNullElse(name, "<unknown>"));
    return sb.toString();
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
        .fieldIfNotEmpty("name", name)
        .fieldIfNotEmpty("declaring_namespace", declaringNamespace)
        .fieldIfNotEmpty("declaring_type", declaringType)
        .fieldIfNotEmpty("file_name", fileName)
        .field("line_number", lineNumber)
        .field("column", column)
        .field("module_id", moduleId)
        .fieldIfNotEmpty("data", data)
        .endObject();
  }

  @Override
  public String toString() {
    return getFullName() + (fileName != null ? "(" + fileName + ":" + lineNumber + ")" : "");
  }
}
