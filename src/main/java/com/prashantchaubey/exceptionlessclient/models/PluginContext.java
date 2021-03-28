package com.prashantchaubey.exceptionlessclient.models;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import com.prashantchaubey.exceptionlessclient.models.enums.PluginContextKey;
import com.prashantchaubey.exceptionlessclient.models.services.RequestInfo;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Optional;

import static com.prashantchaubey.exceptionlessclient.utils.EventUtils.safeGetAs;

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
    return getException().isPresent();
  }

  public Optional<Exception> getException() {
    return Optional.ofNullable(
        safeGetAs(data.get(PluginContextKey.EXCEPTION.value()), Exception.class));
  }

  public boolean isUnhandledError() {
    Boolean unhandledError =
        safeGetAs(data.get(PluginContextKey.IS_UNHANDLED_ERROR.value()), Boolean.class);
    return unhandledError != null && unhandledError;
  }

  public Optional<String> getSubmissionMethod() {
    return Optional.ofNullable(
        safeGetAs(data.get(PluginContextKey.SUBMISSION_METHOD.value()), String.class));
  }
}
