package com.exceptionless.models;

import com.exceptionless.json.JsonSerializable;
import com.exceptionless.json.JsonWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * An error ({@code @error}) and its chain of causes. Modules are only set on the outermost error.
 */
public final class ErrorInfo implements JsonSerializable {
  private @Nullable String message;
  private @Nullable String type;
  private @Nullable String code;
  private @Nullable ErrorInfo inner;
  private final List<StackFrame> stackTrace = new ArrayList<>();
  private final List<ModuleInfo> modules = new ArrayList<>();
  private final Map<String, Object> data = new LinkedHashMap<>();

  public @Nullable String getMessage() {
    return message;
  }

  public ErrorInfo setMessage(@Nullable String message) {
    this.message = message;
    return this;
  }

  /** The fully qualified exception class name. */
  public @Nullable String getType() {
    return type;
  }

  public ErrorInfo setType(@Nullable String type) {
    this.type = type;
    return this;
  }

  public @Nullable String getCode() {
    return code;
  }

  public ErrorInfo setCode(@Nullable String code) {
    this.code = code;
    return this;
  }

  /** The cause of this error, if any. */
  public @Nullable ErrorInfo getInner() {
    return inner;
  }

  public ErrorInfo setInner(@Nullable ErrorInfo inner) {
    this.inner = inner;
    return this;
  }

  public List<StackFrame> getStackTrace() {
    return stackTrace;
  }

  public List<ModuleInfo> getModules() {
    return modules;
  }

  public Map<String, Object> getData() {
    return data;
  }

  /** The innermost (root cause) error. */
  public ErrorInfo getRootCause() {
    ErrorInfo current = this;
    while (current.inner != null) {
      current = current.inner;
    }
    return current;
  }

  @Override
  public void toJson(JsonWriter writer) {
    writer
        .beginObject()
        .fieldIfNotEmpty("message", message)
        .fieldIfNotEmpty("type", type)
        .fieldIfNotEmpty("code", code)
        .fieldIfNotEmpty("data", data)
        .field("inner", inner)
        .fieldIfNotEmpty("stack_trace", stackTrace)
        .fieldIfNotEmpty("modules", modules)
        .endObject();
  }

  @Override
  public String toString() {
    return type + (message != null ? ": " + message : "");
  }
}
