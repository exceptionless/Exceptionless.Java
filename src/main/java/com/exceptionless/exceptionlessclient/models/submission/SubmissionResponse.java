package com.exceptionless.exceptionlessclient.models.submission;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Builder
@Value
@NonFinal
public class SubmissionResponse {
  int statusCode;
  String message;

  public boolean isSuccess() {
    return statusCode >= 200 && statusCode <= 299;
  }

  public boolean isBadRequest() {
    return statusCode == 400;
  }

  public boolean isServiceUnavailable() {
    return statusCode == 503;
  }

  public boolean isPaymentRequired() {
    return statusCode == 402;
  }

  public boolean unableToAuthenticate() {
    return statusCode == 401 || statusCode == 403;
  }

  public boolean isNotFound() {
    return statusCode == 404;
  }

  public boolean isRequestEntityTooLarge() {
    return statusCode == 413;
  }
}
