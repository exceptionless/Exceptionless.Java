package com.exceptionless.exceptionlessclient.exceptions;

public class SubmissionClientException extends RuntimeException {
  public SubmissionClientException(Throwable cause) {
    super(cause);
  }

  public SubmissionClientException(String message) {
    super(message);
  }
}
