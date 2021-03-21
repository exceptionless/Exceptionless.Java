package com.prashantchaubey.exceptionlessclient.plugins;

import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;

import java.util.HashMap;
import java.util.Map;

// todo think about moving it to models as it only contains presentation logic
public class ContextData {
  private Map<String, Object> data;

  private static final class Keys {
    private Keys() {}

    private static final String EXCEPTION = "@@_Exception";
    private static final String IS_UNHANDLED_ERROR = "@@_IsUnhandledError";
    private static final String SUBMISSION_METHOD = "@@_SubmissionMethod";
    private static final String REQUEST_INFO = "@request";
  }

  public ContextData() {
    this.data = new HashMap<>();
  }

  public void setException(Exception exception) {
    data.put(Keys.EXCEPTION, exception);
  }

  public boolean hasException() {
    return data.get(Keys.EXCEPTION) != null;
  }

  public Exception getException() {
    return (Exception) data.get(Keys.EXCEPTION);
  }

  public void markAsUnhandledError() {
    data.put(Keys.IS_UNHANDLED_ERROR, true);
  }

  public boolean isUnhandledError() {
    return data.containsKey(Keys.IS_UNHANDLED_ERROR) && (boolean) data.get(Keys.IS_UNHANDLED_ERROR);
  }

  public void setSubmissionMethod(String method) {
    data.put(Keys.SUBMISSION_METHOD, method);
  }

  public String getSubmissionMethod() {
    return (String) data.get(Keys.SUBMISSION_METHOD);
  }

  public void addRequestInfo(RequestInfo requestInfo) {
    data.put(Keys.REQUEST_INFO, requestInfo);
  }
}
