package com.prashantchaubey.exceptionlessclient.plugins;

import java.util.HashMap;
import java.util.Map;

// todo think about moving it to models as it only contains presentation logic
public class ContextData {
  private Map<String, Object> data;

  private static final class KnownKeys {
    private KnownKeys() {}

    private static final String EXCEPTION = "@@_Exception";
    private static final String IS_UNHANDLED_ERROR = "@@_IsUnhandledError";
    private static final String SUBMISSION_METHOD = "@@_SubmissionMethod";
  }

  public ContextData() {
    this.data = new HashMap<>();
  }

  public void setException(Exception exception) {
    if (exception == null) {
      return;
    }
    data.put(KnownKeys.EXCEPTION, exception);
  }

  public boolean hasException() {
    return data.get(KnownKeys.EXCEPTION) != null;
  }

  public Exception getException() {
    return (Exception) data.get(KnownKeys.EXCEPTION);
  }

  public void markAsUnhandledError() {
    data.put(KnownKeys.IS_UNHANDLED_ERROR, true);
  }

  public boolean isUnhandledError() {
    return data.containsKey(KnownKeys.IS_UNHANDLED_ERROR)
        && (Boolean) data.get(KnownKeys.IS_UNHANDLED_ERROR);
  }

  public void setSubmissionMethod(String method) {
    if (method == null) {
      return;
    }
    data.put(KnownKeys.SUBMISSION_METHOD, method);
  }

  public String getSubmissionMethod() {
    return (String) data.get(KnownKeys.SUBMISSION_METHOD);
  }
}
