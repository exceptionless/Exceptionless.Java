package com.prashantchaubey.exceptionlessclient.models;

import com.prashantchaubey.exceptionlessclient.models.base.Model;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.net.http.HttpRequest;

// Warning `SuperBuilder` will not work for any class extending this. This class breaks the chain
// for customization
@Data
@EqualsAndHashCode(callSuper = true)
public class PluginContext extends Model {
  private boolean eventCancelled;
  private Exception exception;
  private Boolean unhandledError;
  private String submissionMethod;
  private HttpRequest requestInfo;

  @Builder
  public PluginContext(
      Exception exception,
      Boolean unhandledError,
      String submissionMethod,
      HttpRequest requestInfo) {
    this.exception = exception;
    this.unhandledError = unhandledError;
    this.submissionMethod = submissionMethod;
    this.requestInfo = requestInfo;
  }

  public boolean isUnhandledError() {
    return unhandledError != null && unhandledError;
  }
}
