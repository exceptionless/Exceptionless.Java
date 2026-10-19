package com.exceptionless.submission;

import org.jspecify.annotations.Nullable;

/**
 * The result of a request to the server.
 *
 * @param statusCode the HTTP status code, or {@code -1} when the request failed without a response
 * @param message the response body or error message, if any
 * @param configVersion the {@code X-Exceptionless-ConfigVersion} response header
 */
public record SubmissionResponse(
    int statusCode, @Nullable String message, @Nullable Integer configVersion) {

  /** A request that failed without a response. */
  public static SubmissionResponse failed(Throwable error) {
    return new SubmissionResponse(
        -1, error.getClass().getSimpleName() + ": " + error.getMessage(), null);
  }

  /** A request that was not sent, for example because the client is disabled. */
  public static SubmissionResponse notSent(String message) {
    return new SubmissionResponse(-1, message, null);
  }

  public boolean isSuccess() {
    return statusCode >= 200 && statusCode <= 299;
  }

  public boolean isBadRequest() {
    return statusCode == 400;
  }

  public boolean isUnauthorized() {
    return statusCode == 401;
  }

  public boolean isPaymentRequired() {
    return statusCode == 402;
  }

  public boolean isForbidden() {
    return statusCode == 403;
  }

  public boolean isNotFound() {
    return statusCode == 404;
  }

  public boolean isRequestEntityTooLarge() {
    return statusCode == 413;
  }

  public boolean isTooManyRequests() {
    return statusCode == 429;
  }

  public boolean isServiceUnavailable() {
    return statusCode == 503;
  }
}
