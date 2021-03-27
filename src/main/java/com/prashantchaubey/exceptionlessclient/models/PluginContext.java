package com.prashantchaubey.exceptionlessclient.models;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import com.prashantchaubey.exceptionlessclient.models.enums.PluginContextKey;
import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

// Warning `SuperBuilder` will not work for any class extending this. This class breaks the chain
// for customization
@Data
@EqualsAndHashCode(callSuper = true)
public class PluginContext extends Model {
  private boolean eventCancelled;

  @Builder
  public PluginContext(
      Exception exception,
      Boolean unhandledError,
      String submissionMethod,
      RequestInfo requestInfo) {
    if (exception != null) {
      this.data.put(PluginContextKey.EXCEPTION.value(), exception);
    }
    if (unhandledError != null) {
      this.data.put(PluginContextKey.IS_UNHANDLED_ERROR.value(), true);
    }
    if (submissionMethod != null) {
      this.data.put(PluginContextKey.SUBMISSION_METHOD.value(), submissionMethod);
    }
    if (requestInfo != null) {
      this.data.put(PluginContextKey.REQUEST_INFO.value(), requestInfo);
    }
  }

  public boolean hasException() {
    return data.get(PluginContextKey.EXCEPTION.value()) != null;
  }

  public Exception getException() {
    return (Exception) data.get(PluginContextKey.EXCEPTION.value());
  }

  public boolean isUnhandledError() {
    return data.containsKey(PluginContextKey.IS_UNHANDLED_ERROR.value())
        && (boolean) data.get(PluginContextKey.IS_UNHANDLED_ERROR.value());
  }

  public String getSubmissionMethod() {
    return (String) data.get(PluginContextKey.SUBMISSION_METHOD.value());
  }

  public void markAsCancelled() {
    eventCancelled = true;
  }
}
