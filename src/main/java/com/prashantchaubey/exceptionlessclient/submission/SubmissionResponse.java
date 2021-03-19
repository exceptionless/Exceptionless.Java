package com.prashantchaubey.exceptionlessclient.submission;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SubmissionResponse {
  private int statusCode;
  private String message;

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

  private boolean isUnauthenticated() {
    return statusCode == 401;
  }

  private boolean isForbidden() {
    return statusCode == 403;
  }

  private boolean isNotFound() {
    return statusCode == 404;
  }

  private boolean isRequestEntityTooLarge() {
    return statusCode == 413;
  }
}
