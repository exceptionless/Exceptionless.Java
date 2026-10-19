package com.exceptionless.plugins;

import com.exceptionless.models.RequestInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Data that travels with an event through the plugin pipeline but is not sent to the server, such
 * as the original exception.
 */
public final class ContextData {
  private static final String EXCEPTION = "@@_Exception";
  private static final String IS_UNHANDLED = "@@_IsUnhandledError";
  private static final String SUBMISSION_METHOD = "@@_SubmissionMethod";
  private static final String REQUEST_INFO = "@@_RequestInfo";

  private final Map<String, Object> values = new LinkedHashMap<>();

  public @Nullable Object get(String key) {
    return values.get(key);
  }

  public ContextData put(String key, @Nullable Object value) {
    if (value == null) {
      values.remove(key);
    } else {
      values.put(key, value);
    }
    return this;
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public @Nullable Throwable getException() {
    return values.get(EXCEPTION) instanceof Throwable throwable ? throwable : null;
  }

  public ContextData setException(@Nullable Throwable exception) {
    return put(EXCEPTION, exception);
  }

  public boolean isUnhandledError() {
    return Boolean.TRUE.equals(values.get(IS_UNHANDLED));
  }

  /** Marks the error as unhandled (it crashed a thread or request). */
  public ContextData markAsUnhandledError() {
    return put(IS_UNHANDLED, Boolean.TRUE);
  }

  public @Nullable String getSubmissionMethod() {
    return values.get(SUBMISSION_METHOD) instanceof String method ? method : null;
  }

  /** How the event was captured, e.g. {@code uncaughtExceptionHandler} or {@code servletFilter}. */
  public ContextData setSubmissionMethod(@Nullable String submissionMethod) {
    return put(SUBMISSION_METHOD, submissionMethod);
  }

  public @Nullable RequestInfo getRequestInfo() {
    return values.get(REQUEST_INFO) instanceof RequestInfo request ? request : null;
  }

  /** The request the event happened during. Privacy settings are applied by the pipeline. */
  public ContextData setRequestInfo(@Nullable RequestInfo requestInfo) {
    return put(REQUEST_INFO, requestInfo);
  }
}
