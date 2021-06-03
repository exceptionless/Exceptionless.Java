package com.exceptionless.exceptionlessclient.submission;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class SubmissionResponse {
  int code;
  String body;
  boolean rateLimitingHeaderFound;
  Exception exception;

  public boolean isSuccess() {
    return code >= 200 && code <= 299;
  }

  public boolean isBadRequest() {
    return code == 400;
  }

  public boolean isServiceUnavailable() {
    return code == 503;
  }

  public boolean isPaymentRequired() {
    return code == 402;
  }

  public boolean unableToAuthenticate() {
    return code == 401 || code == 403;
  }

  public boolean isNotFound() {
    return code == 404;
  }

  public boolean isRequestEntityTooLarge() {
    return code == 413;
  }

  public boolean isRateLimited() {
    return rateLimitingHeaderFound || code == 429;
  }

  public boolean hasException() {
    return exception != null;
  }
}
