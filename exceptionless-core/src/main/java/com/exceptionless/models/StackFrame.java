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

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public @Nullable String getDeclaringNamespace() {
    return declaringNamespace;
  }

  public void setDeclaringNamespace(@Nullable String declaringNamespace) {
    this.declaringNamespace = declaringNamespace;
  }

  public @Nullable String getDeclaringType() {
    return declaringType;
  }

  public void setDeclaringType(@Nullable String declaringType) {
    this.declaringType = declaringType;
  }

  public @Nullable String getFileName() {
    return fileName;
  }

  public void setFileName(@Nullable String fileName) {
    this.fileName = fileName;
  }

  public @Nullable Integer getLineNumber() {
    return lineNumber;
  }

  public void setLineNumber(@Nullable Integer lineNumber) {
    this.lineNumber = lineNumber;
  }

  public @Nullable Integer getColumn() {
    return column;
  }

  public void setColumn(@Nullable Integer column) {
    this.column = column;
  }

  public @Nullable Integer getModuleId() {
    return moduleId;
  }

  public void setModuleId(@Nullable Integer moduleId) {
    this.moduleId = moduleId;
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
    if (fileName == null) {
      return getFullName();
    }
    return getFullName() + "(" + fileName + (lineNumber != null ? ":" + lineNumber : "") + ")";
  }
}
